package com.hayden.utilitymodule.result;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public record Result<T, E extends Result.Error>(@Delegate Optional<T> result,
                                                @Nullable E error) {

    @RequiredArgsConstructor
    public static class AggregateResultCollector<T extends AggregateResponse, E extends AggregateError> implements Collector<Result<T, E>, Result<T, E>, Result<T, E>> {

        private final T aggregateResponse;
        private final E aggregateError;

        public static <T extends AggregateResponse, E extends AggregateError> AggregateResultCollector<T, E> fromValues(T t, E e) {
            return new AggregateResultCollector<>(t, e);
        }

        public static <T extends AggregateResponse, E extends AggregateError> AggregateResultCollector<T, E> toResult(Supplier<T> t, Supplier<E> e) {
            return new AggregateResultCollector<>(t.get(), e.get());
        }

        @Override
        public Supplier<Result<T, E>> supplier() {
            return () -> Result.from(aggregateResponse, aggregateError);
        }

        @Override
        public BiConsumer<Result<T, E>, Result<T, E>> accumulator() {
            return (r1, r2) -> {
                r2.ifPresent(aggregateResponse::add);
                Optional.ofNullable(r2.error).map(E::errors).ifPresent(aggregateError::addError);
            };
        }

        @Override
        public BinaryOperator<Result<T, E>> combiner() {
            return (r1, r2) -> {
                r2.ifPresent(aggregateResponse::add);
                Optional.ofNullable(r2.error).map(E::errors).ifPresent(aggregateError::addError);
                return Result.from(aggregateResponse, aggregateError);
            };
        }

        @Override
        public Function<Result<T, E>, Result<T, E>> finisher() {
            return r1 -> Result.from(aggregateResponse, aggregateError);
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.emptySet();
        }
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

    public interface Error {
        static Error fromMessage(String error) {
            return new StandardError(error);
        }
        static Error fromE(Throwable error) {
            return new StandardError(error);
        }

        static Error fromE(Throwable error, String cause) {
            return new StandardError(cause, error);
        }

        String getMessage();
    }

    public interface AggregateResponse {
        void add(AggregateResponse aggregateResponse);
    }

    public interface AggregateError extends Error {

        default Set<String> getMessages() {
            return errors().stream().map(Error::getMessage)
                    .collect(Collectors.toSet());
        }

        @Override
        default String getMessage() {
            return String.join(", ", getMessages());
        }

        Set<Error> errors();

        default void addError(Error error) {
            errors().add(error);
        }

        default void addError(Set<Error> error) {
            errors().addAll(error);
        }

        default String prettyPrint() {
            return """
                       Aggregate Error:
                       %s    
                    """.formatted(errors().stream().map(Error::toString).collect(Collectors.joining("\n\r")));
        }

    }

    public static <T, E extends Error> Result<T, E> from(Optional<T> result, Supplier<E> e) {
        return result.map(Result::<T, E>ok)
                .orElse(Result.err(e.get()));
    }

    public static <T, E extends Error> Result<T, E> ok(T result) {
        return new Result<>(Optional.ofNullable(result), null);
    }

    public static <T> Result<T, Result.Error> from(@Nullable T result, @Nullable String error) {
        return new Result<>(Optional.ofNullable(result), new StandardError(error));
    }

    public static <T, E extends AggregateError> Result<T, E> from(@Nullable T result, @Nullable E error) {
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

    public static <T> Result<T, Result.Error> err(String error) {
        return new Result<>(Optional.empty(), new StandardError(error));
    }

    public static <T> Result<T, Result.Error> err(Throwable error) {
        return new Result<>(Optional.empty(), new StandardError(error));
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

    public static <T extends AggregateResponse, E extends AggregateError> @Nullable Result<T, E> all(Collection<Result<T, E>> mapper, Result<T, E> finalResult) {
        return Optional.ofNullable(all(mapper))
                .flatMap(r -> Optional.ofNullable(r.error))
                .map(e -> {
                    finalResult.error.errors().addAll(e.errors()) ;
                    return finalResult;
                })
                .orElse(finalResult);
    }


    @SafeVarargs
    public static <T extends AggregateResponse, E extends AggregateError> @Nullable Result<T, E> all(Result<T, E> ... mapper) {
        return all(Arrays.asList(mapper));
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

    public <U, E extends Result.Error> Result<U, E> flatMapResult(Function<T, Result<U, E>> mapper) {
        try {
            return result.map(mapper)
                    .filter(r -> r.result.isPresent())
                    .orElse((Result<U, E>) this);
        } catch (ClassCastException e) {
            return Result.err(this.error).cast();
        }
    }

    public Optional<T> optional() {
        return result;
    }

    public <OPT> Result<OPT, E> flatMapOptional(Function<T, Optional<OPT>> mapper) {
        return this.optional().flatMap(mapper)
                .<Result<OPT, E>>map(Result::ok)
                .orElse(Result.err(this.error));
    }

    public <OPT> Result<OPT, E> flatMapOptional(Function<T, Optional<OPT>> mapper, @Nullable E fallback) {
        return this.optional().flatMap(mapper)
                .<Result<OPT, E>>map(Result::ok)
                .orElse(Result.err(
                        Optional.ofNullable(fallback)
                                .orElse(this.error)
                ));
    }

    public <U, V extends Error> Result<U, V> map(Function<T, U> mapper) {
        return result.<Result<U, V>>map(t -> Result.ok(mapper.apply(t)))
                .orElse(Result.emptyError());
    }

    public <U, V extends Error> Result<U, V> cast() {
        return this.map(c -> (U) c);
    }

    public <R extends Result<U, V>, U, V extends Error> R cast(TypeReference<R> refDelegate) {
        return (R) this.map(c -> (U) c);
    }

    public static <T, V extends Error> Result<T, V> emptyError() {
        return new Result<>(Optional.empty(), null);
    }



}
