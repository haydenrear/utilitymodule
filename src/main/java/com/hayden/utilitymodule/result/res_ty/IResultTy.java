package com.hayden.utilitymodule.result.res_ty;

import com.hayden.utilitymodule.result.Result;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface IResultTy<R> extends Result.Monadic<R> {

    static <V> IResultTy<V> toRes(V v) {
        if (v instanceof AutoCloseable a) {
            throw new RuntimeException("Did not implement for auto-closable! Probably something to do with scopes to prove only one can be left open and any cannot be accessed twice without knowing.");
//                return (IResultTy<T>) new ClosableResult<>(Optional.of(a));
        }

        return new ResultTyResult<>(Optional.ofNullable(v));
    }

    static <R> IResultTy<R> empty() {
        return new ResultTyResult<>(Optional.empty());
    }


//    Optional<R> optional();

    <V> IResultTy<V> from(V r);

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    <V> IResultTy<V> from(Optional<V> r);

    Stream<R> stream();

    Flux<R> flux();

    Mono<R> mono();


    default boolean isAsyncSub() {
        return false;
    }

    default boolean isZeroOrOneAbstraction() {
        return false;
    }

    default boolean isColdSubscription() {
        throw new RuntimeException("How?");
    }

    default boolean isHotSubscription() {
        throw new RuntimeException("How?");
    }

    default boolean isStream() {
        return false;
    }


    IResultTy<R> filter(Predicate<R> p);

    R get();

    <V> IResultTy<V> flatMap(Function<R, IResultTy<V>> toMap);

    <V> IResultTy<V> map(Function<R, V> toMap);

    R orElse(R r);

    R orElseGet(Supplier<R> r);

    void ifPresent(Consumer<? super R> consumer);

    IResultTy<R> peek(Consumer<? super R> consumer);

    default void forEach(Consumer<? super R> consumer) {
        this.stream().forEach(consumer);
    }

    default boolean isEmpty() {
        return optional().isEmpty();
    }


    default boolean isPresent() {
        return optional().isPresent();
    }
}
