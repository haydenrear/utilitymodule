package com.hayden.utilitymodule.result;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Nullable;
import lombok.experimental.Delegate;

import java.util.*;
import java.util.function.*;

public record Result<T, E extends Error>(@Delegate Optional<T> result,
                                         @Nullable E error) {

    public interface AggregateResponse {
        void add(AggregateResponse aggregateResponse);
    }

    public static <T, E extends Error> Result<T, E> from(Optional<T> result, Supplier<E> e) {
        return result.map(Result::<T, E>ok)
                .orElse(Result.err(e.get()));
    }

    public static <T, E extends Error> Result<T, E> ok(T result) {
        return new Result<>(Optional.ofNullable(result), null);
    }

    public static <T> Result<T, Error> from(@Nullable T result, @Nullable String error) {
        return new Result<>(Optional.ofNullable(result), new Error.StandardError(error));
    }

    public static <T, E extends Error.AggregateError> Result<T, E> from(@Nullable T result, @Nullable E error) {
        return new Result<>(Optional.ofNullable(result), error);
    }

    public static <T, E extends Error.AggregateError> Result<T, E> fromValues(@Nullable T result, @Nullable E error) {
        return new Result<>(Optional.ofNullable(result), error);
    }

    public static <T, E extends Error.AggregateError> Result<T, E> from(Optional<T> result, @Nullable E error) {
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

    public Result<T, E> mapError(Consumer<E> mapper) {
        Optional.ofNullable(error).ifPresent(mapper);
        return this;
    }


    public static <T extends AggregateResponse, E extends Error.AggregateError> @Nullable Result<T, E> all(Collection<Result<T, E>> mapper, Result<T, E> finalResult) {
        return Optional.ofNullable(all(mapper))
                .flatMap(r -> Optional.ofNullable(r.error))
                .map(e -> {
                    finalResult.error.errors().addAll(e.errors()) ;
                    return finalResult;
                })
                .orElse(finalResult);
    }


    @SafeVarargs
    public static <T extends AggregateResponse, E extends Error.AggregateError> @Nullable Result<T, E> all(Result<T, E> ... mapper) {
        return all(Arrays.asList(mapper));
    }

    public static <T extends AggregateResponse, E extends Error.AggregateError> @Nullable Result<T, E> all(Collection<Result<T, E>> mapper) {
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

    private static <T extends AggregateResponse, E extends Error.AggregateError> Result<T, E> addErrors(Result<T, E> r, Result<T, E> result) {
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

    public <U> Result<?, E> flatMap(Function<T, Result<U, E>> mapper) {
        try {
            return result.map(mapper)
                    .filter(r -> r.result.isPresent())
                    .orElse((Result<U, E>) this);
        } catch (Exception e) {
            return Result.emptyError();
        }
    }

    public <U, NE extends Error> Result<U, NE> flatMapError(Function<T, Result<U, NE>> mapper) {
        try {
            return result.map(mapper)
                    .filter(Result::isError)
                    .orElse((Result) this);
        } catch (ClassCastException e) {
            return Result.err(this.error).cast();
        }
    }

    public <U, E extends Error> Result<U, E> flatMapResult(Function<T, Result<U, E>> mapper) {
        try {
            return result.map(mapper)
                    .filter(r -> r.result.isPresent())
                    .orElse((Result) this);
        } catch (ClassCastException e) {
            return Result.err(this.error).cast();
        }
    }

    public Optional<T> optional() {
        return result;
    }

    public <U, V extends Error> Result<U, V> map(Function<T, U> mapper) {
        return result.<Result<U, V>>map(t -> Result.ok(mapper.apply(t)))
                .orElse(Result.emptyError());
    }

    public <U, V extends Error> Result<U, V> cast() {
        return this.flatMapResult(c -> {
            try {
                return (Result) Result.ok(c);
            } catch (ClassCastException ex) {
                return (Result) Result.err(Error.fromMessage("Failed to cast to result: %s.".formatted(ex.getMessage())));
            }
        });
    }

    public <R extends Result<U, V>, U, V extends Error> R cast(TypeReference<R> refDelegate) {
        return (R) this.map(c -> (U) c);
    }

    public static <T, V extends Error> Result<T, V> emptyError() {
        return new Result<>(Optional.empty(), null);
    }



}
