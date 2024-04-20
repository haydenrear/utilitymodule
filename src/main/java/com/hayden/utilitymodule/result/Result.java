package com.hayden.utilitymodule.result;

import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import lombok.experimental.Delegate;
import org.checkerframework.checker.nullness.Opt;

import java.util.ArrayList;
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


    public Result<T, E> or(Supplier<Result<T, E>> res) {
        if (result.isPresent())
            return this;
        return res.get();
    }

    public Result<T, E> flatMap(Function<T, Result<T, E>> mapper) {
        return result.map(mapper).orElse(this);
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
    }

    public interface AggregateError extends Error {

        List<Error> errors();

        default void addError(Error error) {
            errors().add(error);
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
    }


}
