package com.hayden.utilitymodule.result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hayden.utilitymodule.result.error.AggregateError;
import com.hayden.utilitymodule.result.res.Responses;
import jakarta.annotation.Nullable;
import lombok.*;
import lombok.experimental.Delegate;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public record Result<T, E>(ResultInner<T> r, Error<E> e) {

    public static <T, E> Result<T, E> fromOpt(Optional<T> stringStringEntry, E gitAggregateError) {
        return from(new ResultInner<>(stringStringEntry), Error.err(gitAggregateError));
    }

    public Error<E> error() {
        return e;
    }

    public Stream<T> stream() {
        return r.stream();
    }

    public static <R, E> Result<R, E> ok(R r) {
        return new Result<>(new ResultInner<>(r), Error.empty());
    }

    public static <R, E> Result<R, E> ok(ResultInner<R> r) {
        return new Result<>(r, Error.empty());
    }

    public static <R, E> Result<R, E> err(E r) {
        return new Result<>(ResultInner.empty(), Error.err(r));
    }

    public static <R, E> Result<R, E> err(Error<E> r) {
        return new Result<>(ResultInner.empty(), r);
    }

    public static <R, E> Result<R, E> from(R r, E e) {
        return new Result<>(ResultInner.ok(r), Error.err(e));
    }

    public static <R, E> Result<R, E> from(ResultInner<R> r, Error<E> e) {
        return new Result<>(r, e);
    }

    public static <T extends Responses.AggregateResponse, E extends AggregateError> @Nullable Result<T, E> all(Collection<Result<T, E>> mapper, Result<T, E> finalResult) {
        return Optional.ofNullable(all(mapper))
                .flatMap(r -> Optional.ofNullable(r.e))
                .map(e -> {
                    finalResult.e.t
                            .flatMap(f -> Optional.ofNullable(f.errors()))
                            .ifPresent(f -> e.t.ifPresent(toAdd -> f.addAll(toAdd.errors())));
                    return finalResult;
                })
                .orElse(finalResult);
    }


    @SafeVarargs
    public static <T extends Responses.AggregateResponse, E extends AggregateError> @Nullable Result<T, E> all(Result<T, E> ... mapper) {
        return all(Arrays.asList(mapper));
    }

    public static <T extends Responses.AggregateResponse, E extends AggregateError> @Nullable Result<T, E> all(Collection<Result<T, E>> mapper) {
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
                    result.r.get().add(nextResultToAdd.r.get());
                }
            }

            result = addErrors(nextResultToAdd, result);
        }

        return result;
    }

    private static <T extends Responses.AggregateResponse, E extends AggregateError> Result<T, E> addErrors(Result<T, E> r, Result<T, E> result) {
        if (r.e.isPresent()) {
            if (result.e.isEmpty()) {
                result.e.setT(r.e.t);
            } else if (result.e.t.filter(e -> r.e.get() == e).isEmpty()) {
                result.error().get().addError(r.e.get());
            }
        }
        return result;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public abstract static class ResultTy<T> {

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        @Delegate
        protected Optional<T> t;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static final class ResultInner<R> extends ResultTy<R> {

        public ResultInner(R r) {
            super(Optional.ofNullable(r));
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        public ResultInner(Optional<R> r) {
            super(r);
        }

        public static <R> ResultInner<R> ok(R r) {
            return new ResultInner<>(r);
        }

        public static <R> ResultInner<R> empty() {
            return new ResultInner<>(Optional.empty());
        }

        public <S> ResultInner<S> mapResult(Function<R, S> toMap) {
            if (this.t.isPresent())
                return ResultInner.ok(toMap.apply(t.get()));

            return ResultInner.empty();
        }

        public <S> ResultInner<S> flatMapResult(Function<R, ResultInner<S>> toMap) {
            if (this.t.isPresent())
                return toMap.apply(t.get());

            return ResultInner.empty();
        }

        public ResultInner<R> filterResult(Function<R, Boolean> b) {
            if (this.t.isPresent() && b.apply(t.get())) {
                return this;
            }

            return ResultInner.empty();
        }

        public <U> ResultInner<U> cast() {
            if (t.isEmpty())
                return ResultInner.empty();
            try {
                return this.mapResult(s -> (U) s);
            } catch (ClassCastException c) {
                return ResultInner.empty();
            }
        }

        public R orElseRes(R orRes) {
            return this.t.orElse(orRes);
        }

        public R orElseGetRes(Supplier<R> orRes) {
            return this.t.orElseGet(orRes);
        }

        public ResultInner<R> orRes(Supplier<ResultInner<R>> orRes) {
            if (this.t.isPresent())
                return this;

            return orRes.get();
        }

    }

    @EqualsAndHashCode(callSuper = true)
    @RequiredArgsConstructor
    @Data
    public static final class Error<R> extends ResultTy<R> {

        public Error(R r) {
            super(Optional.of(r));
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        public Error(Optional<R> r) {
            super(r);
        }

        public static <R> Error<R> empty() {
            return new Error<>(Optional.empty());
        }

        public static <R> Error<R> err(R r) {
            return new Error<>(Optional.of(r));
        }

        public <S> Error<S> mapErr(Function<R, S> toMap) {
            if (this.t.isPresent())
                return Error.err(toMap.apply(t.get()));

            return Error.empty();
        }

        public <S> Error<S> flatMapErr(Function<R, Error<S>> toMap) {
            if (this.t.isPresent())
                return toMap.apply(t.get());

            return Error.empty();
        }

        public Error<R> filterErr(Function<R, Boolean> b) {
            if (this.t.isPresent() && b.apply(t.get())) {
                return this;
            }

            return Error.empty();
        }

        public <U> Error<U> cast() {
            if (t.isEmpty())
                return Error.empty();
            try {
                return this.mapErr(s -> (U) s);
            } catch (ClassCastException c) {
                return Error.empty();
            }
        }

        public R orElseErr(R orRes) {
            return this.t.orElse(orRes);
        }

        public R orElseGetErr(Supplier<R> orRes) {
            return this.t.orElseGet(orRes);
        }

        public Error<R> orErr(Supplier<Error<R>> orRes) {
            if (this.t.isPresent())
                return this;

            return orRes.get();
        }

    }

    public void ifPresent(Consumer<T> t) {
        this.r.ifPresent(t);
    }

    public boolean isError() {
        return r.isEmpty();
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

    public <U> Result<U, E> map(Function<T, U> mapper) {
        if (this.r.isPresent()) {
            var toRet = mapper.apply(this.r.get());
            return Result.from(Result.ResultInner.ok(toRet), this.e);
        }

        return this.cast();
    }


    public <U, V> Result<U, V> map(Function<T, U> mapper, Supplier<V> err) {
        return r.<Result<U, V>>map(t -> Result.ok(mapper.apply(t)))
                .orElse(Result.err(err.get()));
    }

    public <E1> Result<T, E1> mapError(Function<E, E1> mapper) {
        if (this.e.isPresent()) {
            Error<E1> r1 = this.e.mapErr(mapper);
            return Result.from(this.r, r1);
        }

        return this.castError();
    }

    public Result<T, E> orErrorRes(Supplier<Result<T, E>> s) {
        if (e.isPresent())
            return this;
        return s.get();
    }

    public Result<T, E> orError(Supplier<Error<E>> s) {
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

    public Result<T, E> filterResult(Function<T, Boolean> b) {
        if (r.isPresent() && b.apply(r.get())) {
            return this;
        }

        return Result.empty();
    }

    private static <E, T> Result<T, E> empty() {
        return new Result<>(ResultInner.empty(), Error.empty());
    }

    public <E1> Result<T, E1> mapError(Function<E, E1> mapper, E1 defaultValue) {
        var err = this.mapError(mapper);
        if (err.e().isEmpty()) {
            return Result.from(r, Error.err(defaultValue));
        } else {
            return mapError(mapper).orError(() -> Error.err(defaultValue));
        }
    }

    public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
        if (this.r.isPresent())
            return mapper.apply(this.r.get());

        return this.cast();
    }

    public T orElseRes(T or) {
        if (this.r.isPresent())
            return this.r.get();

        return or;
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

    public <NE> Result<T, NE> flatMapError(Function<E, Error<NE>> mapper) {
        if (this.e.isEmpty()) {
            return this.castError();
        } else {
            var mapped =  this.e
                    .flatMapErr(mapper)
                    .orErr(Error::empty);
            return Result.from(this.r, mapped);
        }
    }

    public Result<T, E> orElseGetErr(Supplier<Result<T, E>> s) {
        if (this.e.isPresent())
            return this;

        var retrieved = s.get();
        retrieved.r.t = this.r.t;
        return retrieved;
    }

    public Result<T, E> orElseGetRes(Supplier<Result<T, E>> s) {
        if (this.r.isPresent())
            return this;

        var retrieved = s.get();
        retrieved.e.t = this.e.t;
        return retrieved;
    }

    public Result<T, E> filterErr(Function<Error<E>, Boolean> ty) {
        if(this.e.isPresent() && ty.apply(this.e)) {
            return this;
        }

        return Result.from(this.r, Error.empty());
    }

    public Result<T, E> filterRes(Function<ResultInner<T>, Boolean> ty) {
        if(this.r.isPresent() && ty.apply(this.r)) {
            return this;
        }

        return Result.from(ResultInner.empty(), this.e);
    }

    public <U> Result<U, E> flatMapRes(Function<T, ResultInner<U>> mapper) {
        if (this.r.isEmpty()) {
            return this.cast();
        } else {
            var mapped =  this.r.flatMapResult(mapper);
            return Result.from(mapped, this.e);
        }
    }

    public <U> Result<U, E> flatMapResult(Function<T, Result<U, E>> mapper) {
        if (this.r.isEmpty()) {
            return this.cast();
        } else {
            var mapped =  mapper.apply(this.r.get());
            mapped.e.t = this.e.t;
            return mapped;
        }
    }

    public <U, E1> Result<U, E1> flatMapResultError(Function<T, Result<U, E1>> mapper) {
        if (this.r.isEmpty()) {
            return Result.from(ResultInner.empty(), this.e.cast());
        }
        return mapper.apply(this.r.get());
    }

    public <E1> Result<T, E1> flatMapErr(Function<E, Result<T, E1>> mapper) {
        if (this.e.isEmpty()) {
            return this.castError();
        } else {
            var mapped =  mapper.apply(this.e.get());
            mapped.r.t = this.r.t;
            return mapped;
        }
    }

    private boolean isNotEmpty() {
        return r.isPresent() || e.isPresent();
    }

    private boolean isErr() {
        return e.isEmpty();
    }

    public boolean isOk() {
        return r.isPresent();
    }

    public Result<T, E> doOnNext(Consumer<T> mapper) {
        this.r.ifPresent(mapper);
        return this;
    }

    public <U, NE> Stream<Result<U, NE>> flatMapStreamResult(Function<T, Stream<Result<U, NE>>> mapper) {
        var p = map(mapper);

        if (p.r.isPresent())
            return p.r.get();

        return Stream.empty();
    }

    public Optional<T> toOptional() {
        return r.t;
    }

    public Optional<T> optional() {
        return r.t;
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

}
