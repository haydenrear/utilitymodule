package com.hayden.utilitymodule.result.map;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.agg.AggregateError;
import com.hayden.utilitymodule.result.error.ErrorCollect;
import com.hayden.utilitymodule.result.agg.Responses;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;

public interface AggregateResultCollectors {

    class AggregateResultCollector<T extends Responses.AggregateResponse, E extends AggregateError>
            extends ResultCollectors<T, E, Result<T, E>, T, E> {

        protected final T aggregateResponse;
        protected final E aggregateError;

        public AggregateResultCollector(T t, E e) {
            this.aggregateResponse = t;
            this.aggregateError = e;
        }


        public static <T extends Responses.AggregateResponse, E extends AggregateError> AggregateResultCollector<T, E> fromValues(T t, E e) {
            return new AggregateResultCollector<>(t, e);
        }

        public static <T extends Responses.AggregateResponse, E extends AggregateError> AggregateResultCollector<T, E> toResult(Supplier<T> t, Supplier<E> e) {
            return new AggregateResultCollector<>(t.get(), e.get());
        }

        @Override
        public Supplier<Result<T, E>> supplier() {
            return () -> Result.from(aggregateResponse, aggregateError);
        }

        @Override
        public BiConsumer<Result<T, E>, Result<T, E>> accumulator() {
            return (r1, r2) -> {
                r2.ifPresent(aggregateResponse::addAgg);
                r2.error().map(E::errors).ifPresent(aggregateError::addError);
            };
        }

        @Override
        public BinaryOperator<Result<T, E>> combiner() {
            return (r1, r2) -> {
                r2.ifPresent(aggregateResponse::addAgg);
                r2.error().map(E::errors).ifPresent(aggregateError::addError);
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

    class AggregateMappingResultCollector<T extends Responses.AggregateResponse, E extends AggregateError, R1, E1 extends ErrorCollect>
            extends ResultCollectors<T, E, Result<R1, E1>, R1, E1> {

        protected final T aggregateResponse;
        protected final E aggregateError;

        private final Function<Result<R1, E1>, Optional<T>> mapResult;
        private final Function<Result<R1, E1>, Optional<E>> mapError;

        public AggregateMappingResultCollector(T t, E e,
                                               Function<Result<R1, E1>, Optional<T>> result,
                                               Function<Result<R1, E1>, Optional<E>> error) {
            this.aggregateResponse = t;
            this.aggregateError = e;
            mapResult = result;
            mapError = error;
        }

        @Override
        public Supplier<Result<T, E>> supplier() {
            return () -> Result.from(this.aggregateResponse, this.aggregateError);
        }

        @Override
        public BiConsumer<Result<T, E>, Result<R1, E1>> accumulator() {
            return (r1, r2) -> {
                mapResult.apply(r2).ifPresent(a -> r1.ifPresent(b -> b.addAgg(a)));
                mapError.apply(r2).ifPresent(a -> r1.error().ifPresent(b -> b.addError(a)));
            };
        }

        @Override
        public BinaryOperator<Result<T, E>> combiner() {
            return (r1, r2) -> {
                r2.ifPresent(aggregateResponse::addAgg);
                Optional.ofNullable(r2.error().get()).map(E::errors).ifPresent(aggregateError::addError);
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
