package com.hayden.utilitymodule.result.map;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.ResultTestModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ParameterizedResultCollectorsTest {


    @Test
    public void paramTest() {
        var found = IntStream.range(0, 100)
                .boxed()
                .map(Object::toString)
                .map(Result::<String, ResultTestModel.TestOneErr>ok)
                .flatMap(toe -> Stream.<Result<String, ResultTestModel.TestOneErr>>of(toe, Result.err(new ResultTestModel.TestOneErr("fail"))))
                .collect(ResultCollectors.from(new ResultTestModel.TestOneAggResp(new ArrayList<>()), new ResultTestModel.TestOneAggErr(new HashSet<>())));

        assertEquals(100, found.r().get().all().size());
        assertEquals(1, found.e().get().allItems().size());

        var foundCollected = found.collectList();
        assertEquals(1, foundCollected.r().get().size());
        assertEquals(1, foundCollected.e().get().size());

        var foundNone = Stream.<Result<String, ResultTestModel.TestOneErr>>empty()
                .collect(ResultCollectors.from(new ResultTestModel.TestOneAggResp(new ArrayList<>()), new ResultTestModel.TestOneAggErr(new HashSet<>())));
        assertEquals(0, foundNone.r().get().all().size());
        assertEquals(0, foundNone.e().get().allItems().size());

        var foundNoneResCollected = foundNone.collectList();
        assertEquals(1, foundNoneResCollected.r().get().size());
        assertEquals(1, foundNoneResCollected.e().get().size());

        var foundOneRes = Stream.of(Result.<String, ResultTestModel.TestOneErr>ok("hello"))
                .collect(ResultCollectors.from(new ResultTestModel.TestOneAggResp(new ArrayList<>()), new ResultTestModel.TestOneAggErr(new HashSet<>())));
        assertEquals(1, foundOneRes.r().get().all().size());
        assertEquals(0, foundOneRes.e().get().allItems().size());

        var foundOneResCollected = foundOneRes.collectList();
        assertEquals(1, foundOneResCollected.r().get().size());
        assertEquals(1, foundOneResCollected.e().get().size());

        var foundOneErr = Stream.of(Result.<String, ResultTestModel.TestOneErr>err(new ResultTestModel.TestOneErr("fail")))
                .collect(ResultCollectors.from(new ResultTestModel.TestOneAggResp(new ArrayList<>()), new ResultTestModel.TestOneAggErr(new HashSet<>())));
        assertEquals(0, foundOneErr.r().get().all().size());
        assertEquals(1, foundOneErr.e().get().allItems().size());

        var foundOneCollected = foundOneErr.collectList();
        assertEquals(1, foundOneCollected.r().get().size());
        assertEquals(1, foundOneCollected.e().get().size());
    }
}