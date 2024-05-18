package com.hayden.utilitymodule.result;

import com.google.common.collect.Sets;

import java.util.Set;
import java.util.stream.Collectors;

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

    interface AggregateError extends Error {

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

    record StandardError(String error, Throwable throwable) implements Error {
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

    record StandardAggregateError(
            Set<Error> messages) implements AggregateError {

        public StandardAggregateError(Error error) {
            this(Sets.newHashSet(error));
        }

        public StandardAggregateError(String message) {
            this(Sets.newHashSet(Error.fromMessage(message)));
        }

        public StandardAggregateError(Throwable message) {
            this(Sets.newHashSet(fromE(message)));
        }

        @Override
        public Set<Error> errors() {
            return messages;
        }
    }
}
