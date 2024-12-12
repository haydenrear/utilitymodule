package com.hayden.utilitymodule.result.async;

import com.google.common.collect.Lists;
import com.hayden.utilitymodule.result.res_ty.IResultTy;
import com.hayden.utilitymodule.result.res_ty.ResultTyResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hayden.utilitymodule.result.Result.logAsync;
import static com.hayden.utilitymodule.result.Result.logThreadStarvation;

@Slf4j
public record FluxResult<R>(Flux<R> r) implements IAsyncResultTy<R> {

    @Override
    public boolean isAsyncSub() {
        return true;
    }

    @Override
    public boolean isZeroOrOneAbstraction() {
        return false;
    }

    @Override
    public Optional<R> optional() {
        logThreadStarvation();
        var l = Lists.newArrayList(r.toIterable());
        if (l.size() > 1) {
            log.error("Called optional on stream result with more than one value. Returning first.");
        }

        return !l.isEmpty() ? Optional.of(l.getFirst()) : Optional.empty();
    }

    @Override
    public <T> IResultTy<T> from(T r) {
        return new ResultTyResult<>(Optional.ofNullable(r));
    }

    @Override
    public <T> IResultTy<T> from(Optional<T> r) {
        return new ResultTyResult<>(r);
    }

    @Override
    public Stream<R> stream() {
        logAsync();
        return r.toStream();
    }

    @Override
    public Flux<R> flux() {
        return r;
    }

    @Override
    public Mono<R> mono() {
        return r.collectList()
                .flatMap(l -> l.size() <= 1
                              ? Mono.justOrEmpty(l.getFirst())
                              : Mono.error(new RuntimeException("Called Mono on stream result with more than one value.")));
    }

    @Override
    public void subscribe(Consumer<? super R> consumer) {
        logAsync();
        this.r.subscribe(consumer);
    }

    @Override
    public IResultTy<R> filter(Predicate<R> p) {
        return new com.hayden.utilitymodule.result.async.FluxResult<>(r.filter(p));
    }

    @Override
    public R get() {
        return optional().orElse(null);
    }

    @Override
    public <T> com.hayden.utilitymodule.result.async.FluxResult<T> flatMap(Function<R, IResultTy<T>> toMap) {
        return new com.hayden.utilitymodule.result.async.FluxResult<>(
                r.map(toMap)
                        .flatMap(IResultTy::flux)
        );
    }

    @Override
    public <T> IResultTy<T> map(Function<R, T> toMap) {
        return new com.hayden.utilitymodule.result.async.FluxResult<>(r.map(toMap));
    }

    @Override
    public R orElse(R r) {
        return optional().orElse(r);
    }

    @Override
    public R orElseGet(Supplier<R> r) {
        return optional().orElseGet(r);
    }

    @Override
    public void ifPresent(Consumer<? super R> consumer) {
        logAsync();
        this.r.subscribe(consumer);
    }

    @Override
    public com.hayden.utilitymodule.result.async.FluxResult<R> peek(Consumer<? super R> consumer) {
        return new com.hayden.utilitymodule.result.async.FluxResult<>(this.r.map(f -> {
            consumer.accept(f);
            return f;
        }));
    }
}
