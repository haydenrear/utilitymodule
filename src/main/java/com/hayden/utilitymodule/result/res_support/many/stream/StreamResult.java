package com.hayden.utilitymodule.result.res_support.many.stream;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hayden.utilitymodule.Either;
import com.hayden.utilitymodule.reflection.TypeReferenceDelegate;
import com.hayden.utilitymodule.result.*;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.map.StreamResultCollector;
import com.hayden.utilitymodule.result.ok.Ok;
import com.hayden.utilitymodule.result.res_support.one.ResultTy;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachableStream;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import com.hayden.utilitymodule.result.res_support.many.stream.stream_cache.CachingOperations;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

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
public class StreamResult<R, E> implements ManyResult<R, E>, CachableStream<Result<R, E>, StreamResult<R, E>> {

    private final StreamResultStreamWrapper<R, E> r;


    protected static class StreamResultStreamWrapper<R, E> extends ResultStreamWrapper<StreamResult<R, E>, Result<R, E>> {

        public StreamResultStreamWrapper(StreamResultOptions options, Stream<Result<R, E>> underlying,
                                         StreamResult<R, E> res) {
            super(options, underlying, CachingOperations.ResultStreamCacheOperation.class, res);
        }

        public Ok<R> getOk() {
            return Result.fromOpt(
                            TypeReferenceDelegate.<CachingOperations.RetrieveRes<R, E>>create(CachingOperations.RetrieveRes.class),
                            new SingleError.StandardError("Failed to parse type reference delegate for %s".formatted(CachingOperations.RetrieveFirstRes.class.getName()))
                    )
                    .flatMapResult(this::get)
                    .peekError(se -> log.error("Found err: {}", se))
                    .r()
                    .orElse(Ok.empty());
        }

        public Err<E> getErr() {
            return Result.fromOpt(
                            TypeReferenceDelegate.<CachingOperations.RetrieveError<R, E>>create(CachingOperations.RetrieveError.class),
                            new SingleError.StandardError("Failed to parse type reference delegate for %s".formatted(CachingOperations.RetrieveFirstRes.class.getName()))
                    )
                    .flatMapResult(this::get)
                    .peekError(se -> log.error("Found err: {}", se))
                    .r()
                    .orElse(Err.empty());
        }

        public Result<R, E> first() {
            return Result.fromOpt(
                            TypeReferenceDelegate.<CachingOperations.RetrieveFirstRes<R, E>>create(CachingOperations.RetrieveFirstRes.class),
                            new SingleError.StandardError("Failed to parse type reference delegate for %s".formatted(CachingOperations.RetrieveFirstRes.class.getName()))
                    )
                    .flatMapResult(this::get)
                    .peekError(se -> log.error("Found err: {}", se))
                    .r()
                    .orElse(Result.empty());
        }

        @Override
        public @NotNull Optional<Result<R, E>> findAny() {
            cacheResultsIfNotCached();
            return Optional.ofNullable(first());
        }

        @Override
        public @NotNull Optional<Result<R, E>> findFirst() {
            return findAny();
        }
    }

    public StreamResult(Stream<Result<R, E>> r) {
        this(r, StreamResultOptions.builder().build());
    }

