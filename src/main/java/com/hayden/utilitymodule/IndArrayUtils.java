package com.hayden.utilitymodule;

import org.datavec.api.util.ndarray.RecordConverter;
import org.datavec.api.writable.Writable;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class IndArrayUtils {
    public static List<List<List<Writable>>> convertToWritable(INDArray indArray) {
        int[] size = IntStream.range(0, (int) indArray.size(1))
                        .toArray();
        List<List<Writable>> values = RecordConverter.toRecords(indArray.getRows(size));
        List<Writable> dates = RecordConverter.toRecord(indArray.getRow(0));
        List<List<List<Writable>>> finalValues = new ArrayList<>();
        int i = 0;
        for (var v : dates) {
            finalValues.add(new ArrayList<>());
            ArrayList<Writable> withValues = new ArrayList<>();
            withValues.add(v);
            finalValues.get(i).add(new ArrayList<>());
            ++i;
        }
        for (var v : values) {
            finalValues.get(i).add(v);
            ++i;
        }
        return finalValues;
    }
}
