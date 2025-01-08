package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.result.res_support.many.stream.StreamResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class StreamResultTest {

    @Test
    public void doTestStreamResultCollect() {
        record TestErr() {}
        Result<String, TestErr> sr = new StreamResult<>(Stream.of(Result.ok("first"), Result.ok("second"), Result.ok("third"),
                Result.err(new TestErr())));

        var collected = sr.toList();
        var e = collected.errs();
        var r = collected.res();
        var errors = collected.errsList();
        var results = collected.results();
        var cached = collected.resultCache();

        assertThat(results).containsExactlyElementsOf(List.of("first", "second", "third"));
        assertThat(errors).hasSameElementsAs(List.of(new TestErr()));

        assertThat(cached.size()).isNotZero();

        assertThat(e.size()).isNotZero();
        assertThat(r.size()).isNotZero();
    }

    @Test
    public void doTestResultCollect() {
        record TestErr() {}
        Result<String, TestErr> sr = Result.from("hello", new TestErr());

        var collected = sr.toList();
        var e = collected.errs();
        var r = collected.res();
        var errors = collected.errsList();
        var results = collected.results();
        var cached = collected.resultCache();

        assertThat(results).containsExactlyElementsOf(List.of("hello"));
        assertThat(errors).hasSameElementsAs(List.of(new TestErr()));

        assertThat(cached.size()).isNotZero();

        assertThat(e.size()).isNotZero();
        assertThat(r.size()).isNotZero();
    }

    @Test
    public void doTestResultCollectWithMap() {
        record TestErr() {}
        Result<String, TestErr> sr = Result.from("hello", new TestErr());

        sr = sr.flatMapResult(h -> Result.ok("whatever"));

        var collected = sr.toList();
        var e = collected.errs();
        var r = collected.res();
        var errors = collected.errsList();
        var results = collected.results();
        var cached = collected.resultCache();

        assertThat(results).containsExactlyElementsOf(List.of("whatever"));
        assertThat(errors).hasSameElementsAs(List.of(new TestErr()));

        assertThat(cached.size()).isNotZero();

        assertThat(e.size()).isNotZero();
        assertThat(r.size()).isNotZero();
    }

}
