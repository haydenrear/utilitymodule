package com.hayden.utilitymodule.scaling;


import com.hayden.utilitymodule.MapFunctions;
import jdk.incubator.vector.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

// current way of doing this is to keep the vectors in store at all times, but a different
// way to do it would be to keep the data in groups of the vector length, and add to the vector
// only when an operation is to be done, which lends itself to effectively no data structure at all.
/**
 * Map backed by a vector. This will need to be able to easily add and remove values. Contains a concurrent hash
 * map with the indices of the value in the vector.
 * @param <K>
 * @param <V>
 */
public class VectorizedMap<K,V extends Number>
//        extends ConcurrentSkipListMap<K, V>
{

    final VectorSpecies<Float> vectorSpecies;

    Comparable<? super K> compareTo;

    static final ReadWriteLock minReadWriteLock = new ReentrantReadWriteLock();
    final Class<V> clzz;


    V minimum;
    V maximum;

    static final ReadWriteLock maxReadWriteLock = new ReentrantReadWriteLock();

    record VectorIndex(Integer indexOfVector, Integer indexWithinVector) {}

    final ConcurrentSkipListMap<K, VectorIndex> indices = new ConcurrentSkipListMap<>();
    //TODO: could probably implement lock striping for each float vector.
    final ConcurrentHashMap<Integer, FloatVector> vectors = new ConcurrentHashMap<>();
    final AtomicInteger allVectorsIndex = new AtomicInteger(0);
    final AtomicReference<FloatVector> thisVector = new AtomicReference<>();
    final AtomicInteger thisVectorsIndex = new AtomicInteger(0);

    static final ReadWriteLock thisVectorsReadWriteLock = new ReentrantReadWriteLock();

    public VectorizedMap<K,V> getScaledValueMap() {
        return new VectorizedMap<>(clzz);
    }

    public VectorizedMap<V,V> getScaledKeyMap(Function<K,V> converter) {
        return new VectorizedMap<>(clzz);
    }

    public VectorizedMap<V,V> getScaledKeyValueMap(Function<K,V> converter) {
        return new VectorizedMap<>(clzz);
    }

    //    @Override
    public int size() {
        return indices.size();
    }

//    @Override
    public boolean containsValue(Object value) {
        V toCheck = (V) value;
        thisVectorsReadWriteLock.readLock().lock();
        for (float v : thisVector.get().toArray()) {
            if (Objects.equals(toCheck, from(v))) {
                thisVectorsReadWriteLock.readLock().unlock();
                return true;
            }
        }
        boolean doAnyMatch = vectors.values().stream()
                .anyMatch(f -> {
                    for(float v : f.toArray()) {
                        if (Objects.equals(toCheck, from(v))) {
                            return true;
                        }
                    }
                    return false;
                });
        thisVectorsReadWriteLock.readLock().unlock();
        return doAnyMatch;
    }

    /**
     * Should be avoided and putAll should be used instead, because it copies over the vector.
     * @param key
     * @param value
     */
//    @Override
    public V put(K key, V value) {
        setMinMax(value);

        Lock writeLock = thisVectorsReadWriteLock.writeLock();
        writeLock.lock();

        int thisVectorsIndex = this.thisVectorsIndex.getAndIncrement();
        float[] workingArray = getWorkingArray();
        workingArray[thisVectorsIndex] = value.floatValue();
        thisVector.set((FloatVector) vectorSpecies.fromArray(workingArray, 0));

        if (thisVectorsIndex >= vectorSpecies.length() - 1) {
            this.thisVectorsIndex.set(0);
            FloatVector zero = (FloatVector) vectorSpecies.zero();
            FloatVector prev = thisVector.getAndSet(zero);
            int vectorsIndex = allVectorsIndex.getAndIncrement();
            vectors.put(vectorsIndex, prev);
            indices.put(key, new VectorIndex(vectorsIndex, 0));
        } else {
            int indexOfVector = allVectorsIndex.get();
            indices.put(key, new VectorIndex(indexOfVector, thisVectorsIndex));
        }

        writeLock.unlock();
        return value;
    }

//    @Override
    public V remove(Object key) {
        return ThisVectorsRemoveStateMachine.getThisVectorsState(this)
                .remove(this, (K) key);
    }

    public void removeAll(Collection<K> keysToRemove) {

        thisVectorsReadWriteLock.writeLock().lock();

        ConcurrentSkipListMap<K, VectorIndex> toSmoosh = getLeftOverAfterRemove(keysToRemove);

        var indicesReplaced = toSmoosh.values().stream()
                .map(vectorIndex -> vectorIndex.indexOfVector)
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));

        if(indicesReplaced.contains(allVectorsIndex.get())) {
            FloatVector currentVector = thisVector.getAndSet(null);
            vectors.put(allVectorsIndex.get(), currentVector);
        }

        int length = vectorSpecies.length();
        int numIndicesRequired = toSmoosh.size() % length;

        Map<Integer,FloatVector> vectorsMap = new HashMap<>();
        List<K> keySet = new ArrayList<>(toSmoosh.keySet());
        for (int i=0; i<numIndicesRequired - 1; ++i) {

            Integer newIndexOfVector = indicesReplaced.get(i);
            List<K> next = keySet.subList(i * length, i * length + length);
            float[] newArray = new float[length];

            for(int j=0; j < next.size(); ++j) {
                K k = next.get(j);
                VectorIndex prevVectorIndex = toSmoosh.get(k);
                float value = vectors.get(prevVectorIndex.indexOfVector)
                        .toArray()[prevVectorIndex.indexWithinVector];
                newArray[j] = value;
                indices.put(k, new VectorIndex(newIndexOfVector, j));
            }

            FloatVector floatVector = (FloatVector) vectorSpecies.fromArray(newArray, 0);
            vectorsMap.put(newIndexOfVector, floatVector);
        }

        var toAddBack = keySet.subList(numIndicesRequired, toSmoosh.size())
                .stream()
                .filter(k -> !keysToRemove.contains(k))
                .map(k -> Map.entry(k, from(vectors.get(toSmoosh.get(k).indexOfVector).toArray()[toSmoosh.get(k).indexWithinVector])))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        putAllInternal(toAddBack);

        // now deal with the one's left over...
        List<K> leftOver = keySet.subList(
                numIndicesRequired * length,
                (numIndicesRequired * length) + (keySet.size() % length)
        );

        if(thisVector.get() == null) {
            float[] leftOverValues = new float[length];
            for(int i=0; i<leftOver.size(); ++i) {
                K key = leftOver.get(i);
                VectorIndex value = toSmoosh.get(key);
                indices.put(key, value);
                leftOverValues[i] = vectorsMap.get(value.indexOfVector)
                        .toArray()[value.indexWithinVector];
            }
            thisVector.set((FloatVector) vectorSpecies.fromArray(leftOverValues, 0));
        } else {
            var addRegularly = leftOver.stream()
                    .map(k -> {
                        VectorIndex vectorIndex = toSmoosh.get(k);
                        return Map.entry(k, from(vectors.get(vectorIndex.indexOfVector)
                                .toArray()[vectorIndex.indexWithinVector]));
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            putAllInternal(addRegularly);
        }


        indicesReplaced.forEach(vectors::remove);
        keysToRemove.forEach(indices::remove);

        vectors.putAll(vectorsMap);

        thisVectorsReadWriteLock.writeLock().unlock();
    }

    ConcurrentSkipListMap<K, VectorIndex> getLeftOverAfterRemove(Collection<K> keysToRemove) {
        var toSmoosh = MapFunctions.CollectMap(
                keysToRemove.stream()
                        .map(indices::get)
                        .filter(Objects::nonNull)
                        .flatMap(v -> {
                            //TODO: probably better to keep around reverseIndices...
                            return indices
                                    .entrySet()
                                    .stream()
                                    .filter(e -> Objects.equals(e.getValue().indexOfVector, v.indexOfVector));
                        })
                        .map(v -> Map.entry(getByVectorIndex(v.getValue()), v.getValue())), ConcurrentSkipListMap::new
        );
        return toSmoosh;
    }

    K getByVectorIndex(VectorIndex vectorIndex) {
        //TODO: point of optimization
        return indices.entrySet()
                .stream()
                .filter(k -> k.getValue().equals(vectorIndex))
                .map(Map.Entry::getKey)
                .findAny()
                .orElse(null);
    }

//    @Override
    public boolean containsKey(Object key) {
        return indices.containsKey(key);
    }

//    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        thisVectorsReadWriteLock.writeLock().lock();
        putAllInternal(map);
        thisVectorsReadWriteLock.writeLock().unlock();
    }

    /**
     * Assumes that the lock is already held.
     * @param map
     */
    void putAllInternal(Map<? extends K, ? extends V> map) {

        if(map.size() == 0) {
            return;
        }

        int toAdd = map.size();
        int amountLeftForThisVector = vectorSpecies.length() - thisVectorsIndex.get();
        int numFloatsForNewVectors = toAdd - amountLeftForThisVector;

        int numVectorsToBeCreated = 0;
        int amountLeft = 0;

        if (numFloatsForNewVectors > 0) {
            numVectorsToBeCreated = Math.max(numFloatsForNewVectors / vectorSpecies.length(), 1);
            amountLeft = numFloatsForNewVectors % vectorSpecies.length();
        }

        List<K> keys = new ArrayList<>(map.keySet());

        addAdditionalVectors(map, amountLeftForThisVector, numVectorsToBeCreated, keys);
        addToThisVector(map, keys.subList(0, Math.min(amountLeftForThisVector, keys.size())));

        if (amountLeft >= amountLeftForThisVector) {
            FloatVector replaceWith = getVectorToReplace(map, amountLeft, keys, thisVectorsIndex.get());
            FloatVector value = thisVector.getAndSet(replaceWith);
            vectors.put(allVectorsIndex.getAndIncrement(), value);
        }
    }

    void addToThisVector(Map<? extends K, ? extends V> map, List<K> forThisVector) {
        int counter;
        float[] thisVectorToReplace = thisVector.get().toArray();

        assert(thisVectorsIndex.get() + forThisVector.size() <= vectorSpecies.length());
        counter = 0;
        for(int i = thisVectorsIndex.get();
            i < forThisVector.size() + thisVectorsIndex.get();
            ++i
        ) {
            K thisKey = forThisVector.get(counter);
            V value = map.get(thisKey);
            setMinMax(value);
            thisVectorToReplace[i] = value.floatValue();
            VectorIndex vectorIndex = new VectorIndex(thisVectorsIndex.get(), i);
            indices.put(thisKey, vectorIndex);
            ++counter;
        }
        thisVector.set((FloatVector) vectorSpecies.fromArray(thisVectorToReplace, 0));
        thisVectorsIndex.updateAndGet(v -> forThisVector.size() + v);
        System.out.println();
    }

    FloatVector getVectorToReplace(
            Map<? extends K, ? extends V> map,
            int amountLeft,
            List<K> keys,
            int thisVectorsIndex
    ) {
        int fromIndex = keys.size() - amountLeft;
        List<K> last = keys.subList(fromIndex, keys.size());
        float[] toSetArray = new float[vectorSpecies.length()];

        int counter = 0;
        for (var k: last) {
            V v = map.get(k);
            setMinMax(v);
            toSetArray[counter] = v.floatValue();
            VectorIndex vectorIndex = new VectorIndex(thisVectorsIndex + 1, counter);
            indices.put(k, vectorIndex);
            ++counter;
        }

        FloatVector replaceWith = (FloatVector) vectorSpecies.fromArray(toSetArray, 0);
        return replaceWith;
    }

    void addAdditionalVectors(Map<? extends K, ? extends V> map, int amountLeftForThisVector, int numVectorsToBeCreated,
                                      List<K> keys) {
        for (int i = amountLeftForThisVector;
             i <= Math.min(map.size(), numVectorsToBeCreated * vectorSpecies.length() + vectorSpecies.length());
             i = i + vectorSpecies.length()
        ) {
            List<K> next = keys.subList(i, Math.min(i + vectorSpecies.length(), keys.size()));
            int nextVectorToInc = allVectorsIndex.getAndIncrement();
            float[] nextArr = new float[vectorSpecies.length()];
            for (int j = 0; j < next.size(); ++j) {
                K key = next.get(j);
                V value = map.get(key);
                setMinMax(value);
                float v = value.floatValue();
                nextArr[j] = v;
                VectorIndex thisVectorIndex = new VectorIndex(nextVectorToInc, j);
                indices.put(key, thisVectorIndex);
            }
            vectors.put(nextVectorToInc, (FloatVector) vectorSpecies.fromArray(nextArr, 0));
        }
    }


    //    @Override
    public V get(Object key) {
        VectorIndex vectorIndex = indices.get(key);
        if(!vectors.containsKey(vectorIndex.indexOfVector)) {
            if(vectorIndex.indexOfVector == vectors.size()) {
                return from(thisVector.get().toArray()[vectorIndex.indexWithinVector]);
            }
        }
        var value = vectors.get(vectorIndex.indexOfVector).toArray()[vectorIndex.indexWithinVector];
        return from(value);
    }

    public V from(float from) {
        if(Float.class.isAssignableFrom(clzz)) {
            return (V) ((Float)from);
        } else if (Double.class.isAssignableFrom(clzz)) {
            return (V) ((Double) ((Float)from).doubleValue());
        } else if (Long.class.isAssignableFrom(clzz)) {
            return (V) ((Long) ((Float)from).longValue());
        } else if (Integer.class.isAssignableFrom(clzz)) {
            return (V) ((Integer) ((Float)from).intValue());
        }
        return (V) ((Double) ((Float)from).doubleValue());
    }

    void setMax(V value) {
        Lock readLock = maxReadWriteLock.readLock();
        readLock.lock();
        if(maximum == null || value.doubleValue() > maximum.doubleValue()) {
            readLock.unlock();
            Lock writeLock = maxReadWriteLock.writeLock();
            writeLock.lock();
            maximum = value;
            writeLock.unlock();
        } else {
            readLock.unlock();
        }
    }

    void setMin(V value) {
        Lock readLock = minReadWriteLock.readLock();
        readLock.lock();
        if(minimum == null || value.doubleValue() < minimum.doubleValue()) {
            readLock.unlock();
            Lock writeLock = minReadWriteLock.writeLock();
            writeLock.lock();
            minimum = value;
            writeLock.unlock();
        } else {
            readLock.unlock();
        }
    }


    public float[] getWorkingArray() {
        return thisVector.get().toArray();
    }

    public VectorizedMap(Class<V> integerClass) {
        this.clzz = integerClass;
        vectorSpecies = VectorSpecies.of(float.class, VectorShape.S_Max_BIT);
        thisVector.set((FloatVector) vectorSpecies.zero());
    }

    void setMinMax(V value) {
        setMin(value);
        setMax(value);
    }

}
