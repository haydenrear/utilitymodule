package com.hayden.utilitymodule.result.error;

import com.google.common.collect.Sets;
import com.hayden.utilitymodule.result.Agg;

import java.util.Set;
import java.util.stream.Collectors;

public interface AggregateError extends ErrorCollect, Agg {

    Set<ErrorCollect> errors();

    default boolean isError() {
        return !errors().isEmpty();
    }

    default Set<String> getMessages() {
        return errors().stream().map(ErrorCollect::getMessage)
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

    default void addError(ErrorCollect error) {
        errors().add(error);
    }

    default void addError(Set<ErrorCollect> error) {
        errors().addAll(error);
    }

    default String prettyPrint() {
        return """
                   Aggregate Error:
                   %s
                """.formatted(errors().stream().map(ErrorCollect::toString).collect(Collectors.joining("\n\r")));
    }

    record StandardAggregateError(Set<ErrorCollect> messages) implements AggregateError {

        public StandardAggregateError(ErrorCollect error) {
            this(Sets.newHashSet(error));
        }

        public StandardAggregateError(String message) {
            this(Sets.newHashSet(ErrorCollect.fromMessage(message)));
        }

        public StandardAggregateError(Throwable message) {
            this(Sets.newHashSet(ErrorCollect.fromE(message)));
        }

        @Override
        public Set<ErrorCollect> errors() {
            return messages;
        }

    }
}
