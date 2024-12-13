package com.hayden.utilitymodule.result.async;

import com.google.common.collect.Lists;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.res_ty.IResultTy;
import com.hayden.utilitymodule.result.res_ty.ResultTyResult;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hayden.utilitymodule.result.Result.logAsync;
import static com.hayden.utilitymodule.result.Result.logThreadStarvation;

@Slf4j
public record MonoResult<R>(Mono<R> r, AtomicBoolean finished) implements IAsyncResultTy<R> {

    public MonoResult(Mono<R> r) {
        this(r, new AtomicBoolean(false));
    }

    @Override
    public boolean isZeroOrOneAbstraction() {
        return true;
    }

    @Override
    public Optional<R> optional() {
        logThreadStarvation();
        var l = Lists.newArrayList(r.flux().toIterable());
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
        logThreadStarvation();
        return flux().toStream();
    }

    @Override
    public Flux<R> flux() {
        return r.flux();
    }

    @Override
    public Mono<R> mono() {
        return flux().collectList()
                .flatMap(l -> l.size() <= 1
                              ? Mono.justOrEmpty(l.getFirst())
                              : Mono.error(new RuntimeException("Called Mono on stream result with more than one value.")));
    }

    @Override
    public boolean didFinish() {
        return finished.get();
    }

    @Override
    public void subscribe(Consumer<? super R> consumer) {
        logAsync();
        this.r.doAfterTerminate(() -> this.finished.set(true))
                .subscribe(consumer);
    }

    @Override
    public R block() throws ExecutionException, InterruptedException {
        logThreadStarvation();
        return r.block();
    }

    @Override
    public R block(Duration wait) throws ExecutionException, InterruptedException {
        return this.r.block(wait);
    }

    @Override
    public IResultTy<R> filter(Predicate<R> p) {
        return new MonoResult<>(r.filter(p));
    }

    @Override
    public R get() {
        log.warn("Calling or else on closable. This probably means you have to close yourself...");
        Result.logClosableMaybeNotClosed();
        return optional().orElse(null);
    }

    @Override
    public <T> IResultTy<T> flatMap(Function<R, IResultTy<T>> toMap) {
        return new MonoResult<>(r.map(toMap).flatMap(IResultTy::mono));
    }

    @Override
    public <T> IResultTy<T> map(Function<R, T> toMap) {
        return new MonoResult<>(r.map(toMap));
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
        subscribe(consumer);
    }

    @Override
    public IResultTy<R> peek(Consumer<? super R> consumer) {
        return new MonoResult<>(this.r.doOnNext(consumer));
    }
}
