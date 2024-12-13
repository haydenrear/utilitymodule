package com.hayden.utilitymodule.result.agg;

import com.hayden.utilitymodule.result.ResultTy;
import com.hayden.utilitymodule.result.res_ty.IResultTy;
import com.hayden.utilitymodule.result.res_many.StreamResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface Responses {

    interface AggregateResponse extends Agg {
    }

    interface ParamAggregateResponse<T> extends Agg.ParameterizedAgg<T> {
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    final class Ok<R> extends ResultTy<R> {

        public Ok(R r) {
            super(r);
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        public Ok(Optional<R> r) {
            super(r);
        }

        public Ok(Stream<R> r) {
            super(r);
        }

        public Ok(IResultTy<R> r) {
            super(r);
        }

        public Ok(Mono<R> r) {
            super(r);
        }

        public Ok(Flux<R> r) {
            super(r);
        }

        public static <R> Ok<R> ok(R r) {
            return new Ok<>(r);
        }

        public static <R> Ok<R> stream(Stream<R> r) {
            return new Ok<>(r);
        }

        public static <R> Ok<R> ok(IResultTy<R> r) {
            return new Ok<>(r);
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        public static <R> Ok<R> ok(Optional<R> r) {
            return new Ok<>(r);
        }

        public static <R> Ok<R> empty() {
            return new Ok<>(Optional.empty());
        }

        public <S> Ok<S> mapResult(Function<R, S> toMap) {
            if (this.t.isPresent())
                return Ok.ok(toMap.apply(t.get()));

            return Ok.empty();
        }

        public <S> Ok<S> flatMapResult(Function<R, Ok<S>> toMap) {
            return switch(this.t) {
                case StreamResult<R> s -> {
                    yield Ok.ok(s.map(st-> {
                        var mapped = toMap.apply(st);
                        return mapped.get();
                    }));
                }
                default -> {
                    if (this.t.isPresent())
                        yield toMap.apply(t.get());

                    yield Ok.empty();
                }

            };
        }

        public Ok<R> filterResult(Function<R, Boolean> b) {
            if (this.t.isPresent() && b.apply(t.get())) {
                return this;
            }

            return Ok.empty();
        }

        public <U> Ok<U> cast() {
            if (t.isEmpty())
                return Ok.empty();
            try {
                return this.mapResult(s -> (U) s);
            } catch (ClassCastException c) {
                return Ok.empty();
            }
        }

        public R orElseRes(R orRes) {
            return this.t.orElse(orRes);
        }

        public R orElseGetRes(Supplier<R> orRes) {
            return this.t.orElseGet(orRes);
        }

        public Ok<R> orRes(Supplier<Ok<R>> orRes) {
            if (this.t.isPresent())
                return this;

            return orRes.get();
        }

    }
}
