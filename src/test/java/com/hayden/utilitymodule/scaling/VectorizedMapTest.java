package com.hayden.utilitymodule.scaling;

import org.junit.jupiter.api.Test;

import java.util.Map;
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

    public Map<String,Integer> getTestMap() {
        return IntStream.range(0,100)
                .boxed()
                .collect(Collectors.toMap(String::valueOf, i -> i));
    }
}