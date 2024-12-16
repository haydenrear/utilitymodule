package com.hayden.utilitymodule.result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hayden.utilitymodule.Either;
import com.hayden.utilitymodule.result.agg.Responses;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.map.StreamResultCollector;
import com.hayden.utilitymodule.result.res_many.IManyResultTy;
import com.hayden.utilitymodule.result.res_many.ListResult;
import com.hayden.utilitymodule.result.res_ty.IResultTy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record OkErrRes<T, E>(Responses.Ok<T> r, Err<E> e) implements Result<T, E> {

    public Result<List<T>, List<E>> collectList() {
        return toResultLists();
    }

    public Err<E> error() {
        return null;
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
                    Result.from(Responses.Ok.ok(sr.map(mapper)), this.e);
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
                    Result.from(this.r, Err.err(new ListResult<>(this.e.stream().map(mapper).toList())));
            default -> {
                if (this.e.isPresent()) {
                    Err<E1> r1 = this.e.mapErr(mapper);
                    yield Result.from(this.r, r1);
                }

                yield this.castError();
            }
        };
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

    public <E1> Result<T, E1> mapError(Function<E, E1> mapper, E1 publicValue) {
        return switch(this.e.t) {
            case IManyResultTy<E> st ->
                    Result.from(this.r, Err.err(st.map(mapper)));
            default -> {
                var err = this.mapError(mapper);
                if (err.e().isEmpty()) {
                    yield Result.from(r, Err.err(publicValue));
                } else {
                    yield mapError(mapper).orError(() -> Err.err(publicValue));
                }
            }
        };
    }

    public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
        return switch(this.r.t) {
            case IManyResultTy<T> sr -> Result.from(Stream.of(Result.ok(sr), Result.err(this.e)))
                    .flatMap(mapper);
            default -> {
                if (this.r.isPresent()) {
                    var applied = mapper.apply(this.r.get());
                    Err<E> e1 = applied.e();
                    yield Result.from(applied.r(), e1.addError(this.e));
                }

                yield this.cast();
            }
        };
    }

    @Override
    public Stream<T> toStream() {
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

    public Result<T, E> orElseErr(Result<T, E> or) {
        if (this.r.isPresent()) {

        }
        if (this.e.isPresent())
            return this;

        or.r().t = this.r.t;

        return or;
    }

    public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper, Supplier<E> errorSupplier) {
        return r.map(mapper)
                .filter(r -> r.r().isPresent())
                .orElse(Result.err(errorSupplier.get()));
    }

    public <U> Result<U, E> flatMapResult(Function<T, Result<U, E>> mapper) {
        return switch(this.r.t) {
            case IManyResultTy<T> sr -> {
                var srt = sr.map(mapper);
                yield Result.from(Stream.concat(srt.stream(), Stream.of(Result.err(this.e))));
            }
            default -> {
                if (this.r.isEmpty()) {
                    yield this.cast();
                } else {

                    var mapped =  mapper.apply(this.r.get());
                    mapped.e().t = this.e.t;
                    yield mapped;
                }
            }
        };
    }

    public boolean isOk() {
        return r.isPresent();
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
