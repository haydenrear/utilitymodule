package com.hayden.utilitymodule.result.res_many;

import com.hayden.utilitymodule.result.res_ty.IResultTy;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
public class ListResult<R> implements IStreamResultTy<R> {

    private List<R> r;

    public ListResult(List<R> r) {
        this.r = r;
    }

    public static <R> ListResult<R> of(Stream<IResultTy<R>> stream) {
        return new ListResult<>(stream.flatMap(IResultTy::stream).toList());
    }

    @Override
    public synchronized void swap(List<R> toSwap) {
        this.r = toSwap;
    }


    @Override
    public Stream<R> detachedStream() {
        return this.r.stream();
    }

    @Override
    public <T> IResultTy<T> from(T r) {
        return new ListResult<>(Optional.ofNullable(r).stream().toList());
    }

    @Override
    public <T> IResultTy<T> from(Optional<T> r) {
        return new ListResult<>(r.stream().toList());
    }

    @Override
    public Stream<R> stream() {
        return r.stream();
    }

    @Override
    public Flux<R> flux() {
        return Flux.fromStream(r.stream());
    }

    @Override
    public Mono<R> firstMono() {
        List<R> streamList = this.r;
        swap(streamList);
        return streamList.size() <= 1
               ? Mono.justOrEmpty(streamList.getFirst())
               : Mono.error(new RuntimeException("Called get Mono on list with more than 1."));
    }

    @Override
    public IResultTy<R> filter(Predicate<R> p) {
        return new ListResult<>(r.stream().filter(p).toList());
    }

    @Override
    public R get() {
        return firstOptional().orElse(null);
    }

    @Override
    public <T> ListResult<T> flatMap(Function<R, IResultTy<T>> toMap) {
        return new ListResult<>(
                r.stream().map(toMap)
                        .flatMap(IResultTy::stream)
                        .toList()
        );
    }

    @Override
    public IManyResultTy<R> add(R r) {
        this.r.add(r);
        return this;
    }

    @Override
    public IManyResultTy<R> concat(IManyResultTy<R> r) {
        r.stream().forEach(t -> this.r.add(t));
        return this;
    }

    @Override
    public <T> ListResult<T> map(Function<R, T> toMap) {
        return new ListResult<>(r.stream().map(toMap).toList());
    }

    @Override
    public R orElse(R r) {
        return firstOptional().orElse(r);
    }

    @Override
    public R orElseGet(Supplier<R> r) {
        return firstOptional().orElseGet(r);
    }

    @Override
    public void ifPresent(Consumer<? super R> consumer) {
        this.r.forEach(consumer);
    }

    @Override
    public ListResult<R> peek(Consumer<? super R> consumer) {
        return new ListResult<>(this.r.stream().peek(consumer).toList());
    }

    public Stream<R> r() {
        return r.stream();
    }

}
