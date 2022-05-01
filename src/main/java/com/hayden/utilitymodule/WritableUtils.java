package com.hayden.utilitymodule;

import org.datavec.api.writable.*;
import org.nd4j.common.util.ArrayUtil;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

public class WritableUtils {


    public static Writable GetWritableComparable(Object o)
    {
        return switch(o)
                {
                    case String s -> new Text(s);
                    case Long l -> new LongWritable(l);
                    case Double[] doubles -> new NDArrayWritable(Nd4j.create(ArrayUtil.toPrimitives(doubles)));
                    case Double d -> new DoubleWritable(d);
                    case Double[][] doubleDouble -> new NDArrayWritable(Nd4j.create(ArrayUtil.toPrimitives(doubleDouble)));
                    default -> new DoubleWritable(0d);
                };
    }

    public static WritableType fromType(Object from)
    {
        return GetWritableComparable(from).getType();
    }

    public static Writable from(Object from)
    {
        return GetWritableComparable(from);
    }

    public static INDArray NdArrayFromPrimitive(Double[][] asks)
    {
        return Nd4j.create(ArrayUtil.toPrimitives(asks));
    }
}
