package com.hayden.utilitymodule;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;

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
