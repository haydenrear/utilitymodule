package com.hayden.utilitymodule.result.agg;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.ResultTy;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.res_many.IManyResultTy;
import com.hayden.utilitymodule.result.res_many.ListResult;
import com.hayden.utilitymodule.result.res_ty.IResultTy;
import com.hayden.utilitymodule.result.res_many.StreamResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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
            return switch(this.t) {
                case IManyResultTy<R> s ->
                        Ok.ok(s.map(toMap));
                default -> {
                    if (this.t.isPresent())
                        yield Ok.ok(toMap.apply(t.get()));

                    yield Ok.empty();
                }

            };

        }

        public <S> Ok<S> flatMapResult(Function<R, Ok<S>> toMap) {
            return switch(this.t) {
                case IManyResultTy<R> s ->
                        Ok.ok(s.flatMap(st-> {
                            var mapped = toMap.apply(st);
                            return mapped.t;
                        }));
                default -> {
                    if (this.t.isPresent())
                        yield toMap.apply(t.get());

                    yield Ok.empty();
                }

            };
        }

        public <U> Ok<U> cast() {
            return switch (this.t) {
                case IManyResultTy<R> s ->
                        Ok.ok(s.map(r -> (U) r));
                default -> {
                    if (t.isEmpty())
                        yield Ok.empty();
                    try {
                        yield this.mapResult(s -> (U) s);
                    } catch (ClassCastException c) {
                        yield Ok.empty();
                    }
                }
            };
        }

        public R orElseRes(R orRes) {
            return this.t.orElse(orRes);
        }

    }
}
