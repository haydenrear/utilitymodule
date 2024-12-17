package com.hayden.utilitymodule.result.res_ty;

import com.google.common.collect.Lists;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.res_many.IManyResultTy;
import com.hayden.utilitymodule.result.res_many.ListResult;
import com.hayden.utilitymodule.result.res_single.ISingleResultTy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface IResultTy<R> extends Result.Monadic<R> {

    static <V> IResultTy<V> toRes(V v) {
        if (v instanceof AutoCloseable a) {
            return (IResultTy<V>) new ClosableResult<>(Optional.of(a));
        }

        return new ResultTyResult<>(Optional.ofNullable(v));
    }

    static <R> IResultTy<R> empty() {
        return new ResultTyResult<>(Optional.empty());
    }

    Stream<R> detachedStream();

    <V> IResultTy<V> from(V r);

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    <V> IResultTy<V> from(Optional<V> r);

    Stream<R> stream();

    Flux<R> flux();

    Mono<R> firstMono();

    ISingleResultTy<R> single();

    IResultTy<R> filter(Predicate<R> p);

    R get();

    <V> IResultTy<V> flatMap(Function<R, IResultTy<V>> toMap);

    <V> IResultTy<V> map(Function<R, V> toMap);

    void ifPresent(Consumer<? super R> consumer);

    IResultTy<R> peek(Consumer<? super R> consumer);

    boolean isPresent();

    default Optional<R> firstOptional(boolean keepAll) {
        return firstOptional();
    }

    default IManyResultTy<R> many() {
        if (this instanceof IManyResultTy<R> t) {
            return t;
        }

        return new ListResult<>(Lists.newArrayList(this.get()));
    }


    default boolean isAsyncSub() {
        return false;
    }

    default boolean isZeroOrOneAbstraction() {
        return false;
    }

    default boolean isStream() {
        return false;
    }

    default void forEach(Consumer<? super R> consumer) {
        this.stream().forEach(consumer);
    }


    default boolean isEmpty() {
        return !isPresent();
    }

}
