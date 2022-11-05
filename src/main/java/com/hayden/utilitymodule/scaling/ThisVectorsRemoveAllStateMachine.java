package com.hayden.utilitymodule.scaling;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * This algorithm consists of
 * 1. squashing -- taking all of the vectors that have a key contained in the remove, and reorganizing them so that the vectors
 * are full.
 * 2. reorganizing the indices so that the vectors are contiguous.
 * 3. removing the references to the vectors that were there.
 *
 * When determining how many continguous vectors there are going to be,
 * 1. Take the squashed number and add the numbers in this vector
 * 2. Use this number
 */


enum ThisVectorsRemoveAllStateMachine {


    /**
     * This means that the vectors in this vector must be "added back"
     */
    THIS_VECTOR_HAS_VALUE_HAS_ROOM {
        @Override
        public <K, V extends Number> V removeAll(VectorizedMap<K, V> removeFrom, Collection<K> key) {

        }


    },
    THIS_VECTOR_NO_VALUE_NO_VECTORS_IN_MAP {
        @Override
        public <K, V extends Number> V removeAll(VectorizedMap<K, V> removeFrom, Collection<K> key) {

            VectorizedMap.thisVectorsReadWriteLock.writeLock().lock();
            VectorizedMap.VectorIndex index = removeFrom.indices.remove(key);
            float valueToRemove;

            if (index == null)
                return null;

            FloatVector floatVector = removeFrom.thisVector.get();
            valueToRemove = floatVector.toArray()[index.indexWithinVector()];

            for (int i = removeFrom.vectorSpecies.length() - 1; i > index.indexWithinVector(); --i) {
                K thisValue = removeFrom.getByVectorIndex(new VectorizedMap.VectorIndex(0, i));
                removeFrom.indices.put(thisValue, new VectorizedMap.VectorIndex(0, i - 1));
            }

            VectorizedMap.thisVectorsReadWriteLock.writeLock().unlock();

            return removeFrom.from(valueToRemove);
        }

    },
    THIS_VECTOR_NO_VALUE_VECTORS_IN_MAP {
        @Override
        public <K, V extends Number> V removeAll(VectorizedMap<K, V> removeFrom, Collection<K> key) {

            VectorSpecies<Float> thisVectorsSpecies = removeFrom.vectorSpecies;

            VectorizedMap.thisVectorsReadWriteLock.writeLock().lock();
            VectorizedMap.VectorIndex index = removeFrom.indices.remove(key);

            if (index == null)
                return null;

            FloatVector toReplaceWith = removeFrom.vectors.get(removeFrom.allVectorsIndex.decrementAndGet());
            float value = toReplaceWith.toArray()[thisVectorsSpecies.length() - 1];
            toReplaceWith.toArray()[thisVectorsSpecies.length() - 1] = 0;
            FloatVector floatVectorWithReplacedValue = (FloatVector) thisVectorsSpecies.fromArray(
                    toReplaceWith.toArray(), 0);
            removeFrom.thisVector.set(floatVectorWithReplacedValue);
            float[] floatVectorToRemoveFrom = removeFrom.vectors.get(index.indexOfVector()).toArray();
            float valueToRemove = floatVectorToRemoveFrom[index.indexWithinVector()];
            floatVectorToRemoveFrom[index.indexWithinVector()] = value;
            removeFrom.vectors.put(index.indexOfVector(),
                                   (FloatVector) thisVectorsSpecies.fromArray(floatVectorToRemoveFrom, 0));

            VectorizedMap.thisVectorsReadWriteLock.writeLock().unlock();
            return removeFrom.from(valueToRemove);
        }

    };

    public record Squashed<K,V extends Number>(
            ConcurrentSkipListMap<K, VectorizedMap.VectorIndex> leftOver,
            ArrayList<Integer> indicesReplaced
    ) {}

    public <K, V extends Number> V removeAll(VectorizedMap<K, V> removeFrom, Collection<K> key) {
        throw new UnsupportedOperationException("Impossible!");
    }

    public static <K, V extends Number> ThisVectorsRemoveAllStateMachine getThisVectorsState(VectorizedMap<K, V> vector) {
        if (vector.thisVectorsIndex.get() == 0) {
            if (vector.allVectorsIndex.get() == 0) {
                return THIS_VECTOR_NO_VALUE_NO_VECTORS_IN_MAP;
            } else {
                return THIS_VECTOR_NO_VALUE_VECTORS_IN_MAP;
            }
        } else {
            return THIS_VECTOR_HAS_VALUE_HAS_ROOM;
        }
    }


}

