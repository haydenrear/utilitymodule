package com.hayden.utilitymodule.result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hayden.utilitymodule.result.error.AggregateError;
import com.hayden.utilitymodule.result.res.Responses;
import jakarta.annotation.Nullable;
import lombok.*;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public record Result<T, E>(Ok<T> r, Err<E> e) {

    public static <T, E> Result<T, E> fromOpt(Optional<T> stringStringEntry, E gitAggregateError) {
        return from(new Ok<>(stringStringEntry), Err.err(gitAggregateError));
    }

    public static <T extends AutoCloseable, E> Result<T, E> tryFrom(Callable<T> o) {
        try {
            return Result.ok(o.call());
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

    public Err<E> error() {
        return e;
    }

    public Stream<T> stream() {
        return r.stream();
    }

    public static <R, E> Result<R, E> ok(R r) {
        return new Result<>(new Ok<>(r), Err.empty());
    }

    public static <R, E> Result<R, E> ok(Stream<R> r) {
        return new Result<>(new Ok<>(r), Err.empty());
    }

    public static <R, E> Result<R, E> ok(Ok<R> r) {
        return new Result<>(r, Err.empty());
    }

    public static <R, E> Result<R, E> err(E r) {
        return new Result<>(Ok.empty(), Err.err(r));
    }

    public static <R, E> Result<R, E> err(Err<E> r) {
        return new Result<>(Ok.empty(), r);
    }

    public static <R, E> Result<R, E> from(R r, E e) {
        return new Result<>(Ok.ok(r), Err.err(e));
    }

    public static <R, E> Result<R, E> from(Ok<R> r, Err<E> e) {
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
    public abstract static class ResultTy<U> {

        @Delegate
        protected IResultTy<U> t;

        public void set(U u) {
            this.t = from(u);
        }

        public ResultTy(Stream<U> t) {
            this.t = new IResultTy.StreamResult<>(t);
        }

        public ResultTy(Optional<U> t) {
            if (t.isEmpty()) {
                this.t = new IResultTy.ResultTyResult<>(Optional.empty());
            } else {
                var to = t.get();

                if (to instanceof AutoCloseable a) {
                    this.t = (IResultTy<U>) new IResultTy.ClosableResult<>(Optional.of(a));
                } else {
                    this.t = new IResultTy.ResultTyResult<>(Optional.ofNullable(to));
                }

            }
        }

        public ResultTy(U t) {
            if (t instanceof AutoCloseable a) {
                this.t = (IResultTy<U>) new IResultTy.ClosableResult<>(Optional.of(a));
            } else {
                this.t = new IResultTy.ResultTyResult<>(Optional.ofNullable(t));
            }
        }

        static <V> ResultTy<V> ok(V v) {
            return Ok.ok(v);
        }

        static <V> ResultTy<V> stream(Stream<V> t) {
            return Ok.stream(t);
        }

        static <V extends AutoCloseable> ResultTy<V> tryFrom(V t) {
            return Ok.ok(t);
        }

        static <V> ResultTy<V> err(V v) {
            return Err.err(v);
        }

    }

    public interface Monadic<R> {

        R get();

        Stream<R> stream();
        Optional<R> optional();
        IResultTy<R> filter(Predicate<R> p);

        <T> IResultTy<T> flatMap(Function<R, IResultTy<T>> toMap);
        <T> IResultTy<T> map(Function<R, T> toMap);

        R orElse(R r);

        R orElseGet(Supplier<R> r);

        void ifPresent(Consumer<? super R> consumer);

        boolean isEmpty();

    }

    public interface IResultTy<R> extends Monadic<R> {

        static <V> IResultTy<V> toRes(V v) {
            if (v instanceof AutoCloseable a) {
                throw new RuntimeException("Did not implement for auto-closable! Probably something to do with scopes to prove only one can be left open and any cannot be accessed twice without knowing.");
//                return (IResultTy<T>) new ClosableResult<>(Optional.of(a));
            }

            return new ResultTyResult<>(Optional.ofNullable(v));
        }

        static IResultTy<AutoCloseable> empty() {
            return new ResultTyResult<>(Optional.empty());
        }


        Optional<R> optional();

        <V> IResultTy<V> from(V r);

        <V> IResultTy<V> from(Optional<V> r);

        Stream<R> stream();
        IResultTy<R> filter(Predicate<R> p);
        R get();
        <V> IResultTy<V> flatMap(Function<R, IResultTy<V>> toMap);
        <V> IResultTy<V> map(Function<R, V> toMap);
        R orElse(R r);
        R orElseGet(Supplier<R> r);
        void ifPresent(Consumer<? super R> consumer);
        IResultTy<R> peek(Consumer<? super R> consumer);

        default void forEach(Consumer<? super R> consumer) {
            this.stream().forEach(consumer);
        }

        default boolean isEmpty() {
            return optional().isEmpty();
        }

        /**
         * If R is AutoClosable, assumes there is a terminating operation:
         *   ifPresent(...)
         *   ifPresentOrElse(..., ...)
         * @param r
         * @param <R>
         */
        record ClosableResult<R extends AutoCloseable>(Optional<R> r) implements IResultTy<R> {

            @Override
            public Stream<R> stream() {
                return r.stream();
            }

            @Override
            public Optional<R> optional() {
                return r();
            }

            @Override
            public <T> IResultTy<T> from(T r) {
                if (r instanceof AutoCloseable a) {
                    return (IResultTy<T>) new ClosableResult<>(Optional.of(a));
                }

                return (IResultTy<T>) new ClosableResult<>(Optional.empty());
            }

            @Override
            public <T> IResultTy<T> from(Optional<T> r) {
                if (r.isEmpty())
                    return (IResultTy<T>) new ClosableResult<>(Optional.empty());
                else if (r.get() instanceof AutoCloseable a) {
                    return (IResultTy<T>) new ClosableResult<>(Optional.of(a));
                }

                return (IResultTy<T>) new ClosableResult<>(Optional.empty());
            }

            @Override
            public void forEach(Consumer<? super R> consumer) {
                this.ifPresent(consumer);
            }

            @Override
            public IResultTy<R> filter(Predicate<R> p) {
                return from(r.filter(p));
            }

            @Override
            public R get() {
                return this.r.get();
            }

            @Override
            public <T> IResultTy<T> flatMap(Function<R, IResultTy<T>> toMap) {
                return from(r.flatMap(t -> {
                    var applied = toMap.apply(t);
                    return applied.optional();
                }));
            }

            @Override
            public <T> IResultTy<T> map(Function<R, T> toMap) {
                return from(r.map(toMap));
            }

            @Override
            public R orElse(R o) {
                return r.orElse(o) ;
            }

            @Override
            public R orElseGet(Supplier<R> o) {
                return r.orElseGet(o);
            }

            @Override
            public void ifPresent(Consumer<? super R> consumer) {
                r.ifPresent(rFound -> {
                    consumer.accept(rFound);
                    try {
                        rFound.close();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
            }

            @Override
            public ClosableResult<R> peek(Consumer<? super R> consumer) {
                r.ifPresent(consumer);
                return this;
            }
        }

        @Slf4j
        record StreamResult<R>(Stream<R> r) implements IResultTy<R> {

            @Override
            public Optional<R> optional() {
                var l = r.toList();
                if (l.size() > 1) {
                    log.error("Called optional on stream result with more than one value. Returning first.");
                }

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
            public IResultTy<R> filter(Predicate<R> p) {
                return new StreamResult<>(r.filter(p));
            }

            @Override
            public R get() {
                return optional().get();
            }

            @Override
            public <T> StreamResult<T> flatMap(Function<R, IResultTy<T>> toMap) {
                return new StreamResult<>(
                        r.map(toMap)
                                .flatMap(IResultTy::stream)
                );
            }

            @Override
            public <T> IResultTy<T> map(Function<R, T> toMap) {
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
        }

        record ResultTyResult<R>(Optional<R> r) implements IResultTy<R> {
            public <T> IResultTy<T> flatMap(Function<R, IResultTy<T>> toMap) {
                if (r().isEmpty())
                    return from(Optional.empty());
                return toMap.apply(r().get());
            }

            public <T> IResultTy<T> map(Function<R, T> toMap) {
                if (r().isEmpty())
                    return from(Optional.empty());
                return from(r().map(toMap));
            }

            @Override
            public R orElse(R r) {
                return this.r.orElse(r);
            }

            @Override
            public R orElseGet(Supplier<R> r) {
                return this.r.orElseGet(r);
            }

            @Override
            public void ifPresent(Consumer<? super R> consumer) {
                this.r.ifPresent(consumer);
            }

            @Override
            public IResultTy<R> peek(Consumer<? super R> consumer) {
                this.ifPresent(consumer);
                return this;
            }


            @Override
            public Stream<R> stream() {
                return this.r.stream();
            }

            @Override
            public Optional<R> optional() {
                return r;
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
            public IResultTy<R> filter(Predicate<R> p) {
                return from(this.r.filter(p));
            }

            @Override
            public R get() {
                return this.r.get();
            }
        }

        default boolean isPresent() {
            return optional().isPresent();
        }
    }


    @EqualsAndHashCode(callSuper = true)
    @Data
    public static final class Ok<R> extends ResultTy<R> {

        public Ok(R r) {
            super(r);
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        public Ok(Optional<R> r) {
            super(r);
        }

        public Ok(Stream<R> r) {
            super(r);
        }

        public Ok(IResultTy<R> r) {
            super(r);
        }

        public static <R> Ok<R> ok(R r) {
            return new Ok<>(r);
        }

        public static <R> Ok<R> stream(Stream<R> r) {
            return new Ok<>(r);
        }

        public static <R> Ok<R> ok(IResultTy<R> r) {
            return new Ok<>(r);
        }

        public static <R> Ok<R> ok(Optional<R> r) {
            return new Ok<>(r);
        }

        public static <R> Ok<R> empty() {
            return new Ok<>(Optional.empty());
        }

        public <S> Ok<S> mapResult(Function<R, S> toMap) {
            if (this.t.isPresent())
                return Ok.ok(toMap.apply(t.get()));

            return Ok.empty();
        }

        public <S> Ok<S> flatMapResult(Function<R, Ok<S>> toMap) {
            if (this.t.isPresent())
                return toMap.apply(t.get());

            return Ok.empty();
        }

        public Ok<R> filterResult(Function<R, Boolean> b) {
            if (this.t.isPresent() && b.apply(t.get())) {
                return this;
            }

            return Ok.empty();
        }

        public <U> Ok<U> cast() {
            if (t.isEmpty())
                return Ok.empty();
            try {
                return this.mapResult(s -> (U) s);
            } catch (ClassCastException c) {
                return Ok.empty();
            }
        }

        public R orElseRes(R orRes) {
            return this.t.orElse(orRes);
        }

        public R orElseGetRes(Supplier<R> orRes) {
            return this.t.orElseGet(orRes);
        }

        public Ok<R> orRes(Supplier<Ok<R>> orRes) {
            if (this.t.isPresent())
                return this;

            return orRes.get();
        }

    }

    @EqualsAndHashCode(callSuper = true)
    @RequiredArgsConstructor
    @Data
    public static final class Err<R> extends ResultTy<R> {

        public Err(R r) {
            super(r);
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        public Err(Optional<R> r) {
            super(r);
        }

        public static <R> Err<R> empty() {
            return new Err<>(Optional.empty());
        }

        public static <R> Err<R> err(R r) {
            return new Err<>(Optional.ofNullable(r));
        }

        public <S> Err<S> mapErr(Function<R, S> toMap) {
            if (this.t.isPresent())
                return Err.err(toMap.apply(t.get()));

            return Err.empty();
        }

        public <S> Err<S> flatMapErr(Function<R, Err<S>> toMap) {
            if (this.t.isPresent())
                return toMap.apply(t.get());

            return Err.empty();
        }

        public Err<R> filterErr(Function<R, Boolean> b) {
            if (this.t.isPresent() && b.apply(t.get())) {
                return this;
            }

            return Err.empty();
        }

        public <U> Err<U> cast() {
            if (t.isEmpty())
                return Err.empty();
            try {
                return this.mapErr(s -> (U) s);
            } catch (ClassCastException c) {
                return Err.empty();
            }
        }

        public R orElseErr(R orRes) {
            return this.t.orElse(orRes);
        }

        public R orElseGetErr(Supplier<R> orRes) {
            return this.t.orElseGet(orRes);
        }

        public Err<R> orErr(Supplier<Err<R>> orRes) {
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

    public <U> Result<U, E> map(Function<T, U> mapper) {
        if (this.r.isPresent()) {
            var toRet = mapper.apply(this.r.get());
            return Result.from(Ok.ok(toRet), this.e);
        }

        return this.cast();
    }

    public Result<T, E> peek(Consumer<T> mapper) {
        return Result.from(Ok.ok(this.r.peek(mapper)), this.e);
    }


    public <U> Result<U, E> map(Function<T, U> mapper, Supplier<E> err) {
        return r.<Result<U, E>>map(t -> Result.from(Ok.ok(mapper.apply(t)), this.e))
                .orElse(Result.err(err.get()));
    }

    public <E1> Result<T, E1> mapError(Function<E, E1> mapper) {
        if (this.e.isPresent()) {
            Err<E1> r1 = this.e.mapErr(mapper);
            return Result.from(this.r, r1);
        }

        return this.castError();
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

    public Result<T, E> filterResult(Predicate<T> b) {
        if (r.isPresent() && b.test(r.get())) {
            return this;
        }

        return Result.empty();
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
        return new Result<>(Ok.empty(), Err.empty());
    }

    public <E1> Result<T, E1> mapError(Function<E, E1> mapper, E1 defaultValue) {
        var err = this.mapError(mapper);
        if (err.e().isEmpty()) {
            return Result.from(r, Err.err(defaultValue));
        } else {
            return mapError(mapper).orError(() -> Err.err(defaultValue));
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

    public Result<T, E> filterErr(Function<Err<E>, Boolean> ty) {
        if(this.e.isPresent() && ty.apply(this.e)) {
            return this;
        }

        return Result.from(this.r, Err.empty());
    }

    public Result<T, E> filterRes(Function<Ok<T>, Boolean> ty) {
        if(this.r.isPresent() && ty.apply(this.r)) {
            return this;
        }

        return Result.from(Ok.empty(), this.e);
    }

    public <U> Result<U, E> flatMapRes(Function<T, Ok<U>> mapper) {
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
            return Result.from(Ok.empty(), this.e.cast());
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

    public <U, NE> Stream<Result<U, NE>> flatMapStreamResult(Function<T, Stream<Result<U, NE>>> mapper) {
        var p = map(mapper);

        if (p.r.isPresent())
            return p.r.get();

        return Stream.empty();
    }

    public Optional<T> toOptional() {
        return r.t.optional();
    }

    public Optional<T> optional() {
        return r.t.optional();
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

}
