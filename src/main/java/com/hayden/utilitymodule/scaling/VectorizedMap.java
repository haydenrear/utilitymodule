package com.hayden.utilitymodule.scaling;


import com.hayden.utilitymodule.MapFunctions;
import jdk.incubator.vector.*;
import lombok.extern.slf4j.Slf4j;

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
import java.util.stream.IntStream;
import java.util.stream.Stream;

// current way of doing this is to keep the vectors in store at all times, but a different
// way to do it would be to keep the data in groups of the vector length, and add to the vector
// only when an operation is to be done, which lends itself to effectively no data structure at all.
/**
 * Map backed by a vector. This will need to be able to easily add and remove values. Contains a concurrent hash
 * map with the indices of the value in the vector.
 * @param <K>
 * @param <V>
 */
@Slf4j
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
        return VectorMapRemoveStateMachine.getThisVectorsState(this)
                .remove(this, (K) key);
    }

    public void removeAll(Collection<K> keysToRemove) {

        thisVectorsReadWriteLock.writeLock().lock();

        int startingAllIndex = allVectorsIndex.get();

        var squashed = getLeftOverAfterRemove(keysToRemove);
        var toSmoosh = squashed.leftOver;
        var indicesReplaced = squashed.indicesReplaced.stream()
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));

        var indicesRemoved = keysToRemove.stream()
                .map(v -> indices.get(v).indexOfVector)
                .distinct().toList();

        int length = vectorSpecies.length();
        int numVectorsCreated = toSmoosh.size() / length;

        List<K> keySet = new ArrayList<>(toSmoosh.keySet());

        var tempVectorsMap = putReplaceAllVectorsInternal(toSmoosh, indicesReplaced, numVectorsCreated, keySet);

        // Take the starting index, subtract
        int numIndicesDropped = indicesReplaced.size() - numVectorsCreated;
        int newAllVectorsIndex = startingAllIndex - numIndicesDropped;

        allVectorsIndex.set(newAllVectorsIndex);
        FloatVector thisVectors = thisVector.getAndSet(
                (FloatVector) vectorSpecies.fromArray(new float[vectorSpecies.length()], 0)
        );

        var toAddBack = keySet.subList(Math.min(length * numVectorsCreated, toSmoosh.size()), toSmoosh.size())
                .stream()
                .filter(k -> !keysToRemove.contains(k))
                .map(k -> {
                    VectorIndex vectorIndex = toSmoosh.get(k);
                    float[] vector;
                    if(vectorIndex.indexOfVector == startingAllIndex) {
                        vector = thisVectors.toArray();
                    } else {
                        vector = vectors.get(vectorIndex.indexOfVector).toArray();
                    }
                    return Map.entry(k, from(vector[vectorIndex.indexWithinVector]));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        indicesRemoved.forEach(vectors::remove);

        keysToRemove.forEach(indices::remove);

        tempVectorsMap.keySet()
                .forEach(vectors::remove);
        vectors.putAll(tempVectorsMap);

        assert toAddBack.size() < vectorSpecies.length();

        thisVectorsIndex.set(0);

        putAllInternal(toAddBack);

        thisVectorsReadWriteLock.writeLock().unlock();
    }

    private Map<Integer,FloatVector> putReplaceAllVectorsInternal(
            Map<K, VectorIndex> toSmoosh,
            ArrayList<Integer> indicesReplaced,
            int numIndicesRequired,
            List<K> keySet
    ) {
        int length = vectorSpecies.length();
        Map<Integer,FloatVector> tempVectorsMap = new HashMap<>();
        for (int i = 0; i < numIndicesRequired; ++i) {

            Integer newIndexOfVector = indicesReplaced.get(i);
            List<K> next = keySet.subList(i * length, i * length + length);
            float[] newArray = new float[length];

            float[] thisVectorArray = thisVector.get().toArray();

            for(int j=0; j < next.size(); ++j) {
                K k = next.get(j);
                VectorIndex prevVectorIndex = toSmoosh.get(k);
                float value;
                if (prevVectorIndex.indexOfVector == allVectorsIndex.get()) {
                    value = thisVectorArray[prevVectorIndex.indexWithinVector];
                } else {
                    value = vectors.get(prevVectorIndex.indexOfVector)
                            .toArray()[prevVectorIndex.indexWithinVector];
                }
                newArray[j] = value;
                indices.put(k, new VectorIndex(newIndexOfVector, j));
            }

            FloatVector floatVector = (FloatVector) vectorSpecies.fromArray(newArray, 0);
            tempVectorsMap.put(newIndexOfVector, floatVector);
        }

        return tempVectorsMap;
    }

    private List<IndexVector<K>> createFloatVectors(
            Map<K, V> toSmoosh,
            ArrayList<Integer> indicesReplaced,
            List<K> keySet
    ) {
        int length = vectorSpecies.length();
        assert keySet.size() % length == 0;
        int max = indicesReplaced.stream().max(Integer::compareTo)
                .orElse(0);
        assert keySet.size() == (max + 1) * length;

        return indicesReplaced.stream()
                .map(i -> createVector(
                        keySet.subList(i * length, i * length + length),
                        toSmoosh,
                        i
                ))
                .toList();

    }

    record IndexVector<K>(Map<K,VectorIndex> vectorIndex, FloatVector vector) {}

    public IndexVector<K> createVector(List<K> keyset, Map<K,V> values, int vectorIndex) {
        assert keyset.size() == vectorSpecies.length();
        float[] vector = new float[vectorSpecies.length()];
        Map<K,VectorIndex> indices = new HashMap<>();

        for(int i=0; i<vectorSpecies.length(); ++i) {
            K key = keyset.get(i);
            vector[i] = values.get(key).floatValue();
            indices.put(key, new VectorIndex(vectorIndex, i));
        }

        return new IndexVector<>(indices, (FloatVector) vectorSpecies.fromArray(vector, 0));
    }

    public record Squashed<K>(
            ConcurrentSkipListMap<K, VectorizedMap.VectorIndex> leftOver,
            ArrayList<Integer> indicesReplaced
    ) {}

    Squashed<K> getLeftOverAfterRemove(Collection<K> keysToRemove) {
        Stream<VectorIndex> build = getIndicesWithKeysInBlockToBeRemoved(keysToRemove);
        var toSmoosh = MapFunctions.CollectMap(
                build
                        .flatMap(v -> {
                            //TODO: probably better to keep around reverseIndices...
                            return indices
                                    .entrySet()
                                    .stream()
                                    .filter(e -> Objects.equals(e.getValue().indexOfVector, v.indexOfVector)
                                            && !keysToRemove.contains(e.getKey())
                                    );
                        })
                        .map(v -> Map.entry(getByVectorIndex(v.getValue()), v.getValue())), ConcurrentSkipListMap::new
        );

        var indicesReplaced = toSmoosh.values().stream()
                .map(vectorIndex -> vectorIndex.indexOfVector)
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));

        return new Squashed<>(toSmoosh, indicesReplaced);
    }

    private Stream<VectorIndex> getIndicesWithKeysInBlockToBeRemoved(Collection<K> keysToRemove) {
        Stream.Builder<VectorIndex> indicesToRemoveStreamBuilder = Stream.builder();
        keysToRemove.stream()
                .map(indices::get)
                .filter(Objects::nonNull)
                .forEach(indicesToRemoveStreamBuilder::add);
        IntStream.range(0, thisVectorsIndex.get())
                .boxed()
                .map(i -> new VectorIndex(allVectorsIndex.get(), i))
                .forEach(indicesToRemoveStreamBuilder::add);

        Stream<VectorIndex> build = indicesToRemoveStreamBuilder.build();
        return build;
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

        int length = vectorSpecies.length();
        int amountLeftForThisVector = length - thisVectorsIndex.get();
        int amountLeft = map.size() % length;
        int numVectors = map.size() / length;

        ArrayList<K> keys = new ArrayList<>(map.keySet());

        if(numVectors != 0) {

            ArrayList<Integer> allVectorsIndicesFound = IntStream.range(this.allVectorsIndex.get(), numVectors)
                    .boxed()
                    .collect(Collectors.toCollection(ArrayList::new));

            List<K> keySet = keys.subList(0, map.size() - amountLeft);
            List<IndexVector<K>> floatVectorsToAdd = createFloatVectors((Map<K, V>) map, allVectorsIndicesFound,
                                                                        keySet);

            floatVectorsToAdd.stream()
                    .flatMap(floatVectorToAdd -> {
                        floatVectorToAdd.vectorIndex.entrySet()
                                .stream().findAny()
                                .ifPresentOrElse(
                                        any -> vectors.put(any.getValue().indexOfVector, floatVectorToAdd.vector),
                                        () -> log.error("There was not any vector present!"));
                        return floatVectorToAdd.vectorIndex.entrySet().stream();
                    })
                    .forEach(v -> indices.put(v.getKey(), v.getValue()));

            allVectorsIndex.set(numVectors);
        }

        if(amountLeft != 0) {
            addToThisVector(map, amountLeftForThisVector, keys.subList(map.size() - amountLeft, map.size()));
        }

    }

    void addToThisVector(Map<? extends K, ? extends V> map, int amountLeftForThisVector, List<K> ks) {
        if (ks.size() == amountLeftForThisVector) {
            replaceThisVector(map, ks);
        } else if (ks.size() < amountLeftForThisVector){
            float[] floats = this.thisVector.get().toArray();
            for (var e : ks) {
                int andIncrement = thisVectorsIndex.getAndIncrement();
                floats[andIncrement] = map.get(e).floatValue();
                indices.put(e, new VectorIndex(allVectorsIndex.get(), andIncrement));
            }
            this.thisVector.set((FloatVector) vectorSpecies.fromArray(floats, 0));
        } else {
            replaceThisVector(map, ks.subList(0, amountLeftForThisVector));
            var floats = thisVector.get().toArray();
            int vectorIndex = allVectorsIndex.get();
            for (int i=amountLeftForThisVector; i<ks.size(); ++i) {
                int andIncremenet = thisVectorsIndex.getAndIncrement();
                K key = ks.get(i);
                floats[andIncremenet] = map.get(key).floatValue();
                indices.put(key, new VectorIndex(vectorIndex, andIncremenet));
            }
            FloatVector newValue = (FloatVector) vectorSpecies.fromArray(floats, 0);
            this.thisVector.set(newValue);
        }
    }

    private void replaceThisVector(
            Map<? extends K, ? extends V> map,
            List<K> keys)
    {
        assert vectorSpecies.length() - thisVectorsIndex.get() == keys.size();
        FloatVector thisVectorFound = this.thisVector.getAndSet(
                (FloatVector) vectorSpecies.fromArray(new float[vectorSpecies.length()], 0)
        );
        float[] floats = thisVectorFound.toArray();
        int i = thisVectorsIndex.get();
        for (var e : keys) {
            floats[i] = map.get(e).floatValue();
            indices.put(e, new VectorIndex(allVectorsIndex.get(), i));
            ++i;
        }
        vectors.put(allVectorsIndex.getAndIncrement(), (FloatVector) vectorSpecies.fromArray(floats, 0));
        thisVectorsIndex.set(0);
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
