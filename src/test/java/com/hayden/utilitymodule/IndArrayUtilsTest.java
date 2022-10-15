package com.hayden.utilitymodule;

import com.hayden.utilitymodule.scaling.VectorizedMap;
import jdk.incubator.vector.*;
import org.datavec.api.util.ndarray.RecordConverter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.DataSetUtil;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.util.DataSetUtils;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


class IndArrayUtilsTest {
    static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;


    @Test
    public void testINDArrayIndex() {

        INDArray zeros = Nd4j.zeros(3,4,2,5);
        INDArrayIndex[] indArrayIndices = NDArrayIndex.indexesFor(0, 0, 0);
        INDArray indArray = zeros.get(indArrayIndices);
        System.out.println(indArray);
        INDArray zeros1 = Nd4j.zeros(1, 2, 3, 4);
        INDArray indArray1 = zeros1.get(NDArrayIndex.indexesFor(1));
        System.out.println(indArray1);
    }

    @Test
    public void testRecordConverter() {
        Assertions.assertDoesNotThrow(() -> RecordConverter.toTensor(List.of(List.of(List.of()))));
    }



}