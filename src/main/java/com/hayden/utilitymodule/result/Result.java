package com.hayden.utilitymodule.result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.hayden.utilitymodule.result.error.AggregateError;
import com.hayden.utilitymodule.result.error.Error;
import com.hayden.utilitymodule.result.res.Responses;
import jakarta.annotation.Nullable;
import lombok.experimental.Delegate;

import java.util.*;
import java.util.function.*;

public record Result<T, E extends Error>(@Delegate Optional<T> result,
                                         @Nullable E error) {

    public static <T, E extends Error> Result<T, E> fromThunk(Optional<T> result, Supplier<E> e) {
        return result.map(Result::<T, E>ok)
                .orElse(Result.err(e.get()));
    }

    public static <T, E extends Error> Result<T, E> ok(T result) {
        return new Result<>(Optional.ofNullable(result), null);
    }

    public static <T> Result<T, Error> from(@Nullable T result, @Nullable String error) {
        return new Result<>(Optional.ofNullable(result), new Error.StandardError(error));
    }

    public static <T, E extends AggregateError> Result<T, E> from(@Nullable T result, @Nullable E error) {
        return new Result<>(Optional.ofNullable(result), error);
    }

    public static <T, E extends Error> Result<T, E> from(@Nullable T result, @Nullable E error) {
        return new Result<>(Optional.ofNullable(result), error);
    }

    public static <T, E extends AggregateError> Result<T, E> fromValues(@Nullable T result, @Nullable E error) {
        return new Result<>(Optional.ofNullable(result), error);
    }

    public static <T, E extends AggregateError> Result<T, E> from(Optional<T> result, @Nullable E error) {
        return new Result<>(result, error);
    }

    public static <T, E extends Error> Result<T, E> err(@Nullable E error) {
        return Optional.ofNullable(error)
                .map(e -> new Result<>(Optional.<T>empty(), e))
                .orElse(Result.emptyError());
    }

    public static <T> Result<T, Error> err(String error) {
        return new Result<>(Optional.empty(), new Error.StandardError(error));
    }

    public static <T> Result<T, Error> err(Throwable error) {
        return new Result<>(Optional.empty(), new Error.StandardError(error));
    }

    public boolean isOk() {
        return result.isPresent();
    }

    public boolean isError() {
        return error != null;
    }

    public Result<T, E> doOnError(Consumer<E> mapper) {
        Optional.ofNullable(error).ifPresent(mapper);
        return this;
    }


    public <E1 extends Error> Result<T, E1> mapError(Function<E, E1> mapper) {
        var e = Optional.ofNullable(error).map(mapper);
        if (e.isEmpty()) {
            return (Result) this;
        } else {
            return Result.err(e.get());
        }
    }

    public <E1 extends Error> Result<T, E1> mapError(Function<E, E1> mapper, E1 defaultValue) {
        var e = Optional.ofNullable(error).map(mapper);
        if (e.isEmpty()) {
            return Result.from(result.orElse(null), defaultValue);
        } else {
            return Result.err(e.get());
        }
    }

    public static <T extends Responses.AggregateResponse, E extends AggregateError> @Nullable Result<T, E> all(Collection<Result<T, E>> mapper, Result<T, E> finalResult) {
        return Optional.ofNullable(all(mapper))
                .flatMap(r -> Optional.ofNullable(r.error))
                .map(e -> {
                    Optional.ofNullable(finalResult.error)
                            .flatMap(f -> Optional.ofNullable(f.errors()))
                            .ifPresent(f -> f.addAll(e.errors()));
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
            if (nextResultToAdd.result.isPresent()) {
                // can't add a ref, only update current, because it's immutable.
                if (result.result.isEmpty()) {
                    var temp = result;
                    result = nextResultToAdd;
                    nextResultToAdd = temp;
                } else {
                    result.result.get().add(nextResultToAdd.result.get());
                }
            }

            result = addErrors(nextResultToAdd, result);
        }

        return result;
    }

    private static <T extends Responses.AggregateResponse, E extends AggregateError> Result<T, E> addErrors(Result<T, E> r, Result<T, E> result) {
        if (r.error != null) {
            if (result.error == null) {
                result = result.result
                        .map(s -> Result.from(s, r.error))
                        // this one shouldn't ever happen...
                        .orElseGet(() -> r.result
                                .map(t -> Result.from(t, r.error))
                                .orElseGet(() -> Result.err(r.error)));
            } else {
                Optional.ofNullable(r.error.errors())
                        .ifPresent(result.error::addError);
            }
        }
        return result;
    }


    public Result<T, E> or(Supplier<Result<T, E>> res) {
        if (result.isPresent())
            return this;
        return res.get();
    }

    public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
        return result.map(mapper)
                .filter(r -> r.result.isPresent())
                .orElse(this.cast());
    }

    public <NE extends Error> Result<T, NE> flatMapError(Supplier<Result<T, NE>> mapper) {
        return Optional.ofNullable(mapper.get())
                .filter(Result::isError)
                .orElse(this.castError());
    }

    public <NE extends Error> Result<T, NE> flatMapError(Function<E, Result<T, NE>> mapper) {
        return Optional.ofNullable(error)
                .map(mapper)
                .filter(Result::isError)
                .orElseGet(() -> (Result) this);
    }

    public <U, NE extends Error> Result<U, NE> flatMapResult(Function<T, Result<U, NE>> mapper) {
        return result.map(mapper)
                .filter(r -> r.result.isPresent() || r.error != null)
                .orElseThrow(() -> new RuntimeException("Failed to flatmap as neither result or error provided."));
    }

    public Optional<T> optional() {
        return result;
    }

    public <U, V extends Error> Result<U, V> map(Function<T, U> mapper) {
        return result.<Result<U, V>>map(t -> Result.ok(mapper.apply(t)))
                .orElse(Result.emptyError());
    }

    public <U, V extends Error> Result<U, V> cast() {
        return this.flatMapResult(c -> (Result) Result.ok(c));
    }

    public <V extends Error> Result<T, V> castError() {
        return this.flatMapError(c -> (Result) Result.err(c));
    }

    public <R extends Result<U, V>, U, V extends Error> R cast(TypeReference<R> refDelegate) {
        return (R) this.map(c -> (U) c);
    }

    public static <T, V extends Error> Result<T, V> emptyError() {
        return new Result<>(Optional.empty(), null);
    }



}
