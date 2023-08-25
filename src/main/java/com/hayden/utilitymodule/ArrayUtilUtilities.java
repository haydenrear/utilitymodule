package com.hayden.utilitymodule;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

public class ArrayUtilUtilities {

    private ArrayUtilUtilities() {}

    public static double[][] toPrimitive(Double[][] arr) {
        return Arrays.stream(arr)
                .map(ArrayUtils::toPrimitive)
                .toArray(double[][]::new);
    }

    public static long[][] toPrimitive(Long[][] arr) {
        return Arrays.stream(arr)
                .map(ArrayUtils::toPrimitive)
                .toArray(long[][]::new);
    }

    public static double[][][][] fromDoubleCollection(List<List<double[][]>> lst) {
        double[][][][] arr = new double[lst.size()][lst.stream().map(List::size).max(Integer::compareTo).orElse(0)][][];
        for (int i=0; i<lst.size(); ++i) {
            for (int j=0; j<lst.get(0).size(); ++j) {
                arr[i][j] = lst.get(i).get(j);
            }
        }
        return arr;
    }

    public static float[][] toPrimitive(Float[][] arr) {
        return Arrays.stream(arr)
                .map(ArrayUtils::toPrimitive)
                .toArray(float[][]::new);
    }

    public static Double[][] fromPrimitive(double[][] arr) {
        return Arrays.stream(arr)
                .map(d -> Arrays.stream(d).boxed().toArray(Double[]::new))
                .toArray(Double[][]::new);
    }

    public static Long[][] fromPrimitive(long[][] arr) {
        return Arrays.stream(arr)
                .map(d -> Arrays.stream(d).boxed().toArray(Long[]::new))
                .toArray(Long[][]::new);
    }

    public static Float[][] fromPrimitive(float[][] arr) {
        return Arrays.stream(arr)
                .map(d -> {
                    Float[] f = new Float[d.length];
                    for (int i=0; i<d.length; ++i) {
                        f[i] = d[i];
                    }
                    return f;
                })
                .toArray(Float[][]::new);
    }

}
