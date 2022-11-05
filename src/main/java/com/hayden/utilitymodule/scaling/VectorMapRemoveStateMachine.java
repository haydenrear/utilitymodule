package com.hayden.utilitymodule.scaling;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

enum VectorMapRemoveStateMachine {

    THIS_VECTOR_HAS_VALUE_HAS_ROOM {
        @Override
        public <K, V extends Number> V remove(VectorizedMap<K, V> removeFrom, K key) {

            VectorizedMap.thisVectorsReadWriteLock.writeLock().lock();
            VectorizedMap.VectorIndex index = removeFrom.indices.remove(key);

            if (index == null)
                return null;

            FloatVector thisVector = removeFrom.thisVector.get();
            int i = removeFrom.thisVectorsIndex.decrementAndGet();
            float toReplaceWith = thisVector.toArray()[i];
            thisVector.toArray()[i] = 0;
            float[] vectorToRemoveFrom = removeFrom.vectors.get(index.indexOfVector()).toArray();
            float valueToRemove = vectorToRemoveFrom[index.indexWithinVector()];
            vectorToRemoveFrom[index.indexWithinVector()] = toReplaceWith;
            removeFrom.vectors.put(index.indexOfVector(),
                                   (FloatVector) removeFrom.vectorSpecies.fromArray(vectorToRemoveFrom, 0));

            VectorizedMap.thisVectorsReadWriteLock.writeLock().unlock();

            return removeFrom.from(valueToRemove);
        }


    },
    THIS_VECTOR_NO_VALUE_NO_VECTORS_IN_MAP {
        @Override
        public <K, V extends Number> V remove(VectorizedMap<K, V> removeFrom, K key) {

            VectorizedMap.thisVectorsReadWriteLock.writeLock().lock();
            VectorizedMap.VectorIndex index = removeFrom.indices.remove(key);
            float valueToRemove;

            if (index == null)
                return null;

            FloatVector floatVector = removeFrom.thisVector.get();
            valueToRemove = floatVector.toArray()[index.indexWithinVector()];

            for (int i = removeFrom.vectorSpecies.length() - 1; i > index.indexWithinVector(); --i) {
                K thisValue = removeFrom.getByVectorIndex(new VectorizedMap.VectorIndex(0, i));
                assert thisValue != null;
                removeFrom.indices.put(thisValue, new VectorizedMap.VectorIndex(0, i - 1));
            }

            VectorizedMap.thisVectorsReadWriteLock.writeLock().unlock();

            return removeFrom.from(valueToRemove);
        }

    },
    THIS_VECTOR_NO_VALUE_VECTORS_IN_MAP {
        @Override
        public <K, V extends Number> V remove(VectorizedMap<K, V> removeFrom, K key) {

            VectorSpecies<Float> thisVectorsSpecies = removeFrom.vectorSpecies;

            VectorizedMap.thisVectorsReadWriteLock.writeLock().lock();
            VectorizedMap.VectorIndex index = removeFrom.indices.remove(key);

            if (index == null)
                return null;

            FloatVector toReplaceWith = removeFrom.vectors.get(removeFrom.allVectorsIndex.decrementAndGet());
            float value = toReplaceWith.toArray()[thisVectorsSpecies.length() - 1];
            toReplaceWith.toArray()[thisVectorsSpecies.length() - 1] = 0;
            FloatVector floatVectorWithReplacedValue = (FloatVector) thisVectorsSpecies.fromArray(
                    toReplaceWith.toArray(), 0
            );
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

    public <K, V extends Number> V remove(VectorizedMap<K, V> removeFrom, K key) {
        throw new UnsupportedOperationException("Impossible!");
    }

    public static <K, V extends Number> VectorMapRemoveStateMachine getThisVectorsState(VectorizedMap<K, V> vector) {
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
