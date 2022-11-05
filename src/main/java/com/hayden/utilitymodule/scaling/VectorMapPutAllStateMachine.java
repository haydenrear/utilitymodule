//package com.hayden.utilitymodule.scaling;
//
//import jdk.incubator.vector.FloatVector;
//import jdk.incubator.vector.VectorSpecies;
//
//import java.util.Collection;
//import java.util.Map;
//
///**
// * This algorithm consists of
// * 1. squashing -- taking all of the vectors that have a key contained in the remove, and reorganizing them so that the vectors
// * are full.
// * 2. reorganizing the indices so that the vectors are contiguous.
// * 3. removing the references to the vectors that were there.
// *
// * When determining how many continguous vectors there are going to be,
// * 1. Take the squashed number and add the numbers in this vector
// * 2. Use this number
// */
//
//
//enum VectorMapPutAllStateMachine {
//    THIS_VECTOR_HAS_VALUE_HAS_ROOM {
//        @Override
//        public <K, V extends Number> V putAll(VectorizedMap<K, V> removeFrom, Map<K,V> map) {
//            if(map.size() == 0) {
//                return null;
//            }
//
//            int toAdd = map.size();
//            int amountLeftForThisVector = vectorSpecies.length() - thisVectorsIndex.get();
//            int numFloatsForNewVectors = toAdd - amountLeftForThisVector;
//
//            int numVectorsToBeCreated = 0;
//            int amountLeft = 0;
//
//            if (numFloatsForNewVectors > 0) {
//                numVectorsToBeCreated = Math.max(numFloatsForNewVectors / vectorSpecies.length(), 1);
//                amountLeft = numFloatsForNewVectors % vectorSpecies.length();
//            }
//
//            List<K> keys = new ArrayList<>(map.keySet());
//
//            addAdditionalVectors(map, amountLeftForThisVector, numVectorsToBeCreated, keys);
//            addToThisVector(map, keys.subList(0, Math.min(amountLeftForThisVector, keys.size())));
//
//            if (amountLeft >= amountLeftForThisVector) {
//                FloatVector replaceWith = getVectorToReplace(map, amountLeft, keys, thisVectorsIndex.get());
//                FloatVector value = thisVector.getAndSet(replaceWith);
//                vectors.put(allVectorsIndex.getAndIncrement(), value);
//            }
//        }
//
//
//    },
//    THIS_VECTOR_NO_VALUE_NO_VECTORS_IN_MAP {
//        @Override
//        public <K, V extends Number> V putAll(VectorizedMap<K, V> removeFrom, Map<K,V> key) {
//
//        }
//
//    },
//    THIS_VECTOR_NO_VALUE_VECTORS_IN_MAP {
//        @Override
//        public <K, V extends Number> V putAll(VectorizedMap<K, V> removeFrom, Map<K,V> key) {
//
//        }
//    };
//
//    public <K, V extends Number> V putAll(VectorizedMap<K, V> removeFrom, Map<K,V> key) {
//        throw new UnsupportedOperationException("Impossible!");
//    }
//
//    public static <K, V extends Number> VectorMapPutAllStateMachine getThisVectorsState(VectorizedMap<K, V> vector) {
//        if (vector.thisVectorsIndex.get() == 0) {
//            if (vector.allVectorsIndex.get() == 0) {
//                return THIS_VECTOR_NO_VALUE_NO_VECTORS_IN_MAP;
//            } else {
//                return THIS_VECTOR_NO_VALUE_VECTORS_IN_MAP;
//            }
//        } else {
//            return THIS_VECTOR_HAS_VALUE_HAS_ROOM;
//        }
//    }
//
//
//
//}
//
