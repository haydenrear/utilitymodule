package com.hayden.utilitymodule.result;

import jakarta.annotation.Nullable;
import lombok.experimental.Delegate;
import org.checkerframework.checker.nullness.Opt;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public record Result<T, E extends Result.Error>(@Delegate Optional<T> result, @Nullable E error)  {

    public boolean isError() {
        return error != null;
    }

    public interface Error {
        public static Error fromMessage(String error) {
            return new StandardError(error);
        }
    }

    public static <T> Result<T, Result.Error> fromResult(T result) {
        return new Result<>(Optional.ofNullable(result), null);
    }

    public static <T> Result<T, Result.Error> from(T result, @Nullable String error) {
        return new Result<>(Optional.ofNullable(result), new StandardError(error));
    }

    public static <T> Result<T, Result.Error> fromError(Result.Error error) {
        return new Result<>(Optional.empty(), error);
    }

    public static <T> Result<T, Result.Error> fromError(String error) {
        return new Result<>(Optional.empty(), new StandardError(error));
    }

    public record StandardError(String error) implements Error {}


}
