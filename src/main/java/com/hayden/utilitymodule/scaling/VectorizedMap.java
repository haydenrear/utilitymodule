package com.hayden.utilitymodule.scaling;


import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * Map backed by a vector. This will need to be able to easily add and remove values. Contains a concurrent hash
 * map with the indices of the value in the vector.
 * @param <K>
 * @param <V>
 */
public class VectorizedMap<K,V extends Number> extends ConcurrentSkipListMap<K, V> {

    private final VectorSpecies<Float> vectorSpecies;

    private static final ReadWriteLock minReadWriteLock = new ReentrantReadWriteLock();
    private final Class<V> clzz;
    @Getter(AccessLevel.MODULE)
    private V minimum;
    @Getter(AccessLevel.MODULE)
    private V maximum;

    private static final ReadWriteLock maxReadWriteLock = new ReentrantReadWriteLock();

    record VectorIndex(Integer indexOfVector, Integer indexWithinVector) {}

    private final ConcurrentSkipListMap<K, VectorIndex> indices = new ConcurrentSkipListMap<>();
    private final ConcurrentHashMap<Integer, FloatVector> vectors = new ConcurrentHashMap<>();

    private final AtomicInteger allVectorsIndex = new AtomicInteger(0);
    private final AtomicReference<FloatVector> thisVector = new AtomicReference<>();
    private final AtomicInteger thisVectorsIndex = new AtomicInteger(0);

    private static final ReadWriteLock thisVectorsReadWriteLock = new ReentrantReadWriteLock();

    public VectorizedMap<K,V> getScaledValueMap() {
        return new VectorizedMap<>(clzz);
    }

    public VectorizedMap<V,V> getScaledKeyMap(Function<K,V> converter) {
        return new VectorizedMap<>(clzz);
    }

    public VectorizedMap<V,V> getScaledKeyValueMap(Function<K,V> converter) {
        return new VectorizedMap<>(clzz);
    }

    /**
     * Should be avoided and putAll should be used instead, because it copies over the vector.
     * @param key
     * @param value
     */
    @Override
    public V put(K key, V value) {
        setMinMax(value);

        Lock writeLock = thisVectorsReadWriteLock.writeLock();
        writeLock.lock();

        int thisVectorsIndex = this.thisVectorsIndex.getAndIncrement();
        float[] workingArray = getWorkingArray();
        workingArray[thisVectorsIndex] = value.floatValue();
        thisVector.set((FloatVector) vectorSpecies.fromArray(workingArray, 0));

        if (thisVectorsIndex > vectorSpecies.length()) {
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

    @Override
    public boolean containsKey(Object key) {
        return indices.containsKey(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        Lock lock = thisVectorsReadWriteLock.writeLock();

        lock.lock();

        int toAdd = map.size();
        int amountLeftForThisVector = vectorSpecies.length() % (vectorSpecies.length() - thisVectorsIndex.get());
        int numNewVectors = toAdd - amountLeftForThisVector;

        int numVectorsToBeCreated = numNewVectors / vectorSpecies.length();
        int amountLeft = numNewVectors % vectorSpecies.length();

        List<K> keys = new ArrayList<>(map.keySet());

        for (int i=amountLeftForThisVector;
             i < numVectorsToBeCreated * vectorSpecies.length();
             i = i + vectorSpecies.length()
        ) {
            List<K> next = keys.subList(i, i + vectorSpecies.length());
            int nextVectorToInc = allVectorsIndex.getAndIncrement();
            float[] nextArr = new float[vectorSpecies.length()];
            for(int j=0; j<next.size(); ++j) {
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

        List<K> last = keys.subList(keys.size() - amountLeft, keys.size());
        float[] toSetArray = new float[vectorSpecies.length()];

        int counter = 0;
        for (var k: last) {
            V v = map.get(k);
            setMinMax(v);
            toSetArray[counter] = v.floatValue();
            VectorIndex vectorIndex = new VectorIndex(thisVectorsIndex.get() + 1, counter);
            indices.put(k, vectorIndex);
            ++counter;
        }

        FloatVector replaceWith = (FloatVector) vectorSpecies.fromArray(toSetArray, 0);

        List<K> forThisVector = keys.subList(0, amountLeftForThisVector);
        float[] thisVectorToReplace = thisVector.get().toArray();

        counter = 0;
        for(int i = vectorSpecies.length() - forThisVector.size();
            i < vectorSpecies.length();
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

        FloatVector value = thisVector.getAndSet(replaceWith);
        vectors.put(thisVectorsIndex.getAndIncrement(), value);
        lock.unlock();

    }


    @Override
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

    private void setMax(V value) {
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

    private void setMin(V value) {
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

    private void setMinMax(V value) {
        setMin(value);
        setMax(value);
    }

}
