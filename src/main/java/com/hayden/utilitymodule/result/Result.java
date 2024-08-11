package com.hayden.utilitymodule.result;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.*;
import lombok.experimental.Delegate;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public record Result<T, E>(ResultInner<T> r, Error<E> e) {

    public Error<E> error() {
        return e;
    }

    public static <R, E> com.hayden.utilitymodule.result.Result<R, E> ok(R r) {
        return new com.hayden.utilitymodule.result.Result<>(new ResultInner<>(r), Error.empty());
    }

    public static <R, E> com.hayden.utilitymodule.result.Result<R, E> ok(ResultInner<R> r) {
        return new com.hayden.utilitymodule.result.Result<>(r, Error.empty());
    }

    public static <R, E> Result<R, E> err(E r) {
        return new com.hayden.utilitymodule.result.Result<>(ResultInner.empty(), Error.err(r));
    }

    public static <R, E> com.hayden.utilitymodule.result.Result<R, E> err(Error<E> r) {
        return new com.hayden.utilitymodule.result.Result<>(ResultInner.empty(), r);
    }

    public static <R, E> com.hayden.utilitymodule.result.Result<R, E> from(R r, E e) {
        return new com.hayden.utilitymodule.result.Result<>(ResultInner.ok(r), Error.err(e));
    }

    public static <R, E> com.hayden.utilitymodule.result.Result<R, E> from(ResultInner<R> r, Error<E> e) {
        return new com.hayden.utilitymodule.result.Result<>(r, e);
    }

    public boolean isError() {
        return e.isPresent();
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
            super(Optional.of(r));
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

    public <U> com.hayden.utilitymodule.result.Result<U, E> map(Function<T, U> mapper) {
        return r.map(t -> com.hayden.utilitymodule.result.Result.<U, E>ok(mapper.apply(t)))
                .orElse(null);
    }


    public <U, V> com.hayden.utilitymodule.result.Result<U, V> map(Function<T, U> mapper, Supplier<V> err) {
        return r.<com.hayden.utilitymodule.result.Result<U, V>>map(t -> com.hayden.utilitymodule.result.Result.ok(mapper.apply(t)))
                .orElse(com.hayden.utilitymodule.result.Result.err(err.get()));
    }

    public <E1> com.hayden.utilitymodule.result.Result<T, E1> mapError(Function<E, E1> mapper) {
        if (this.e.isPresent()) {
            return com.hayden.utilitymodule.result.Result.err(this.e.mapErr(mapper));
        }

        return this.castError();
    }

    public com.hayden.utilitymodule.result.Result<T, E> orErrorRes(Supplier<com.hayden.utilitymodule.result.Result<T, E>> s) {
        if (e.isPresent())
            return this;
        return s.get();
    }

    public com.hayden.utilitymodule.result.Result<T, E> orError(Supplier<Error<E>> s) {
        if (e.isPresent())
            return this;

        return com.hayden.utilitymodule.result.Result.err(s.get());
    }

    public com.hayden.utilitymodule.result.Result<T, E> or(Supplier<com.hayden.utilitymodule.result.Result<T, E>> s) {
        if (this.r.isPresent())
            return this;
        return s.get();
    }

    public com.hayden.utilitymodule.result.Result<T, E> filterResult(Function<T, Boolean> b) {
        if (r.isPresent() && b.apply(r.get())) {
            return this;
        }

        return com.hayden.utilitymodule.result.Result.empty();
    }

    private static <E, T> com.hayden.utilitymodule.result.Result<T, E> empty() {
        return new com.hayden.utilitymodule.result.Result<>(ResultInner.empty(), Error.empty());
    }

    public <E1> com.hayden.utilitymodule.result.Result<T, E1> mapError(Function<E, E1> mapper, E1 defaultValue) {
        var err = this.mapError(mapper);
        if (err.isNotEmpty()) {
            return com.hayden.utilitymodule.result.Result.from(r.orElse(null), defaultValue);
        } else {
            return mapError(mapper).orError(() -> Error.err(defaultValue));
        }
    }

    public <U> com.hayden.utilitymodule.result.Result<U, E> flatMap(Function<T, com.hayden.utilitymodule.result.Result<U, E>> mapper) {
        if (this.r.isPresent())
            return mapper.apply(this.r.get());

        return this.cast();
    }

    public com.hayden.utilitymodule.result.Result<T, E> orElse(com.hayden.utilitymodule.result.Result<T, E> or) {
        if (this.r.isPresent())
            return this;

        return or;
    }

    public com.hayden.utilitymodule.result.Result<T, E> orElseErr(com.hayden.utilitymodule.result.Result<T, E> or) {
        if (this.e.isPresent())
            return this;

        return or;
    }

    public <U> com.hayden.utilitymodule.result.Result<U, E> flatMap(Function<T, com.hayden.utilitymodule.result.Result<U, E>> mapper, Supplier<E> errorSupplier) {
        return r.map(mapper)
                .filter(r -> r.r.isPresent())
                .orElse(com.hayden.utilitymodule.result.Result.err(errorSupplier.get()));
    }

    public <NE> com.hayden.utilitymodule.result.Result<T, NE> flatMapError(Function<E, Error<NE>> mapper) {
        if (this.e.isEmpty()) {
            return this.castError();
        } else {
            var mapped =  this.e.flatMapErr(mapper)
                    .orErr(Error::empty);
            return com.hayden.utilitymodule.result.Result.from(this.r, mapped);
        }
    }

    public com.hayden.utilitymodule.result.Result<T, E> orElseGetErr(Supplier<com.hayden.utilitymodule.result.Result<T, E>> s) {
        if (this.e.isPresent())
            return this;

        var retrieved = s.get();
        retrieved.r.t = this.r.t;
        return retrieved;
    }

    public com.hayden.utilitymodule.result.Result<T, E> orElseGetRes(Supplier<com.hayden.utilitymodule.result.Result<T, E>> s) {
        if (this.r.isPresent())
            return this;

        var retrieved = s.get();
        retrieved.e.t = this.e.t;
        return retrieved;
    }

    public com.hayden.utilitymodule.result.Result<T, E> filterErr(Function<Error<E>, Boolean> ty) {
        if(this.e.isPresent() && ty.apply(this.e)) {
            return this;
        }

        return com.hayden.utilitymodule.result.Result.from(this.r, Error.empty());
    }

    public com.hayden.utilitymodule.result.Result<T, E> filterRes(Function<ResultInner<T>, Boolean> ty) {
        if(this.r.isPresent() && ty.apply(this.r)) {
            return this;
        }

        return com.hayden.utilitymodule.result.Result.from(ResultInner.empty(), this.e);
    }

    public <U> com.hayden.utilitymodule.result.Result<U, E> flatMapRes(Function<T, ResultInner<U>> mapper) {
        if (this.r.isEmpty()) {
            return this.cast();
        } else {
            var mapped =  this.r.flatMapResult(mapper);
            return com.hayden.utilitymodule.result.Result.from(mapped, this.e);
        }
    }

    public <U> com.hayden.utilitymodule.result.Result<U, E> flatMapResult(Function<T, Result<U, E>> mapper) {
        if (this.r.isEmpty()) {
            return this.cast();
        } else {
            var mapped =  mapper.apply(this.r.get());
            mapped.e.t = this.e.t;
            return mapped;
        }
    }

    public <E1> com.hayden.utilitymodule.result.Result<T, E1> flatMapErr(Function<E, com.hayden.utilitymodule.result.Result<T, E1>> mapper) {
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

    private boolean isEmpty() {
        return r.isEmpty() && e.isEmpty();
    }

    private boolean isErr() {
        return e.isEmpty();
    }

    private boolean isOk() {
        return r.isPresent();
    }

    public com.hayden.utilitymodule.result.Result<T, E> doOnNext(Consumer<T> mapper) {
        this.r.ifPresent(mapper);
        return this;
    }

    public <U, NE> Stream<com.hayden.utilitymodule.result.Result<U, NE>> flatMapStreamResult(Function<T, Stream<com.hayden.utilitymodule.result.Result<U, NE>>> mapper) {
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

    public <U> com.hayden.utilitymodule.result.Result<U, E> cast() {
        return this.flatMapRes(c -> ResultInner.ok(c).cast());
    }

    public <V> com.hayden.utilitymodule.result.Result<T, V> castError() {
        return this.<V>flatMapError(c -> Error.err(c).cast());
    }

    public <R extends com.hayden.utilitymodule.result.Result<U, V>, U, V> R cast(TypeReference<R> refDelegate) {
        return (R) this.map(c -> (U) c);
    }


}
