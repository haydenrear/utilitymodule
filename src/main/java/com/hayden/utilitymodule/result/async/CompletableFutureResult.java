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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hayden.utilitymodule.result.Result.logAsync;
import static com.hayden.utilitymodule.result.Result.logThreadStarvation;

@Slf4j
public record CompletableFutureResult<R>(CompletableFuture<R> r, AtomicBoolean finished) implements IAsyncResultTy<R> {

    public CompletableFutureResult(CompletableFuture<R> r) {
        this(r, new AtomicBoolean(false));
    }

    @Override
    public boolean isZeroOrOneAbstraction() {
        return true;
    }

    @Override
    public Optional<R> optional() {
        logThreadStarvation();
        try {
            var v = r.get();
            return Optional.ofNullable(v);
        } catch (InterruptedException |
                 ExecutionException e) {
            throw new RuntimeException(e);
        }
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
        return optional().stream();
    }

    @Override
    public Flux<R> flux() {
        return Flux.fromStream(stream());
    }

    @Override
    public Mono<R> mono() {
        return Mono.fromFuture(this.r);
    }

    @Override
    public boolean didFinish() {
        return finished.get();
    }

    @Override
    public void subscribe(Consumer<? super R> consumer) {
        logAsync();
        var c = this.r.thenAcceptAsync(consumer);
        c.thenRun(() -> this.finished.set(true));
    }

    @Override
    public R block() throws ExecutionException, InterruptedException {
        logThreadStarvation();
        var gotten = r.get();
        return gotten;
    }

    @Override
    public R block(Duration wait) throws ExecutionException, InterruptedException {
        try {
            return this.r.get(wait.getSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException t) {
            throw new ExecutionException(t);
        }
    }

    @Override
    public IResultTy<R> filter(Predicate<R> p) {
        return new MonoResult<>(mono())
                .filter(p);
    }

    @Override
    public R get() {
        log.warn("Calling or else on closable. This probably means you have to close yourself...");
        Result.logClosableMaybeNotClosed();
        return optional().orElse(null);
    }

    @Override
    public <T> IResultTy<T> flatMap(Function<R, IResultTy<T>> toMap) {
        return new MonoResult<>(mono())
                .flatMap(toMap);
    }

    @Override
    public <T> IResultTy<T> map(Function<R, T> toMap) {
        return new CompletableFutureResult<>(r.thenApply(toMap));
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
        return new MonoResult<>(mono())
                .peek(consumer);
    }
}
