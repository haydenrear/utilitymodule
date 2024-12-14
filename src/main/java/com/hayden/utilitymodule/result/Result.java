package com.hayden.utilitymodule.result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hayden.utilitymodule.Either;
import com.hayden.utilitymodule.result.agg.AggregateError;
import com.hayden.utilitymodule.result.agg.AggregateParamError;
import com.hayden.utilitymodule.result.async.FluxResult;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.map.StreamResultCollector;
import com.hayden.utilitymodule.result.agg.Responses;
import com.hayden.utilitymodule.result.res_many.IManyResultTy;
import com.hayden.utilitymodule.result.res_ty.ClosableResult;
import com.hayden.utilitymodule.result.res_ty.IResultTy;
import com.hayden.utilitymodule.result.res_many.StreamResult;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.Callable;
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
 * @param r
 * @param e
 * @param <T>
 * @param <E>
 */
@Slf4j
public record Result<T, E>(Responses.Ok<T> r, Err<E> e) {

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <T, E> Result<T, E> fromOpt(Optional<T> stringStringEntry, E gitAggregateError) {
        return from(new Responses.Ok<>(stringStringEntry), Err.err(gitAggregateError));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <T, E> Result<T, E> fromOptOrErr(Optional<T> stringStringEntry, E gitAggregateError) {
        return stringStringEntry.isPresent()
                ? from(new Responses.Ok<>(stringStringEntry), Err.empty())
                : from(Responses.Ok.empty(), Err.err(gitAggregateError));
    }

    public static <T extends AutoCloseable, E> Result<T, E> tryFrom(T o, Callable<Void> onClose) {
        try {
            log.debug("Doing try from with result ty. Means there was a closable opened. Will log debug on close.");
            return Result.ok(new ClosableResult<>(Optional.ofNullable(o), onClose));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static <T extends AutoCloseable, E> Result<T, E> tryFrom(Callable<T> o) {
        try {
            return Result.ok(o.call());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static <T, E> Result<T, E> stream(Mono<T> o) {
        try {
            return Result.ok(o);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static <T, E> Result<T, E> stream(Stream<T> o) {
        try {
            return Result.ok(o);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public Result<List<T>, List<E>> collectList() {
        return toResultLists();
    }

    public Err<E> error() {
        return e;
    }

    public Stream<T> stream() {
        return r.stream();
    }

    public Stream<E> streamErr() {
        return e.stream();
    }

    public static <R, E> Result<R, E> ok(R r) {
        return new Result<>(new Responses.Ok<>(r), Err.empty());
    }

    public static <R, E> Result<R, E> resOk(R r) {
        return new Result<>(new Responses.Ok<>(r), Err.empty());
    }

    public static <R, E> Result<R, E> ok(IResultTy<R> r) {
        return new Result<>(new Responses.Ok<>(r), Err.empty());
    }

    public static <R, E> Result<R, E> from(Stream<Result<R, E>> r, Err<E> err) {
        var resp = r.map(res -> {
            err.extractError(res);
            return res.r.t;
        });

        return new Result<>(new Responses.Ok<R>(StreamResult.of(resp)), err);
    }

    public static <R, E> Result<R, E> from(Stream<Result<R, E>> r) {
        Err<E> eErr = Err.emptyStream();
        var resp = r.map(res -> {
                    eErr.extractError(res);
                    return res.r.t;
                });

        return new Result<>(new Responses.Ok<R>(StreamResult.of(resp)), eErr);
    }

    public static <R extends AutoCloseable, E> Result<R, E> ok(ClosableResult<R> r, Callable<Void> onClose) {
        return new Result<>(new Responses.Ok<>(r), Err.empty());
    }

    public static <R, E> Result<R, E> ok(Mono<R> r) {
        return new Result<>(new Responses.Ok<>(r), Err.empty());
    }

    public static <R, E> Result<R, E> ok(Flux<R> r) {
        return new Result<>(new Responses.Ok<>(r), Err.empty());
    }

    public static <R, E> Result<R, E> ok(Stream<R> r) {
        return new Result<>(new Responses.Ok<>(r), Err.empty());
    }

    public static <R, E> Result<R, E> ok(Responses.Ok<R> r) {
        return new Result<>(r, Err.empty());
    }

    public static <R, E> Result<R, E> err(E r) {
        return new Result<>(Responses.Ok.empty(), Err.err(r));
    }

    public static <R, E> Result<R, E> err(Err<E> r) {
        return new Result<>(Responses.Ok.empty(), r);
    }

    public static <R, E> Result<R, E> from(R r, E e) {
        return new Result<>(Responses.Ok.ok(r), Err.err(e));
    }

    public static <R, E> Result<R, E> from(IResultTy<R> r, IResultTy<E> e) {
        return new Result<>(Responses.Ok.ok(r), Err.err(e));
    }

    public static <R, E> Result<R, E> from(Responses.Ok<R> r, Err<E> e) {
        return new Result<>(r, e);
    }

    public static <T extends Responses.AggregateResponse, E extends AggregateError> @Nullable Result<T, E> all(Collection<Result<T, E>> mapper, Result<T, E> finalResult) {
        return Optional.ofNullable(all(mapper))
                .flatMap(r -> Optional.ofNullable(r.e))
                .map(e -> {
                    finalResult.e.t
                            .flatMap(f -> IResultTy.toRes(f.errors()))
                            .ifPresent(f -> e.t.ifPresent(toAdd -> f.addAll(toAdd.errors())));
                    return finalResult;
                })
                .orElse(finalResult);
    }

    public static void logThreadStarvation() {
        log.warn("Calling blocking operation on subscription. This could lead to thread  starvation.");
    }

    public static void logClosableMaybeNotClosed() {
        log.warn("Called a function on closable that maybe means close was not closed.");
    }

    public static void logAsync() {
        log.warn("Calling async operation on Mono subscription. This could lead to operations happening before or after expected.");
    }

    @SafeVarargs
    public static <T extends Responses.AggregateResponse, E extends AggregateParamError> @Nullable Result<T, E> all(Result<T, E> ... mapper) {
        return all(Arrays.asList(mapper));
    }

    public static <T extends Responses.AggregateResponse, E extends AggregateParamError> @Nullable Result<T, E> all(Collection<Result<T, E>> mapper) {
        Result<T, E> result = null;
        for (Result<T, E> nextResultToAdd : mapper) {
            if (result == null)
                result = nextResultToAdd;
            if (nextResultToAdd.r.isPresent()) {
                // can't add a ref, only update current, because it's immutable.
                if (result.r.isEmpty()) {
                    var temp = result;
                    result = nextResultToAdd;
                    nextResultToAdd = temp;
                } else {
                    result.r.get().addAgg(nextResultToAdd.r.get());
                }
            }

            result = addErrors(nextResultToAdd, result);
        }

        return result;
    }

    private static <T extends Responses.AggregateResponse, E extends AggregateParamError> Result<T, E> addErrors(Result<T, E> r, Result<T, E> result) {
        if (r.e.isPresent()) {
            if (result.e.isEmpty()) {
                result.e.setT(r.e.t);
            } else {
                r.filterErr(Predicate.not(toFilter -> result.hasErr(re -> re == toFilter)))
                        .streamErr()
                        .forEach(result.error().get()::addError);
            }
        }
        return result;
    }


    public interface Monadic<R> {

        R get();

        Stream<R> stream();
        Optional<R> firstOptional();
        IResultTy<R> filter(Predicate<R> p);

        <T> IResultTy<T> flatMap(Function<R, IResultTy<T>> toMap);
        <T> IResultTy<T> map(Function<R, T> toMap);

        R orElse(R r);

        R orElseGet(Supplier<R> r);

        void ifPresent(Consumer<? super R> consumer);

        boolean isEmpty();

    }

    public void ifPresent(Consumer<T> t) {
        this.r.ifPresent(t);
    }

    public boolean isError() {
        return r.isEmpty();
    }

    public boolean hasError() {
        return e.isPresent();
    }

    public T orElseGet(Supplier<T> o) {
        if (this.r.isPresent())
            return this.r.get();

        return o.get();
    }

    public T get() {
        return this.r.get();
    }

    public boolean isPresent() {
        return r.isPresent();
    }

    public boolean hasErr(Predicate<E> e) {
        return switch(this.e.t)  {
            case IManyResultTy<E> many ->
                    many.has(e);
            default -> this.e.filterErr(e)
                    .isPresent();
        };
    }

    public <U> Result<U, E> map(Function<T, U> mapper) {
        return switch(this.r.t) {
            case IManyResultTy<T> sr ->
                    Result.from(sr.map(mapper), this.e.t);
            default -> {
                if (this.r.isPresent()) {
                    var toRet = mapper.apply(this.r.get());
                    yield Result.from(Responses.Ok.ok(toRet), this.e);
                }

                yield this.cast();
            }
        };
    };

    public Result<T, E> peek(Consumer<T> mapper) {
        return Result.from(Responses.Ok.ok(this.r.peek(mapper)), this.e);
    }


    public <U> Result<U, E> map(Function<T, U> mapper, Supplier<E> err) {
        return r.<Result<U, E>>map(t -> Result.from(Responses.Ok.ok(mapper.apply(t)), this.e))
                .orElse(Result.err(err.get()));
    }

    public <E1> Result<T, E1> mapError(Function<E, E1> mapper) {
        return switch(this.e.t) {
            case IManyResultTy<E> st ->
                    Result.from(this.r, Err.err(st.map(mapper)));
            default -> {
                if (this.e.isPresent()) {
                    Err<E1> r1 = this.e.mapErr(mapper);
                    yield Result.from(this.r, r1);
                }

                yield this.castError();
            }
        };
    }

    public Result<T, E> orErrorRes(Supplier<Result<T, E>> s) {
        if (e.isPresent())
            return this;
        return s.get();
    }

    public Result<T, E> orError(Supplier<Err<E>> s) {
        if (e.isPresent())
            return this;

        Result<T, E> err = Result.err(s.get());
        err.r.t = this.r.t;
        return err;
    }

    public Result<T, E> or(Supplier<Result<T, E>> s) {
        if (this.r.isPresent())
            return this;
        return s.get();
    }

    public Result<T, E> filterErr(Predicate<E> b) {
        return switch(this.e.t) {
            case IManyResultTy<E> st -> {
                var filtered = st.filter(b);

                yield Result.from(this.r, Err.err(filtered));
            }
            default -> {
                if (e.isPresent() && b.test(e.get())) {
                    yield this;
                }

                yield Result.from(this.r, Err.empty());
            }
        };
    }

    public Result<T, E> filterResult(Predicate<T> b) {
        return switch(this.r.t) {
            case IManyResultTy<T> st -> {
                var filtered = st.filter(b);

                yield Result.from(Responses.Ok.ok(filtered), this.e);
            }
            default -> {
                if (r.isPresent() && b.test(r.get())) {
                    yield this;
                }

                yield Result.<T, E>err(this.e);
            }
        };
    }

    public void close() {
        this.r.flatMap(t -> t instanceof AutoCloseable a ? IResultTy.toRes(a) : IResultTy.empty())
                .ifPresent(t -> {
                    try {
                        t.close();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
    }

    public static <E, T> Result<T, E> empty() {
        return new Result<>(Responses.Ok.empty(), Err.empty());
    }

    public <E1> Result<T, E1> mapError(Function<E, E1> mapper, E1 defaultValue) {
        return switch(this.e.t) {
            case IManyResultTy<E> st ->
                    Result.from(this.r, Err.err(st.map(mapper)));
            default -> {
                var err = this.mapError(mapper);
                if (err.e().isEmpty()) {
                    yield Result.from(r, Err.err(defaultValue));
                } else {
                    yield mapError(mapper).orError(() -> Err.err(defaultValue));
                }
            }
        };
    }

    public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
        return switch(this.r.t) {
            case IManyResultTy<T> sr -> {
                Err<E> e = Err.err(this.e.t);
                IManyResultTy<U> f = sr.flatMap(s -> {
                    var flattened = mapper.apply(s);
                    e.extractError(flattened);
                    return flattened.r.t;
                });

                yield Result.from(Responses.Ok.ok(f), e);
            }
            default -> {
                if (this.r.isPresent())
                    yield mapper.apply(this.r.get());

                yield this.cast();
            }
        };
    }

    public T orElseRes(T or) {
        if (this.r.isPresent())
            return this.r.get();

        return or;
    }

    public T orElseErrRes(Function<Err<E>, T> or) {
        if (this.r.isPresent())
            return this.r.get();

        return or.apply(this.e);
    }

    public Result<T, E> orElseErr(Result<T, E> or) {
        if (this.e.isPresent())
            return this;

        or.r.t = this.r.t;

        return or;
    }

    public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper, Supplier<E> errorSupplier) {
        return r.map(mapper)
                .filter(r -> r.r.isPresent())
                .orElse(Result.err(errorSupplier.get()));
    }

    public <NE> Result<T, NE> flatMapError(Function<E, Err<NE>> mapper) {
        if (this.e.isEmpty()) {
            return this.castError();
        } else {
            var mapped =  this.e
                    .flatMapErr(mapper)
                    .orErr(Err::empty);
            return Result.from(this.r, mapped);
        }
    }

    public <U> Result<U, E> flatMapResult(Function<T, Result<U, E>> mapper) {
        return switch(this.r.t) {
            case IManyResultTy<T> sr -> {
                var srt = sr.map(mapper);
                yield Result.from(srt.stream(), this.e);
            }
            default -> {
                if (this.r.isEmpty()) {
                    yield this.cast();
                } else {

                    var mapped =  mapper.apply(this.r.get());
                    mapped.e.t = this.e.t;
                    yield mapped;
                }
            }
        };
    }

    public boolean isOk() {
        return r.isPresent();
    }

    public <U, NE> Stream<Result<U, NE>> flatMapStreamResult(Function<T, Stream<Result<U, NE>>> mapper) {
        var p = map(mapper);

        if (p.r.isPresent())
            return p.r.get();

        return Stream.empty();
    }

    public Optional<T> toOptional() {
        return r.t.firstOptional();
    }

    public Optional<T> optional() {
        return r.t.firstOptional();
    }

    public <U> Result<U, E> cast() {
        return Result.from(this.r.cast(), this.e);
    }

    public <V> Result<T, V> castError() {
        return Result.from(this.r, this.e.cast());
    }

    public <R extends Result<U, V>, U, V> R cast(TypeReference<R> refDelegate) {
        return (R) this.map(c -> (U) c);
    }

    public Result<T, E> doOnError(Consumer<E> e) {
        this.e.ifPresent(e);
        return this;
    }

    public void doOnEach(Consumer<T> e) {
        this.r.forEach(e);
    }

    /**
     * If underlying is stream, then collect to list, otherwise then
     * will be lists of 1.
     * @return
     */
    public Result<List<T>, List<E>> toResultLists() {
        return this.toEntryStream().collect(new StreamResultCollector<>());
    }

    public Stream<Either<Responses.Ok<T>, Err<E>>> toEntryStream() {
        List<Either<Responses.Ok<T>, Err<E>>> l = this.r.stream()
                .map(t -> Either.<Responses.Ok<T>, Err<E>>from(Responses.Ok.ok(t), null))
                .collect(Collectors.toCollection(ArrayList::new));
        List<Either<Responses.Ok<T>, Err<E>>> r = this.e.stream()
                .map(t -> Either.<Responses.Ok<T>, Err<E>>from(null, Err.err(t)))
                .collect(Collectors.toCollection(() -> l));

        return r.stream();
    }

}
