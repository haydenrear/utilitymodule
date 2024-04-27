package com.hayden.utilitymodule.result;

import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import lombok.experimental.Delegate;
import org.checkerframework.checker.nullness.Opt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record Result<T, E extends Result.Error>(@Delegate Optional<T> result, @Nullable E error)  {

    public boolean isError() {
        return error != null;
    }

    public Result<T, E> mapError(Consumer<E> mapper) {
        mapper.accept(error);
        return this;
    }

    public static <T extends AggregateResponse, E extends AggregateError> @Nullable Result<T, E> all(Collection<Result<T, E>> mapper) {
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

    private static <T extends AggregateResponse, E extends AggregateError> Result<T, E> addErrors(Result<T, E> r, Result<T, E> result) {
        if (r.error != null) {
            if (result.error == null) {
                result = result.result
                        .map(s -> Result.from(s, r.error))
                        // this one shouldn't ever happen...
                        .orElseGet(() -> r.result
                                .map(t -> Result.from(t, r.error))
                                .orElseGet(() -> Result.fromError(r.error)));
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
        try {
            return result.map(mapper).orElse((Result<U, E>) this);
        } catch (Exception e) {
            return Result.emptyError();
        }
    }

    public Optional<T> optional() {
        return result;
    }

    public <OPT> Result<OPT, E> flatMapOptional(Function<T, Optional<OPT>> mapper) {
        return this.optional().flatMap(mapper)
                .<Result<OPT, E>>map(Result::fromResult)
                .orElse(Result.fromError(this.error));
    }

    public <U, V extends Error> Result<U, V> map(Function<T, U> mapper) {
        return result.<Result<U, V>>map(t -> Result.fromResult(mapper.apply(t)))
                .orElse(Result.emptyError());
    }

    public static <T, V extends Error> Result<T, V> emptyError() {
        return new Result<>(Optional.empty(), null);
    }

    public interface Error {
        public static Error fromMessage(String error) {
            return new StandardError(error);
        }
        public static Error fromE(Throwable error) {
            return new StandardError(error);
        }

        String getMessage();
    }

    public interface AggregateResponse {
        void add(AggregateResponse aggregateResponse);
    }

    public interface AggregateError extends Error {

        default List<String> getMessages() {
            return errors().stream().map(Error::getMessage)
                    .toList();
        }

        @Override
        default String getMessage() {
            return String.join(", ", getMessages());
        }

        List<Error> errors();

        default void addError(Error error) {
            errors().add(error);
        }

        default void addError(List<Error> error) {
            errors().addAll(error);
        }

        default String prettyPrint() {
            return """
                       Aggregate Error:
                       %s    
                    """.formatted(errors().stream().map(Error::toString).collect(Collectors.joining("\n\r")));
        }

    }

    public static <T, E extends Error> Result<T, E> fromResult(T result) {
        return new Result<>(Optional.ofNullable(result), null);
    }

    public static <T> Result<T, Result.Error> from(T result, @Nullable String error) {
        return new Result<>(Optional.ofNullable(result), new StandardError(error));
    }

    public static <T, E extends AggregateError> Result<T, E> from(T result, @Nullable E error) {
        return new Result<>(Optional.ofNullable(result), error);
    }

    public static <T, E extends Error> Result<T, E> fromError(E error) {
        return new Result<>(Optional.empty(), error);
    }

    public static <T> Result<T, Result.Error> fromError(String error) {
        return new Result<>(Optional.empty(), new StandardError(error));
    }

    public static <T> Result<T, Result.Error> fromError(Throwable error) {
        return new Result<>(Optional.empty(), new StandardError(error));
    }

    public record StandardError(String error, Throwable throwable) implements Error {
        public StandardError(Throwable throwable) {
            this(throwable.getMessage(), throwable);
        }
        public StandardError(String throwable) {
            this(throwable, null);
        }

        @Override
        public String getMessage() {
            return error;
        }
    }


}
