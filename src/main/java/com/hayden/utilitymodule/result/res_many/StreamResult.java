package com.hayden.utilitymodule.result.res_many;

import com.hayden.utilitymodule.reflection.TypeReferenceDelegate;
import com.hayden.utilitymodule.result.CachableStream;
import com.hayden.utilitymodule.result.ResultStreamWrapper;
import com.hayden.utilitymodule.result.StreamResultOptions;
import com.hayden.utilitymodule.result.StreamWrapper;
import com.hayden.utilitymodule.result.res_ty.IResultTy;
import com.hayden.utilitymodule.result.stream_cache.CachingOperations;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
public class StreamResult<R> implements IStreamResultTy<R>, CachableStream<R, StreamResult<R>> {

    private ResultTyStreamWrapper<R> r;

    private static class ResultTyStreamWrapper<R> extends ResultStreamWrapper<StreamResult<R>, R> {

        public ResultTyStreamWrapper(StreamResultOptions options, Stream<R> underlying, StreamResult<R> res) {
            super(options, underlying, CachingOperations.ResultTyStreamWrapperOperation.class, res);
        }

        public R first() {
            return this.get(TypeReferenceDelegate.<CachingOperations.RetrieveFirstTy<R>>create(CachingOperations.RetrieveFirstTy.class).get())
                    .get();
        }

        @Override
        public @NotNull Optional<R> findAny() {
            cacheResultsIfNotCached();
            return Optional.ofNullable(first());
        }

        @Override
        public @NotNull Optional<R> findFirst() {
            return findAny();
        }

    }

    public StreamResult(Stream<R> r) {
        this.r = new ResultTyStreamWrapper<>(StreamResultOptions.builder().build(), r, this);
    }

    public static <R> StreamResult<R> of(Stream<IResultTy<R>> stream) {
        return new StreamResult<>(stream.flatMap(IResultTy::stream));
    }

    @Override
    public synchronized void swap(List<R> toSwap) {
        this.swap(toSwap.stream());
    }

    @Override
    public StreamResult<R> swap(Stream<R> toCache) {
        var cached = toCache.toList();
        this.r.swap(cached);

        return new StreamResult<>(cached.stream());
    }

    @Override
    public StreamResult<R> copy() {
        var found = this.r.toList();
        return null;
    }

    @Override
    public Stream<R> detachedStream() {
        var swapped = swap(this.r);
        return swapped.r;
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
        return r.filter(Objects::nonNull);
    }

    @Override
    public Flux<R> flux() {
        return Flux.fromStream(r);
    }

    @Override
    public Mono<R> firstMono() {
        List<R> streamList = this.r.toList();
        swap(streamList);
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
        return firstOptional().orElse(null);
    }

    @Override
    public <T> StreamResult<T> flatMap(Function<R, IResultTy<T>> toMap) {
        return new StreamResult<>(
                r.map(toMap)
                        .flatMap(IResultTy::stream)
        );
    }

    @Override
    public IManyResultTy<R> add(R r) {
        return new StreamResult<>(Stream.concat(this.r, Stream.of(r)));
    }

    @Override
    public IManyResultTy<R> concat(IManyResultTy<R> r) {
        return new StreamResult<>(Stream.concat(r.stream(), this.r));
    }

    @Override
    public <T> StreamResult<T> map(Function<R, T> toMap) {
        return new StreamResult<>(r.map(toMap));
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
    public StreamResult<R> peek(Consumer<? super R> consumer) {
        return new StreamResult<>(this.r.peek(consumer));
    }

    public Stream<R> r() {
        return r;
    }

}
