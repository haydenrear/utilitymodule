package com.hayden.utilitymodule.result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hayden.utilitymodule.Either;
import com.hayden.utilitymodule.reflection.TypeReferenceDelegate;
import com.hayden.utilitymodule.result.agg.Responses;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.map.StreamResultCollector;
import com.hayden.utilitymodule.result.res_many.IManyResultTy;
import com.hayden.utilitymodule.result.res_ty.IResultTy;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contains an Ok and an Err, and these have compositions that can be aggregate
 * and contain multiple, and they have variations of the monad that can collect
 * and iterate over multiple of the item T and E. Ok can be composed of Closable
 * or Async types as well as Stream or Optional.
 */
@Slf4j
public class StreamResult<R, E> implements Result<R, E>, CachableStream<Result<R, E>, StreamResult<R, E>> {

    @Override
    public Stream<Result<R, E>> stream() {
        return r;
    }

    private static class ResultStreamWrapper<R, E> extends StreamWrapper<StreamResult<R, E>, Result<R, E>> {

        public ResultStreamWrapper(StreamResultOptions options, Stream<Result<R, E>> underlying) {
            super(options, underlying, ResultStreamCacheOperation.class);
        }
    }

    private final ResultStreamWrapper<R, E> r;

    private Stream<Result<R, E>> newStream() {
        List<Result<R, E>> list = this.r.toList();
        this.swap(list);
        return list.stream();
    }

    public StreamResult(Stream<Result<R, E>> r) {
        this(r, StreamResultOptions.builder().build());
    }

    public StreamResult(Stream<Result<R, E>> r, StreamResultOptions options) {
        this.r = new ResultStreamWrapper<>(options, r);
        if (options.cache())
            this.r.cacheResults(this);
    }

    public void swap(List<Result<R, E>> toSwap) {
        this.r.swap(toSwap);
    }

    @Override
    public StreamResult<R, E> swap(Stream<Result<R, E>> toCache) {
        List<Result<R, E>> thisList = toCache.toList();
        swap(thisList);

        return new StreamResult<>(thisList.stream());
    }

