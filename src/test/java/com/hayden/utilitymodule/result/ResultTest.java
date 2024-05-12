package com.hayden.utilitymodule.result;

import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    record TestAgg(Set<Result.Error> errors) implements Result.AggregateError {

    }
    record TestRes(Set<String> values) implements Result.AggregateResponse {

        @Override
        public void add(Result.AggregateResponse aggregateResponse) {
            this.values.addAll(((TestRes)aggregateResponse).values());
        }
    }

    @Test
    void flatMap() {
        var singleMessage = singleMessage();
        Result<String, TestAgg> hello = singleMessage
                .flatMap(t -> Result.ok("hello"))
                .cast();
        assertThat(hello.get()).isEqualTo("hello");
        var hello2 = singleMessage
                .flatMap(t -> Result.emptyError())
                .cast();
        assertThat(hello2.get()).isEqualTo(singleMessage.get());
    }


    @Test
    void all() {
        var singleMessage = singleMessage();
        var errorAndMessage = errorAndMessage();
        var singleError = withSingleError();
        var errorAndMessage2 = errorAndMessage2();
        var multipleErrors = multipleErrors();
        var multipleMessage = multipleMessages();
        var multipleMessageMultipleError = multipleErrorAndMessages();


        Set<Set<Result<TestRes, TestAgg>>> combinations
                = Sets.combinations(Set.of(singleMessage, errorAndMessage, singleError, errorAndMessage2), 4);

        for (var c : combinations) {
            var all = Result.all(c);
            var collected = c.stream().collect(Result.AggregateResultCollector.fromValues(new TestRes(new HashSet<>()), new TestAgg(new HashSet<>())));
            assertAll(
                    () -> assertThat(all.get().values).hasSameElementsAs(List.of("hello", "hello1", "hello2")),
                    () -> assertThat(all.error().getMessages()).hasSameElementsAs(List.of("goodbye1", "goodbye2", "goodbye3")),
                    () -> assertThat(collected.get().values).hasSameElementsAs(List.of("hello", "hello1", "hello2")),
                    () -> assertThat(collected.error().getMessages()).hasSameElementsAs(List.of("goodbye1", "goodbye2", "goodbye3"))
            );


        }
        singleMessage = singleMessage();

        Result<TestRes, TestAgg> all3 = Result.all(List.of(singleMessage));
        var collected3 = Stream.of(singleMessage).collect(Result.AggregateResultCollector.fromValues(new TestRes(new HashSet<>()), new TestAgg(new HashSet<>())));

        assertAll(
                () -> assertThat(all3.get().values).hasSameElementsAs(List.of("hello")),
                () -> assertThat(all3.error()).isNull(),
                () -> assertThat(collected3.get().values).hasSameElementsAs(List.of("hello")),
                () -> assertThat(collected3.error()).isNotNull(),
                () -> assertThat(collected3.error().errors().size()).isZero()
        );

        var all2 = Result.all(List.<Result<TestRes, TestAgg>>of());
        var collected2 = Stream.<Result<TestRes, TestAgg>>of().collect(Result.AggregateResultCollector.fromValues(new TestRes(new HashSet<>()), new TestAgg(new HashSet<>())));

        assertAll(
                () -> assertThat(all2).isNull(),
                () -> assertThat(collected2.error()).isNotNull(),
                () -> assertThat(collected2.error().errors().size()).isZero()
        );

        errorAndMessage2 = errorAndMessage2();

        Result<TestRes, TestAgg> all4 = Result.all(List.of(errorAndMessage2));
        Result<TestRes, TestAgg> collected4 = Stream.of(errorAndMessage2).collect(Result.AggregateResultCollector.fromValues(new TestRes(new HashSet<>()), new TestAgg(new HashSet<>())));

        assertAll(
                () -> assertThat(all4.get().values).hasSameElementsAs(List.of("hello2")),
                () -> assertThat(all4.error().getMessages()).hasSameElementsAs(List.of("goodbye3")),
                () -> assertThat(collected4.get().values).hasSameElementsAs(List.of("hello2")),
                () -> assertThat(collected4.error().getMessages()).hasSameElementsAs(List.of("goodbye3"))
        );

        singleError = withSingleError();

        Result<TestRes, TestAgg> all5 = Result.all(List.of(singleError));
        Result<TestRes, TestAgg> collected5 = Stream.of(singleError).collect(Result.AggregateResultCollector.fromValues(new TestRes(new HashSet<>()), new TestAgg(new HashSet<>())));

        assertAll(
                () -> assertThat(all5.isEmpty()).isTrue(),
                () -> assertThat(all5.error().getMessages()).hasSameElementsAs(List.of("goodbye2")),
                () -> assertThat(collected5.isEmpty()).isFalse(),
                () -> assertThat(collected5.get().values().isEmpty()).isTrue(),
                () -> assertThat(collected5.error().getMessages()).hasSameElementsAs(List.of("goodbye2"))
        );


        assertAll(
                () -> assertThat(multipleErrors.isEmpty()).isTrue(),
                () -> assertThat(multipleErrors.error().getMessages()).hasSameElementsAs(List.of("goodbye9", "goodbye10"))
        );


        assertAll(
                () -> assertThat(multipleMessage.get().values).hasSameElementsAs(List.of("hello9", "hello10", "hello11")),
                () -> assertThat(multipleMessage.error()).isNull()
        );


        assertAll(
                () -> assertThat(multipleMessageMultipleError.get().values).hasSameElementsAs(List.of("hello12", "hello13", "hello14")),
                () -> assertThat(multipleMessageMultipleError.error().getMessages()).hasSameElementsAs(List.of("goodbye11", "goodbye12"))
        );
    }

    private static @NotNull Result<TestRes, TestAgg> errorAndMessage2() {
        Result<TestRes, TestAgg> r3 = Result.from(
                new TestRes(Sets.newHashSet("hello2")),
                new TestAgg(Sets.newHashSet(Result.Error.fromMessage("goodbye3")))
        );
        return r3;
    }

    private static @NotNull Result<TestRes, TestAgg> errorAndMessage() {
        Result<TestRes, TestAgg> r1 = Result.from(
                new TestRes(Sets.newHashSet("hello1")),
                new TestAgg(Sets.newHashSet(Result.Error.fromMessage("goodbye1")))
        );
        return r1;
    }

    private static @NotNull Result<TestRes, TestAgg> singleMessage() {
        Result<TestRes, TestAgg> r = Result.ok(new TestRes(Sets.newHashSet("hello")));
        return r;
    }

    private static @NotNull Result<TestRes, TestAgg> withSingleError() {
        Result<TestRes, TestAgg> r2 = Result.err(
                new TestAgg(Sets.newHashSet(Result.Error.fromMessage("goodbye2")))
        );
        return r2;
    }

    private static @NotNull Result<TestRes, TestAgg> multipleErrors() {
        Result<TestRes, TestAgg> r2;
        r2 = Result.err(
                new TestAgg(Sets.newHashSet(Result.Error.fromMessage("goodbye9"), Result.Error.fromMessage("goodbye10")))
        );
        return r2;
    }

    private static @NotNull Result<TestRes, TestAgg> multipleMessages() {
        Result<TestRes, TestAgg> r2 = Result.ok(new TestRes(Sets.newHashSet("hello9", "hello10", "hello11")));
        return r2;
    }


    private static @NotNull Result<TestRes, TestAgg> multipleErrorAndMessages() {
        return Result.from(
                new TestRes(Sets.newHashSet("hello12", "hello13", "hello14")),
                new TestAgg(Sets.newHashSet(Result.Error.fromMessage("goodbye11"), Result.Error.fromMessage("goodbye12")))
        );
    }
}