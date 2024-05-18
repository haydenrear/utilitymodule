package com.hayden.utilitymodule.result;

import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

@RequiredArgsConstructor
public abstract class ResultCollectors<
        T extends Result.AggregateResponse,
        E extends Error.AggregateError,
        R1 extends Result<R1T, R1E>,
        R1T,
        R1E extends Error
        >
        implements Collector<R1, Result<T, E>, Result<T, E>> {

    protected final T aggregateResponse;
    protected final E aggregateError;

    public static <
            T extends Result.AggregateResponse,
            E extends Error.AggregateError,
            R1, E1 extends Error
            >
    ResultCollectors<T, E, Result<R1, E1>, R1, E1> from(
            T t,
            E e,
            Function<Result<R1, E1>, Optional<T>> result,
            Function<Result<R1, E1>, Optional<E>> error
    ) {
        return new AggregateMappingResultCollector<>(t, e, result, error);
    }

    public static <
            T extends Result.AggregateResponse,
            E extends Error.AggregateError
            >
    ResultCollectors<T, E, Result<T, E>, T, E> from(
            T t, E e
    ) {
        return AggregateResultCollector.fromValues(t, e);
    }

    public static class AggregateResultCollector<T extends Result.AggregateResponse, E extends Error.AggregateError>
            extends ResultCollectors<T, E, Result<T, E>, T, E> {

        public AggregateResultCollector(T t, E e) {
            super(t, e);
        }


        public static <T extends Result.AggregateResponse, E extends Error.AggregateError> AggregateResultCollector<T, E> fromValues(T t, E e) {
            return new AggregateResultCollector<>(t, e);
        }

        public static <T extends Result.AggregateResponse, E extends Error.AggregateError> AggregateResultCollector<T, E> toResult(Supplier<T> t, Supplier<E> e) {
            return new AggregateResultCollector<>(t.get(), e.get());
        }

        @Override
        public Supplier<Result<T, E>> supplier() {
            return () -> Result.from(aggregateResponse, aggregateError);
        }

        @Override
        public BiConsumer<Result<T, E>, Result<T, E>> accumulator() {
            return (r1, r2) -> {
                r2.ifPresent(aggregateResponse::add);
                Optional.ofNullable(r2.error()).map(E::errors).ifPresent(aggregateError::addError);
            };
        }

        @Override
        public BinaryOperator<Result<T, E>> combiner() {
            return (r1, r2) -> {
                r2.ifPresent(aggregateResponse::add);
                Optional.ofNullable(r2.error()).map(E::errors).ifPresent(aggregateError::addError);
                return Result.from(aggregateResponse, aggregateError);
            };
        }

        @Override
        public Function<Result<T, E>, Result<T, E>> finisher() {
            return r1 -> Result.from(aggregateResponse, aggregateError);
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }
    }

    public static class AggregateMappingResultCollector<T extends Result.AggregateResponse, E extends Error.AggregateError, R1, E1 extends Error>
            extends ResultCollectors<T, E, Result<R1, E1>, R1, E1> {


        private final Function<Result<R1, E1>, Optional<T>> mapResult;
        private final Function<Result<R1, E1>, Optional<E>> mapError;

        public AggregateMappingResultCollector(T t, E e,
                                               Function<Result<R1, E1>, Optional<T>> result,
                                               Function<Result<R1, E1>, Optional<E>> error) {
            super(t, e);
            mapResult = result;
            mapError = error;
        }

        @Override
        public Supplier<Result<T, E>> supplier() {
            return () -> Result.fromValues(this.aggregateResponse, this.aggregateError);
        }

        @Override
        public BiConsumer<Result<T, E>, Result<R1, E1>> accumulator() {
            return (r1, r2) -> {
                mapResult.apply(r2).ifPresent(a -> r1.ifPresent(b -> b.add(a)));
                mapError.apply(r2).ifPresent(a -> Optional.ofNullable(r1.error()).ifPresent(b -> b.addError(a)));
            };
        }

        @Override
        public BinaryOperator<Result<T, E>> combiner() {
            return (r1, r2) -> {
                r2.ifPresent(aggregateResponse::add);
                Optional.ofNullable(r2.error()).map(E::errors).ifPresent(aggregateError::addError);
                return Result.from(aggregateResponse, aggregateError);
            };
        }

        @Override
        public Function<Result<T, E>, Result<T, E>> finisher() {
            return r1 -> Result.from(aggregateResponse, aggregateError);
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Set.of();
        }
    }
}
