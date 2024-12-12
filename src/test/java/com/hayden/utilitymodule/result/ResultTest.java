package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.result.agg.AggregateError;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.error.ErrorCollect;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.hayden.utilitymodule.result.ResultTestModel.singleMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ResultTest {

    @SneakyThrows
    @Test
    public void autoClosable() throws InterruptedException {
        AtomicInteger i = new AtomicInteger(0);
        Result.<FileInputStream, ErrorCollect>tryFrom(
                        new FileInputStream("build.gradle.kts"),
                        () -> {
                            i.getAndIncrement();
                            return null;
                        })
                .ifPresent(fi -> {
                    assertEquals(1, i.incrementAndGet());
                    try {
                        assertNotEquals(0, fi.available());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        assertEquals(2, i.get());
    }

    @Test
    public void stream() throws InterruptedException {
        Result.stream(Stream.of("one", "two", "three"))
                .peek(System.out::println)
                .ifPresent(System.out::println);
    }

    @Test
    void flatMap() {
        var singleMessageItem = singleMessage();
        Result<String, ResultTestModel.TestAgg> hello = singleMessageItem
                .flatMap(t -> Result.ok("hello"))
                .cast();
        assertThat(hello.r().get()).isEqualTo("hello");
        AggregateError.StandardAggregateError error = new AggregateError.StandardAggregateError("hello...");
        Result<ResultTestModel.TestRes, AggregateError.StandardAggregateError> hello3 = singleMessageItem
                .flatMapError(e -> Err.err(error))
                .castError();
        assertThat(hello3.error().isEmpty()).isTrue();
        hello3 = singleMessageItem
                .<AggregateError.StandardAggregateError>flatMapError(t -> Err.empty())
                .cast();
        assertThat(hello3.error().isEmpty()).isTrue();

    }

    @Test
    public void testMapErr() {

        Result<String, ResultTestModel.TestErr1> from = Result.from("hello", new ResultTestModel.TestErr1("hello"));
        Result<Integer, ResultTestModel.TestErr> from1 = Result.from(1, new ResultTestModel.TestErr("hello"));

        var n = from.mapError(e -> new ResultTestModel.TestErr1("yes"));
        Assertions.assertTrue(n.error().get().getMessage().equals("yes"));


        Result<Integer, ResultTestModel.TestErr> from2 = Result.ok(1);
        Result<Integer, ResultTestModel.TestErr1> hello = from2.mapError(e -> new ResultTestModel.TestErr1("hello"), new ResultTestModel.TestErr1("hello"));
        assertEquals("hello", hello.error().get().getMessage());
        hello = from2.mapError(e -> new ResultTestModel.TestErr1("hello"));
        Assertions.assertTrue(hello.error().isEmpty());
    }

}
