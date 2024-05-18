package com.hayden.utilitymodule.result.error;

import com.google.common.collect.Sets;
import com.hayden.utilitymodule.result.Agg;

import java.util.Set;
import java.util.stream.Collectors;

public interface AggregateError extends Error, Agg {

    Set<Error> errors();

    default Set<String> getMessages() {
        return errors().stream().map(Error::getMessage)
                .collect(Collectors.toSet());
    }

    @Override
    default String getMessage() {
        return String.join(", ", getMessages());
    }

    default void add(Agg agg) {
        if (agg instanceof AggregateError e) {
            this.errors().addAll(e.errors());
            this.getMessages().addAll(e.getMessages());
        } else {
            throw new UnsupportedOperationException("Cannot add Aggregate type %s to %s."
                    .formatted(agg.getClass().getName(), this.getClass().getName()));
        }
    }

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

    record StandardAggregateError(Set<Error> messages) implements AggregateError {

        public StandardAggregateError(Error error) {
            this(Sets.newHashSet(error));
        }

        public StandardAggregateError(String message) {
            this(Sets.newHashSet(Error.fromMessage(message)));
        }

        public StandardAggregateError(Throwable message) {
            this(Sets.newHashSet(Error.fromE(message)));
        }

        @Override
        public Set<Error> errors() {
            return messages;
        }

    }
}