    public StreamResult(Stream<Result<R, E>> r, StreamResultOptions options) {
        this.r = new StreamResultStreamWrapper<>(options, r, this);
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

    public static <R, E> StreamResult<R, E> ok(IResultItem<R> r) {
        return new StreamResult<>(Stream.of(Result.ok(r)));
    }

    public static <R, E> StreamResult<R, E> ok(Stream<R> r) {
        return new StreamResult<>(r.map(Result::ok));
    }

    public static <R, E> StreamResult<R, E> ok(Ok<R> r) {
        return new StreamResult<>(Stream.of(Result.ok(r)));
    }

    public static <R, E> StreamResult<R, E> err(E r) {
        return new StreamResult<>(Stream.of(Result.err(r)));
    }

    public static <R, E> StreamResult<R, E> err(Err<E> r) {
        return new StreamResult<>(Stream.of(Result.err(r)));
    }

    public static <E, T> StreamResult<T, E> empty() {
        return new StreamResult<>(Stream.empty(), StreamResultOptions.builder()
                .empty(true)
                .build());
    }

    @Override
    public Stream<Result<R, E>> stream() {
        return r.underlying;
    }

    @Override
    public Ok<R> r() {
        return this.r.getOk();
    }

    @Override
    public Err<E> e() {
        return this.r.getErr();
    }

    @Override
    public OneResult<R, E> one() {
        var last = this.r.cacheResultsIfNotCachedWithList(c -> {});
        if (last.size() > 1) {
            log.warn("Called one() on StreamResult with size greater than 1. Discarding all other.");
        }

        return !last.isEmpty()
               ? Optional.of(last.getFirst())
                       .map(res -> Result.from(res.r(), res.e()))
                       .map(Result::one)
                       .orElse(null)
               : Result.<E, R>empty().one();
    }


    @Override
    public StreamResult<R, E> swap(Stream<Result<R, E>> toCache) {
        List<Result<R, E>> thisList = toCache.toList();
        swap(thisList);

        return new StreamResult<>(thisList.stream());
    }

    public Result<R, E> hasAnyOr(Supplier<Result<R, E>> s) {
        if (!this.r.hasAnyResult(this))  {
            return s.get();
        }

        return this;
    }

    @Override
    public Result<R, E> last(Consumer<Result<R, E>> last) {
        var lastRef = this.r.cacheResultsIfNotCachedWithList(last);
        return lastRef.getLast();
    }

    public void swap(List<Result<R, E>> toSwap) {
        this.r.swap(toSwap);
    }

    public <U> StreamResult<U, E> map(Function<R, U> mapper) {
        return new StreamResult<>(this.r.map(res -> res.map(mapper)));
    }

    public StreamResult<R, E> peek(Consumer<R> mapper) {
        return new StreamResult<>(this.r.peek(res -> res.peek(mapper)));
    }

    public <U> StreamResult<U, E> map(Function<R, U> mapper, Supplier<E> err) {
        return new StreamResult<>(r.map(t -> this.map(mapper).hasAnyOr(() -> Result.err(err.get()))));
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

    public <E1> StreamResult<R, E1> mapError(Function<E, E1> mapper, E1 defaultValue) {
        return mapError(mapper);
    }

    public <U> StreamResult<U, E> flatMap(Function<R, Result<U, E>> mapper) {
        return new StreamResult<>(
                this.r.map(res -> res.flatMap(mapper)));
    }

    public R firstResOrElse(R or) {
        if (this.r.hasAnyResult(this)) {
            return retrieveFirstCachedResIfExists();
        }

        return or;
    }

    public R hasFirstErrOrElseGet(Function<Err<E>, R> or) {
        if (this.r.isCompletelyEmpty(this)) {
            return retrieveFirstCachedResIfExists();
        }

        return or.apply(this.r.getErr());
    }

    private R retrieveFirstCachedResIfExists() {
        Ok<R> r = this.r.getOk();
        return Optional.ofNullable(r)
                .flatMap(ResultTy::firstOptional)
                .orElseThrow(RuntimeException::new);
    }

    public <U> StreamResult<U, E> flatMap(Function<R, Result<U, E>> mapper, Supplier<E> errorSupplier) {
        return new StreamResult<>(this.r.flatMap(m -> m.toStream().map(mapper)))
                .firstErrOr(() -> Err.err(errorSupplier.get()));
    }

    @Override
    public StreamResult<R, E> firstErrOr(Supplier<Err<E>> s) {
        if (this.r.hasAnyError(this))
            return new StreamResult<>(Stream.concat(this.stream(), Stream.of(Result.err(s.get()))));

        return this;
    }

    public <U> StreamResult<U, E> flatMapResult(Function<R, Result<U, E>> mapper) {
        return flatMap(mapper);
    }

    public boolean isOk() {
        return r.hasAnyResult(this);
    }

    public Optional<R> oneOptional() {
        return r.flatMap(Result::toStream)
                .findAny();
    }

    public Optional<R> optional() {
        return oneOptional();
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

    public Stream<Either<Ok<R>, Err<E>>> toEntryStream() {
        List<Either<Ok<R>, Err<E>>> l = this.r
                .map(t -> Either.<Ok<R>, Err<E>>from(t.r(), t.e()))
                .collect(Collectors.toCollection(ArrayList::new));

        return l.stream();
    }


    @Override
    public Stream<R> toStream() {
        return this.r.flatMap(r -> r.r().stream());
    }

    @Override
    public Stream<R> detachedStream() {
        var swapped = swap(this.r);
        return swapped.stream()
                .flatMap(Result::detachedStream);
    }


    public void forEach(Consumer<? super Result<R, E>> toDo) {
        this.r.forEach(toDo);
    }

}
