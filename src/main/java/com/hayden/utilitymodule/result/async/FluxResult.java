package com.hayden.utilitymodule.result.async;

import com.google.common.collect.Lists;
import com.hayden.utilitymodule.result.res_many.IManyResultTy;
import com.hayden.utilitymodule.result.res_ty.IResultTy;
import com.hayden.utilitymodule.result.res_ty.ResultTyResult;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
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
public class FluxResult<R> implements IAsyncManyResultTy<R> {

    private final AtomicBoolean finished = new AtomicBoolean(false);

    private Flux<R> r;

    public FluxResult(Flux<R> r) {
        this.r = r;
    }


    public void swap(Flux<R> toSwap) {
        this.r = toSwap;
    }


    @Override
    public Stream<R> detachedStream() {
        throw new RuntimeException();
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
    public Mono<R> firstMono() {
        return r.collectList()
                .flatMap(l -> l.size() <= 1
                              ? Mono.justOrEmpty(l.getFirst())
                              : Mono.error(new RuntimeException("Called Mono on stream result with more than one value.")));
    }

    @Override
    public boolean didFinish() {
        return finished.get();
    }

    @Override
    public void doAsync(Consumer<? super R> consumer) {
        logAsync();
        this.r.doOnComplete(() -> finished.set(true))
                .subscribe(consumer);
    }

    @Override
    public R block() throws ExecutionException, InterruptedException {
        return blockFirst();
    }

    @Override
    public R blockFirst() {
        logFluxSingle();
        logThreadStarvation();
        return r.blockFirst();
    }

    @Override
    public R blockLast() {
        logFluxSingle();
        logThreadStarvation();
        return r.blockLast();
    }

    @Override
    public List<R> blockAll() {
        logThreadStarvation();
        return r.collectList().block();
    }

    @Override
    public List<R> blockAll(Duration duration) {
        logThreadStarvation();
        return r.buffer(duration)
                .blockFirst();
    }

    @Override
    public R block(Duration wait) throws ExecutionException, InterruptedException {
        logFluxSingle();
        return this.r.blockFirst(wait);
    }

    private static void logFluxSingle() {
        log.warn("Calling block single on Flux. Could mean that multiple will not be returned.");
    }

    @Override
    public IResultTy<R> filter(Predicate<R> p) {
        return new FluxResult<>(r.filter(p));
    }

    @Override
    public R get() {
        return this.firstOptional().orElse(null);
    }

    @Override
    public <T> IManyResultTy<T> flatMap(Function<R, IResultTy<T>> toMap) {
        return new FluxResult<>(
                r.map(toMap)
                        .flatMap(IResultTy::flux)
        );
    }

    @Override
    public IManyResultTy<R> add(R r) {
        return new FluxResult<>(Flux.concat(this.r, Flux.just(r)));
    }

    @Override
    public IManyResultTy<R> concat(IManyResultTy<R> r) {
        return new FluxResult<>(Flux.concat(this.r, r.flux()));
    }

    @Override
    public <T> IResultTy<T> map(Function<R, T> toMap) {
        return new FluxResult<>(r.map(toMap));
    }

    @Override
    public R orElse(R r) {
        return this.firstOptional().orElse(r);
    }

    @Override
    public R orElseGet(Supplier<R> r) {
        return this.firstOptional().orElseGet(r);
    }

    @Override
    public void ifPresent(Consumer<? super R> consumer) {
        doAsync(consumer);
    }

    @Override
    public IResultTy<R> peek(Consumer<? super R> consumer) {
        return new FluxResult<>(this.r.doOnNext(consumer));
    }

    public Flux<R> r() {
        return r;
    }

    public AtomicBoolean finished() {
        return finished;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (FluxResult) obj;
        return Objects.equals(this.r, that.r) &&
               Objects.equals(this.finished, that.finished);
    }

    @Override
    public int hashCode() {
        return Objects.hash(r, finished);
    }

    @Override
    public String toString() {
        return "FluxResult[" +
               "r=" + r + ", " +
               "finished=" + finished + ']';
    }

    @Override
    public void swap(List<R> toSwap) {
        this.r = Flux.fromIterable(toSwap);
    }
}
