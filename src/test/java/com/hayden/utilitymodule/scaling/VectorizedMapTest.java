package com.hayden.utilitymodule.scaling;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class VectorizedMapTest {

    @Test
    public void testVectorizedMapSimplePut() {
        VectorizedMap<String,Integer> vectorizedMap = new VectorizedMap<String, Integer>(Integer.class);
        vectorizedMap.put("hello", 1);
        vectorizedMap.put("hello1", 2);
        vectorizedMap.put("hello2", 3);
        assertThat(vectorizedMap.get("hello")).isEqualTo(1);
    }

    @Test
    public void testVectorizedMapSimplePutSameValueReplaces() {
        VectorizedMap<String,Integer> vectorizedMap = new VectorizedMap<String, Integer>(Integer.class);
        vectorizedMap.put("hello", 1);
        assertThat(vectorizedMap.get("hello")).isEqualTo(1);
        vectorizedMap.put("hello", 2);
        assertThat(vectorizedMap.get("hello")).isEqualTo(2);
    }

    @Test
    public void testVectorizedMapSimplePutGetsMaxAndMin() {
        VectorizedMap<String,Integer> vectorizedMap = new VectorizedMap<String, Integer>(Integer.class);
        vectorizedMap.put("hello1", 1);
        vectorizedMap.put("hello2", 2);
        assertThat(vectorizedMap.getMinimum()).isEqualTo(1);
        assertThat(vectorizedMap.getMaximum()).isEqualTo(2);
    }

    @Test
    public void testVectorizedMapPutAll() {
        VectorizedMap<String,Integer> vectorizedMap = new VectorizedMap<String, Integer>(Integer.class);
        vectorizedMap.putAll(getTestMap());
        assertTrue(IntStream.range(0,100)
                .boxed()
                .allMatch(i -> {
                    var contains = vectorizedMap.containsKey(String.valueOf(i));
                    if(!contains) {
                        System.out.println();
                    }
                    return contains;
                })
        );
    }

    @Test
    public void testVectorizedPut() {
        VectorizedMap<String,Integer> vectorizedMap = new VectorizedMap<>(Integer.class);
        getTestMap().forEach(vectorizedMap::put);
        assertTrue(IntStream.range(0,100)
                           .boxed()
                           .allMatch(i -> {
                               var contains = vectorizedMap.containsKey(String.valueOf(i));
                               if(!contains) {
                                   throw new AssertionError();
                               }
                               return contains;
                           })
        );
    }

    @Test
    public void testVectorizedPutThenPutAllAndPutAllThenPut() {
        Map<String,Integer> first = Map.of(
                "ten", 10,
                "11", 11,
                "12", 12
        );

        Map<String,Integer> second = Map.of(
                "one", 1,
                "two", 2,
                "three", 3
        );

        Consumer<VectorizedMap<String,Integer>> map = vectorizedMap -> {
            vectorizedMap.putAll(first);
            second.forEach(vectorizedMap::put);
        };

        Map<String,Integer> ending = new HashMap<>();
        ending.putAll(first);
        ending.putAll(second);

        assertForMap(map, ending);

        map = vectorizedMap -> {
            second.forEach(vectorizedMap::put);
            vectorizedMap.putAll(first);
        };

        assertForMap(map, ending);

    }

    @Test
    public void testRemove() {
        var testMap = getTestMap();
        var testMapAssert = getTestMap();
        testMapAssert.remove("1");
        assertForMap(v -> {
            v.putAll(testMap);
            v.remove("1");
        }, testMapAssert);

        testMapAssert = getTestMap();
        removeSome(testMapAssert);
        testMapAssert.remove("1");
        assertForMap(v -> {
            v.putAll(testMap);
            removeSome(v);
        }, testMapAssert);
    }

    private void removeSome(Map<String, Integer> testMapAssert) {
        IntStream.range(0,10).boxed()
                 .map(String::valueOf)
                .forEach(testMapAssert::remove);
    }

    private void removeSome(VectorizedMap<String, Integer> testMapAssert) {
        IntStream.range(0,10).boxed()
                .map(String::valueOf)
                .forEach(testMapAssert::remove);
    }

    private void removeSomeRemoveAll(VectorizedMap<String, Integer> testMapAssert) {
        List<String> toRemove = IntStream.range(0, 10).boxed()
                .map(String::valueOf)
                .toList();

        testMapAssert.removeAll(toRemove);
    }

    @Test
    public void testRemoveAll() {
        Map<String, Integer> testMap = getTestMap();
        assertForMap(v -> {
            v.putAll(testMap);
            v.removeAll(testMap.keySet());
        }, new HashMap<>());
    }

    @Test
    public void testRemoveAllNotRemovingEvery() {
        Map<String, Integer> assertMap = getTestMap();

        assertForMap(v -> {
            v.putAll(assertMap);
            removeSome(assertMap);
            removeSomeRemoveAll(v);
        }, assertMap);
    }

    public static void assertForMap(Consumer<VectorizedMap<String,Integer>> actions, Map<String,Integer> endingState) {
        VectorizedMap<String,Integer> map = new VectorizedMap<>(Integer.class);
        actions.accept(map);
        endingState.forEach((key,val) -> {
            assertThat(map.containsKey(key)).isTrue();
            assertThat(map.containsValue(val)).isTrue();
        });
        assertThat(map.size()).isEqualTo(endingState.size());
    }

    public Map<String,Integer> getTestMap() {
        return IntStream.range(0,100)
                .boxed()
                .collect(Collectors.toMap(String::valueOf, i -> i));
    }
}