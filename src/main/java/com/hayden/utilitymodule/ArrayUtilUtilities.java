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

}
