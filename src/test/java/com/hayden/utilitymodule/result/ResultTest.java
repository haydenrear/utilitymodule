package com.hayden.utilitymodule.result;

import com.google.common.collect.Sets;
import org.assertj.core.util.Lists;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    record TestAgg(List<Result.Error> errors) implements Result.AggregateError {

    }
    record TestRes(List<String> values) implements Result.AggregateResponse {

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
            assertAll(
                    () -> assertThat(all.get().values).hasSameElementsAs(List.of("hello", "hello1", "hello2")),
                    () -> assertThat(all.error().getMessages()).hasSameElementsAs(List.of("goodbye1", "goodbye2", "goodbye3"))
            );
        }
        singleMessage = singleMessage();

        Result<TestRes, TestAgg> all3 = Result.all(List.of(singleMessage));

        assertAll(
                () -> assertThat(all3.get().values).hasSameElementsAs(List.of("hello")),
                () -> assertThat(all3.error()).isNull()
        );

        var all2 = Result.all(List.<Result<TestRes, TestAgg>>of());

        assertAll(
                () -> assertThat(all2).isNull()
        );

        errorAndMessage2 = errorAndMessage2();

        Result<TestRes, TestAgg> all4 = Result.all(List.of(errorAndMessage2));

        assertAll(
                () -> assertThat(all4.get().values).hasSameElementsAs(List.of("hello2")),
                () -> assertThat(all4.error().getMessages()).hasSameElementsAs(List.of("goodbye3"))
        );

        singleError = withSingleError();

        Result<TestRes, TestAgg> all5 = Result.all(List.of(singleError));

        assertAll(
                () -> assertThat(all5.isEmpty()).isTrue(),
                () -> assertThat(all5.error().getMessages()).hasSameElementsAs(List.of("goodbye2"))
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
                new TestRes(Lists.newArrayList("hello2")),
                new TestAgg(Lists.newArrayList(Result.Error.fromMessage("goodbye3")))
        );
        return r3;
    }

    private static @NotNull Result<TestRes, TestAgg> errorAndMessage() {
        Result<TestRes, TestAgg> r1 = Result.from(
                new TestRes(Lists.newArrayList("hello1")),
                new TestAgg(Lists.newArrayList(Result.Error.fromMessage("goodbye1")))
        );
        return r1;
    }

    private static @NotNull Result<TestRes, TestAgg> singleMessage() {
        Result<TestRes, TestAgg> r = Result.ok(new TestRes(Lists.newArrayList("hello")));
        return r;
    }

    private static @NotNull Result<TestRes, TestAgg> withSingleError() {
        Result<TestRes, TestAgg> r2 = Result.err(
                new TestAgg(Lists.newArrayList(Result.Error.fromMessage("goodbye2")))
        );
        return r2;
    }

    private static @NotNull Result<TestRes, TestAgg> multipleErrors() {
        Result<TestRes, TestAgg> r2;
        r2 = Result.err(
                new TestAgg(Lists.newArrayList(Result.Error.fromMessage("goodbye9"), Result.Error.fromMessage("goodbye10")))
        );
        return r2;
    }

    private static @NotNull Result<TestRes, TestAgg> multipleMessages() {
        Result<TestRes, TestAgg> r2 = Result.ok(new TestRes(Lists.newArrayList("hello9", "hello10", "hello11")));
        return r2;
    }


    private static @NotNull Result<TestRes, TestAgg> multipleErrorAndMessages() {
        return Result.from(
                new TestRes(Lists.newArrayList("hello12", "hello13", "hello14")),
                new TestAgg(Lists.newArrayList(Result.Error.fromMessage("goodbye11"), Result.Error.fromMessage("goodbye12")))
        );
    }
}