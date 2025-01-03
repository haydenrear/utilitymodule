package com.hayden.utilitymodule.result.res_support.one;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hayden.utilitymodule.Either;
import com.hayden.utilitymodule.result.ManyResult;
import com.hayden.utilitymodule.result.OneResult;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.ok.Ok;
import com.hayden.utilitymodule.result.res_many.ListResultItem;
import com.hayden.utilitymodule.result.res_ty.IResultItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Note OneOkErrRes can also contain Ok or Err with StreamResult inside
 * @param r
 * @param e
 * @param <T>
 * @param <E>
 */
public record OneOkErrRes<T, E>(Ok<T> r, Err<E> e) implements OneResult<T, E>, ManyResult<T, E> {

    public Err<E> error() {
        return e;
    }

    public Stream<T> stream() {
        return r.stream();
    }

    public Stream<E> streamErr() {
        return e.stream();
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
        if (this.e.isMany())
            return this.e.many().has(e);
        else return this.e.filterErr(e)
                .isPresent();
    }

    public <U> OneResult<U, E> map(Function<T, U> mapper) {
        if (this.r.isMany())
            return Result.from(Ok.ok(this.r.many().map(mapper)), this.e).one();
        else {
            if (this.r.isPresent()) {
                var toRet = mapper.apply(this.r.get());
                return Result.from(Ok.ok(toRet), this.e).one();
            }

            return this.cast();
        }
    }

    public OneResult<T, E> peek(Consumer<T> mapper) {
        return Result.from(Ok.ok(this.r.peek(mapper)), this.e).one();
    }


    public <U> OneResult<U, E> map(Function<T, U> mapper, Supplier<E> err) {
        return r.<Result<U, E>>map(t -> Result.from(Ok.ok(mapper.apply(t)), this.e))
                .orElse(Result.err(err.get())).one();
    }

    @Override
    public ManyResult<T, E> peekError(Consumer<E> mapper) {
        return new OneOkErrRes<>(this.r, Err.err(this.e.peek(mapper)));
    }


    public <E1> OneResult<T, E1> mapError(Function<E, E1> mapper) {
        if (this.e.isMany())
            return Result.from(this.r, Err.err(new ListResultItem<>(this.e.stream().map(mapper).toList()))).one();
        else {
            if (this.e.isPresent()) {
                Err<E1> r1 = this.e.mapErr(mapper);
                return Result.from(this.r, r1).one();
            }

            return this.castError();
        }
    }

    public OneResult<T, E> or(Supplier<Result<T, E>> s) {
        if (this.r.isPresent())
            return this;
        return s.get().one();
    }

    public OneResult<T, E> filterErr(Predicate<E> b) {
        if (this.e.isMany()) {
            var filtered = this.e.many().filter(b);

            return Result.from(this.r, Err.err(filtered)).one();
        } else {
            if (e.isPresent() && b.test(e.get())) {
                return this;
            }

            return Result.<T, E>from(this.r, Err.empty()).one();
        }
    }

    public OneResult<T, E> filterResult(Predicate<T> b) {
        if (this.r.isMany()) {
            var filtered = r.many().filter(b);

            return Result.from(Ok.ok(filtered), this.e).one();
        } else {
            if (r.isPresent() && b.test(r.get())) {
                return this;
            }

            return Result.<T, E>err(this.e).one();
        }
    }

    public void close() {
        this.r.flatMap(t -> t instanceof AutoCloseable a ? IResultItem.toRes(a) : IResultItem.empty())
                .ifPresent(t -> {
                    try {
                        t.close();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
    }

    public <E1> OneResult<T, E1> mapError(Function<E, E1> mapper, E1 publicValue) {
        if (this.e.isMany()) {
            return Result.from(this.r, Err.err(this.e.many().map(mapper))).one();
        } else {
            var err = this.mapError(mapper);
            if (err.e().isEmpty()) {
                return Result.from(r, Err.err(publicValue)).one();
            } else {
                return mapError(mapper).one().orError(() -> Err.err(publicValue)).one();
            }
        }
    }

    public <U> ManyResult<U, E> flatMap(Function<T, Result<U, E>> mapper) {
        if (this.r.isMany()) {
            var m = Result.from(Stream.of(Result.ok(this.r), Result.err(this.e)))
                        .flatMap(mapper);

            return m.many();
        } else {
            if (this.r.isPresent()) {
                var applied = mapper.apply(this.r.get());
                Err<E> e1 = applied.e();
                return Result.from(applied.r(), e1.addError(this.e)).one();
            }

            return this.cast();
        }
    }

    @Override
    public Stream<T> toStream() {
        return this.r.stream();
    }

    @Override
    public Stream<T> detachedStream() {
        return this.r.stream();
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

    public <U> OneResult<U, E> flatMap(Function<T, Result<U, E>> mapper, Supplier<E> errorSupplier) {
        return r.map(mapper)
                .filter(r -> r.r().isPresent())
                .orElse(Result.err(errorSupplier.get()))
                .one();
    }

    public <U> ManyResult<U, E> flatMapResult(Function<T, Result<U, E>> mapper) {
        if (this.r.isMany()) {
            var srt = r.many().map(mapper);
            return Result.from(Stream.concat(srt.stream(), Stream.of(Result.err(this.e)))).many();
        } else {
            if (this.r().isEmpty()) {
                return this.cast();
            } else {
                var s = r().map(mapper)
                        .single()
                        .get();

                s.e().addError(this.e());
                return s.one();
            }
        }
    }

    public boolean isOk() {
        return r.isPresent();
    }

    public Optional<T> toOptional() {
        return r.firstOptional();
    }

    public Optional<T> optional() {
        return toOptional();
    }

    public <U> OneResult<U, E> cast() {
        return Result.<U, E>from(this.r.cast(), this.e).one();
    }

    public <V> OneResult<T, V> castError() {
        return Result.<T, V>from(this.r, this.e.cast()).one();
    }

    public <R extends Result<U, V>, U, V> R cast(TypeReference<R> refDelegate) {
        return (R) this.map(c -> (U) c);
    }

    public OneResult<T, E> doOnError(Consumer<E> e) {
        this.e.ifPresent(e);
        return this;
    }

    public void doOnEach(Consumer<T> e) {
        this.r.forEach(e);
    }

    public Stream<Either<Ok<T>, Err<E>>> toEntryStream() {
        List<Either<Ok<T>, Err<E>>> l = this.r.stream()
                .map(t -> Either.<Ok<T>, Err<E>>from(Ok.ok(t), null))
                .collect(Collectors.toCollection(ArrayList::new));
        List<Either<Ok<T>, Err<E>>> r = this.e.stream()
                .map(t -> Either.<Ok<T>, Err<E>>from(null, Err.err(t)))
                .collect(Collectors.toCollection(() -> l));

        return r.stream();
    }

}