    public static <T, E> StreamResult<T, E> stream(Stream<T> o) {
        try {
            return StreamResult.ok(o);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public Result<List<R>, List<E>> collectList() {
        return toResultLists();
    }

    public static <T, E> StreamResult<T, E> from(Stream<Result<T, E>> r) {
        return new StreamResult<>(r);
    }

    public static <R, E> StreamResult<R, E> ok(R r) {
        return new StreamResult<>(Stream.of(Result.ok(r)));
    }

    public static <R, E> StreamResult<R, E> resOk(R r) {
        return new StreamResult<>(Stream.of(Result.ok(r)));
    }

    public static <R, E> StreamResult<R, E> ok(IResultTy<R> r) {
        return new StreamResult<>(Stream.of(Result.ok(r)));
    }

    public static <R, E> StreamResult<R, E> ok(Stream<R> r) {
        return new StreamResult<>(r.map(Result::ok));
    }

    public static <R, E> StreamResult<R, E> ok(Responses.Ok<R> r) {
        return new StreamResult<>(Stream.of(Result.ok(r)));
    }

    public static <R, E> StreamResult<R, E> err(E r) {
        return new StreamResult<>(Stream.of(Result.err(r)));
    }

    public static <R, E> StreamResult<R, E> err(Err<E> r) {
        return new StreamResult<>(Stream.of(Result.err(r)));
    }

    @Override
    public Err<E> e() {
        return null;
    }

    public <U> StreamResult<U, E> map(Function<R, U> mapper) {
        return new StreamResult<>(this.r.map(res -> res.map(mapper)));
    }

    public StreamResult<R, E> peek(Consumer<R> mapper) {
        return new StreamResult<>(this.r.peek(res -> res.peek(mapper)));
    }


    public <U> StreamResult<U, E> map(Function<R, U> mapper, Supplier<E> err) {
        return new StreamResult<>(r.map(t -> this.map(mapper).or(() -> Result.err(err.get()))));
    }

    @Override
    public Result<R, E> or(Supplier<Result<R, E>> s) {

        return Result.super.or(s);
    }

    public <E1> StreamResult<R, E1> mapError(Function<E, E1> mapper) {
        return new StreamResult<>(this.r.map(res -> res.mapError(mapper)));
    }

    public StreamResult<R, E> filterErr(Predicate<E> b) {
        return new StreamResult<>(this.r.map(res -> res.filterErr(b)));
    }

    public StreamResult<R, E> filterResult(Predicate<R> b) {
        return new StreamResult<>(this.r.map(res -> res.filterResult(b)));
    }

    public void close() {
        this.r.close();
    }

    public static <E, T> StreamResult<T, E> empty() {
        return new StreamResult<>(Stream.empty(), StreamResultOptions.builder()
                .empty(true)
                .build());
    }

    public <E1> StreamResult<R, E1> mapError(Function<E, E1> mapper, E1 defaultValue) {
        return mapError(mapper);
    }

    public <U> StreamResult<U, E> flatMap(Function<R, Result<U, E>> mapper) {
        return new StreamResult<>(this.r.map(res -> res.flatMap(mapper)));
    }

    public R orElseRes(R or) {
        if (!((boolean) this.r.get(StreamWrapper.HasResult.class))) {
            Responses.Ok<R> r = (Responses.Ok<R>) this.r.get(StreamWrapper.RetrieveRes.class);
            if (r != null)
                return r.get();
        }

        return or;
    }

    public R orElseErrRes(Function<Err<E>, R> or) {
        if (!((boolean) this.r.get(StreamWrapper.IsCompletelyEmpty.class))) {
            Responses.Ok<R> r = (Responses.Ok<R>) this.r.get(StreamWrapper.RetrieveRes.class);
            if (r != null)
                return r.get();
        }

        return or.apply((Err<E>) this.r.get(StreamWrapper.RetrieveError.class));
    }

    public StreamResult<R, E> orElseErr(StreamResult<R, E> or) {
//        if (this.e.isPresent())
//            return this;
//
//        or.r.t = this.r.t;

        return or;
    }

    public <U> StreamResult<U, E> flatMap(Function<R, Result<U, E>> mapper, Supplier<E> errorSupplier) {
        return new StreamResult<>(this.r.flatMap(m -> m.toStream().map(mapper)))
                .orError(() -> Err.err(errorSupplier.get()));
    }

    @Override
    public StreamResult<R, E> orError(Supplier<Err<E>> s) {
        if (this.r.get(StreamWrapper.RetrieveError.class) == null)
            return new StreamResult<>(Stream.concat(this.stream(), Stream.of(Result.err(s.get()))));

        return this;
    }

    public <U> StreamResult<U, E> flatMapResult(Function<R, Result<U, E>> mapper) {
        return flatMap(mapper);
    }

    public boolean isOk() {
        boolean d = (boolean) r.get(StreamWrapper.IsCompletelyEmpty.class);
        return !d;
    }


    public Optional<R> toOptional() {
        return r.map(Result::toOptional)
                .flatMap(Optional::stream)
                .findAny();
    }

    public Optional<R> optional() {
        return toOptional();
    }

    public <U> StreamResult<U, E> cast() {
        return StreamResult.from(r.map(t -> t.map(u -> (U) u)));
    }

    public <V> StreamResult<R, V> castError() {
        return StreamResult.from(r.map(t -> t.mapError(u -> (V) u)));
    }

    public <R extends Result<U, V>, U, V> R cast(TypeReference<R> refDelegate) {
        return (R) this.map(c -> (U) c);
    }

    public StreamResult<R, E> doOnError(Consumer<E> e) {
        return new StreamResult<>(this.r.map(res -> res.doOnError(e)));
    }

    public void doOnEach(Consumer<R> e) {
        this.r.forEach(res -> res.doOnEach(e));
    }

    /**
     * If underlying is stream, then collect to list, otherwise then
     * will be lists of 1.
     *
     * @return
     */
    public Result<List<R>, List<E>> toResultLists() {
        return this.toEntryStream().collect(new StreamResultCollector<>());
    }

    public Stream<Either<Responses.Ok<R>, Err<E>>> toEntryStream() {
        List<Either<Responses.Ok<R>, Err<E>>> l = this.r
                .map(t -> Either.<Responses.Ok<R>, Err<E>>from(t.r(), t.e()))
                .collect(Collectors.toCollection(ArrayList::new));

        return l.stream();
    }

    public Responses.Ok<R> r() {
        Responses.Ok<R> reStreamResult = r.get(TypeReferenceDelegate.<StreamWrapper.RetrieveRes<R, E>>create(StreamWrapper.RetrieveRes.class).get());
        return reStreamResult;
    }

    @Override
    public Stream<R> toStream() {
        return this.r.flatMap(r -> r.r().stream());
    }


}
