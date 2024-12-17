package com.hayden.utilitymodule.result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hayden.utilitymodule.Either;
import com.hayden.utilitymodule.result.agg.AggregateError;
import com.hayden.utilitymodule.result.agg.AggregateParamError;
import com.hayden.utilitymodule.result.agg.Responses;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.map.StreamResultCollector;
import com.hayden.utilitymodule.result.ok.Ok;
import com.hayden.utilitymodule.result.res_many.ListResultItem;
import com.hayden.utilitymodule.result.res_support.one.OneOkErrRes;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamResult;
import com.hayden.utilitymodule.result.res_ty.ClosableResult;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
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

public interface Result<T, E> {

    Logger log = LoggerFactory.getLogger(Result.class);

    interface Monadic<R> {

        R get();

        Stream<R> stream();
        Optional<R> firstOptional();
        IResultItem<R> filter(Predicate<R> p);

        <T> IResultItem<T> flatMap(Function<R, IResultItem<T>> toMap);
        <T> IResultItem<T> map(Function<R, T> toMap);

        R orElse(R r);

        R orElseGet(Supplier<R> r);

        void ifPresent(Consumer<? super R> consumer);

        boolean isEmpty();

    }


    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static <T, E> Result<T, E> fromOpt(Optional<T> stringStringEntry, E gitAggregateError) {
        return from(new Ok<>(stringStringEntry), Err.err(gitAggregateError));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static <T, E> Result<T, E> fromOptOrErr(Optional<T> stringStringEntry, E gitAggregateError) {
        return stringStringEntry.isPresent()
               ? from(new Ok<>(stringStringEntry), Err.empty())
               : from(Ok.empty(), Err.err(gitAggregateError));
    }

    static <T extends AutoCloseable, E> Result<T, E> tryFrom(T o, Callable<Void> onClose) {
        try {
            log.debug("Doing try from with result ty. Means there was a closable opened. Will log debug on close.");
            return Result.ok(new ClosableResult<>(Optional.ofNullable(o), onClose));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static <T extends AutoCloseable, E> Result<T, E> tryFrom(Callable<T> o) {
        try {
            return Result.ok(o.call());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static <T, E> Result<T, E> stream(Mono<T> o) {
        try {
            return Result.ok(o);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static <T, E> Result<T, E> stream(Stream<T> o) {
        try {
            return Result.ok(o);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static <R, E> OneResult<R, E> ok(R r) {
        return new OneOkErrRes<>(new Ok<>(r), Err.empty());
    }

    static <R, E> OneResult<R, E> resOk(R r) {
        return new OneOkErrRes<>(new Ok<>(r), Err.empty());
    }

    static <R, E> Result<R, E> ok(IResultItem<R> r) {
        return new OneOkErrRes<>(new Ok<>(r), Err.empty());
    }

    static <R, E> Result<R, E> from(Stream<Result<R, E>> r) {
        return new StreamResult<>(r);
    }

    static <R, E> Result<R, E> ok(Mono<R> r) {
        return new OneOkErrRes<>(new Ok<>(r), Err.empty());
    }

    static <R, E> Result<R, E> ok(Flux<R> r) {
        return new OneOkErrRes<>(new Ok<>(r), Err.empty());
    }

    static <R, E> Result<R, E> ok(Stream<R> r) {
        return new OneOkErrRes<>(new Ok<>(r), Err.empty());
    }

    static <R, E> Result<R, E> ok(Ok<R> r) {
        return new OneOkErrRes<>(r, Err.empty());
    }

    static <R, E> OneResult<R, E> err(E r) {
        return new OneOkErrRes<>(Ok.empty(), Err.err(r));
    }

    static <R, E> Result<R, E> err(Err<E> r) {
        return new OneOkErrRes<>(Ok.empty(), r);
    }

    static <R, E> Result<R, E> from(R r, E e) {
        return new OneOkErrRes<>(Ok.ok(r), Err.err(e));
    }

    static <R, E> Result<R, E> from(IResultItem<R> r, IResultItem<E> e) {
        return new OneOkErrRes<>(Ok.ok(r), Err.err(e));
    }

    static <R, E> Result<R, E> from(Ok<R> r, Err<E> e) {
        return new OneOkErrRes<>(r, e);
    }

    static <T extends Responses.AggregateResponse, E extends AggregateError> @Nullable Result<T, E> all(Collection<Result<T, E>> mapper, Result<T, E> finalResult) {
        return Optional.ofNullable(all(mapper))
                .flatMap(r -> Optional.ofNullable(r.e()))
                .map(e -> {
                    finalResult.e()
                            .flatMap(f -> IResultItem.toRes(f.errors()))
                            .ifPresent(f -> e.ifPresent(toAdd -> f.addAll(toAdd.errors())));
                    return finalResult;
                })
                .orElse(finalResult);
    }

    static void logThreadStarvation() {
        log.warn("Calling blocking operation on subscription. This could lead to thread  starvation.");
    }

    static void logClosableMaybeNotClosed() {
        log.warn("Called a function on closable that maybe means close was not closed.");
    }

    static void logAsync() {
        log.warn("Calling async operation on Mono subscription. This could lead to operations happening before or after expected.");
    }

    @SafeVarargs
    static <T extends Responses.AggregateResponse, E extends AggregateParamError> @Nullable Result<T, E> all(Result<T, E> ... mapper) {
        return all(Arrays.asList(mapper));
    }

    static <T extends Responses.AggregateResponse, E extends AggregateParamError> @Nullable Result<T, E> all(Collection<Result<T, E>> mapper) {
        Result<T, E> result = null;
        for (Result<T, E> nextResultToAdd : mapper) {
            if (result == null)
                result = nextResultToAdd;
            if (nextResultToAdd.r().isPresent()) {
                // can't add a ref, only update current, because it's immutable.
                if (result.r().isEmpty()) {
                    var temp = result;
                    result = nextResultToAdd;
                    nextResultToAdd = temp;
                } else {
                    result.r().get().addAgg(nextResultToAdd.r().get());
                }
            }

            result = addErrors(nextResultToAdd, result);
        }

        return result;
    }

    static <T extends Responses.AggregateResponse, E extends AggregateParamError> Result<T, E> addErrors(Result<T, E> r, Result<T, E> result) {
        Assert.isTrue(!(result.e().isMany()), "Result was of wrong type.");
        if (r.e().isPresent()) {
            if (result.e().isEmpty()) {
                result.e().addError(r.e());
            } else {
                var errs = r.filterErr(Predicate.not(toFilter -> result.hasErr(re -> re == toFilter)))
                        .streamErr()
                        .toList();

                errs.forEach(result.e().get()::addError);
//                if (result.e().t instanceof IManyResultTy<E> res) {
//                    res!!!!!!!!!!
//                }
            }
        }
        return result;
    }

    static <E, T> Result<T, E> empty() {
        return new OneOkErrRes<>(Ok.empty(), Err.empty());
    }

    Result<T, E> filterErr(Predicate<E> b);

    Result<T, E> filterResult(Predicate<T> b);

    void close();

    <E1> Result<T, E1> mapError(Function<E, E1> mapper, E1 defaultValue);

    <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper);

    /**
     * //TODO: This is bad, replace
     * This is bad
     * @return
     */
    Err<E> e();

    /**
     * //TODO: This is bad, replace
     * @return
     */
    Ok<T> r();

    Stream<T> toStream();

    Stream<T> detachedStream();

    ManyResult<T, E> many();

    OneResult<T, E> one();

    default StreamResult<T, E> streamResult() {
        if (this instanceof StreamResult s) {
            return s;
        }

        else return new StreamResult<>(Stream.of(Result.ok(this.r()), Result.err(this.e())));
    }

    default Result<List<T>, List<E>> collectList() {
        return toResultLists();
    }

    default Stream<E> streamErr() {
        return e().stream();
    }


    default void ifPresent(Consumer<T> t) {
        this.r().ifPresent(t);
    }

    default boolean isError() {
        return r().isEmpty();
    }

    default boolean hasErr(Predicate<E> e) {
        if (this.e().isMany())
            return this.e().many().has(e);
        else {
            return this.e().filterErr(e)
                    .isPresent();
        }
    }

    default <U> Result<U, E> map(Function<T, U> mapper) {
        if (this.r().isMany())
            return Result.from(Ok.ok(this.r().many().map(mapper)), this.e());
        else {
            if (this.r().isPresent()) {
                var toRet = mapper.apply(this.r().get());
                return Result.from(Ok.ok(toRet), this.e());
            }

            return this.cast();
        }
    };

    default Result<T, E> peek(Consumer<T> mapper) {
        return Result.from(Ok.ok(this.r().peek(mapper)), this.e());
    }


    default <U> Result<U, E> map(Function<T, U> mapper, Supplier<E> err) {
        return r().<Result<U, E>>map(t -> Result.from(Ok.ok(mapper.apply(t)), this.e()))
                .orElse(Result.err(err.get()));
    }

    default <E1> Result<T, E1> mapError(Function<E, E1> mapper) {
        if (this.e().isMany()) {
            return Result.from(this.r(), Err.err(new ListResultItem<>(this.e().stream().map(mapper).toList())));
        } else {

            if (this.e().isPresent()) {
                Err<E1> r1 = this.e().mapErr(mapper);
                return Result.from(this.r(), r1);
            }

            return this.castError();
        }
    }

    default <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper, Supplier<E> errorSupplier) {
        return r().map(mapper)
                .filter(r -> r.r().isPresent())
                .orElse(Result.err(errorSupplier.get()));
    }

    default <U> Result<U,E> flatMapResult(Function<T, Result<U, E>> mapper) {
        if (this.r().isMany()) {
            var srt = this.r().many().map(mapper);
            return Result.from(Stream.concat(srt.stream(), Stream.of(Result.err(this.e()))));
        } else {
            if (this.r().isEmpty()) {
                return this.cast();
            } else {

                var mapped =  mapper.apply(this.r().get());
                mapped.e().addError(this.e());
                return mapped;
            }
        }
    }

    default boolean isOk() {
        return r().isPresent();
    }

    default <U, NE> Stream<Result<U, NE>> flatMapStreamResult(Function<T, Stream<Result<U, NE>>> mapper) {
        var p = map(mapper);

        if (p.r().isPresent())
            return p.r().get();

        return Stream.empty();
    }

    default <U> Result<U, E> cast() {
        return Result.from(this.r().cast(), this.e());
    }

    default <V> Result<T, V> castError() {
        return Result.from(this.r(), this.e().cast());
    }

    default <R extends Result<U, V>, U, V> R cast(TypeReference<R> refDelegate) {
        return (R) this.map(c -> (U) c);
    }

    default Result<T, E> doOnError(Consumer<E> e) {
        this.e().ifPresent(e);
        return this;
    }

    default void doOnEach(Consumer<T> e) {
        this.r().forEach(e);
    }

    /**
     * If underlying is stream, then collect to list, otherwise then
     * will be lists of 1.
     * @return
     */
    default Result<List<T>, List<E>> toResultLists() {
        return this.toEntryStream().collect(new StreamResultCollector<>());
    }

    default Stream<Either<Ok<T>, Err<E>>> toEntryStream() {
        List<Either<Ok<T>, Err<E>>> l = this.r().stream()
                .map(t -> Either.<Ok<T>, Err<E>>from(Ok.ok(t), null))
                .collect(Collectors.toCollection(ArrayList::new));
        List<Either<Ok<T>, Err<E>>> r = this.e().stream()
                .map(t -> Either.<Ok<T>, Err<E>>from(null, Err.err(t)))
                .collect(Collectors.toCollection(() -> l));

        return r.stream();
    }

}
