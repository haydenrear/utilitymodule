package com.hayden.utilitymodule.result.res_many;

import com.hayden.utilitymodule.result.res_ty.IResultTy;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
public class StreamResult<R> implements IStreamResultTy<R> {

    private Stream<R> r;

    public StreamResult(Stream<R> r) {
        this.r = r;
    }

    public static <R> StreamResult<R> of(Stream<IResultTy<R>> stream) {
        return new StreamResult<>(stream.flatMap(IResultTy::stream));
    }

    public synchronized void swap(Stream<R> toSwap) {
        this.r = toSwap;
    }

    // TODO: remove into res_single?
    @Override
    public Optional<R> optional() {
        var l = r.toList();
        if (l.size() > 1) {
            log.error("Called optional on stream result with more than one value. Returning first.");
        }

        swap(l.stream());

        return !l.isEmpty() ? Optional.of(l.getFirst()) : Optional.empty();
    }

    @Override
    public <T> IResultTy<T> from(T r) {
        return new StreamResult<>(Stream.ofNullable(r));
    }

    @Override
    public <T> IResultTy<T> from(Optional<T> r) {
        return new StreamResult<>(r.stream());
    }

    @Override
    public Stream<R> stream() {
        return r;
    }

    @Override
    public Flux<R> flux() {
        return Flux.fromStream(r);
    }

    @Override
    public Mono<R> mono() {
        List<R> streamList = this.r.toList();
        swap(streamList.stream());
        return streamList.size() <= 1
               ? Mono.justOrEmpty(streamList.getFirst())
               : Mono.error(new RuntimeException("Called get Mono on list with more than 1."));
    }

    @Override
    public IResultTy<R> filter(Predicate<R> p) {
        return new StreamResult<>(r.filter(p));
    }

    @Override
    public R get() {
        return optional().orElse(null);
    }

    @Override
    public <T> StreamResult<T> flatMap(Function<R, IResultTy<T>> toMap) {
        return new StreamResult<>(
                r.map(toMap)
                        .flatMap(IResultTy::stream)
        );
    }

    @Override
    public <T> StreamResult<T> map(Function<R, T> toMap) {
        return new StreamResult<>(r.map(toMap));
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
        this.r.forEach(consumer);
    }

    @Override
    public StreamResult<R> peek(Consumer<? super R> consumer) {
        return new StreamResult<>(this.r.peek(consumer));
    }

    public Stream<R> r() {
        return r;
    }

}
