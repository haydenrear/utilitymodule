package com.hayden.utilitymodule.result.agg;

import com.hayden.utilitymodule.result.error.ErrorCollect;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public interface AggregateParamError<T extends ErrorCollect>
        extends Agg.ParameterizedAgg<T>, ErrorCollect {

    Set<T> errors();

    default boolean isError() {
        return !errors().isEmpty();
    }

    default Set<String> getMessages() {
        return errors().stream().map(ErrorCollect::getMessage)
                .collect(Collectors.toSet());
    }

    default String getMessage() {
        return String.join(", ", getMessages());
    }

    default void addItem(T toAdd) {
        this.addError(toAdd);
    }

    default List<T> all() {
        return errors().stream().toList();
    }

    default void addItem(Agg agg) {
        if (agg instanceof AggregateParamError e) {
            this.errors().addAll(e.errors());
            this.getMessages().addAll(e.getMessages());
        } else {
            throw new UnsupportedOperationException("Cannot add Aggregate type %s to %s."
                    .formatted(agg.getClass().getName(), this.getClass().getName()));
        }
    }

    default void addError(T error) {
        errors().add(error);
    }

    default void addError(Set<T> error) {
        errors().addAll(error);
    }

    default String prettyPrint() {
        return """
                   Aggregate Error:
                   %s
                """.formatted(errors().stream().map(ErrorCollect::toString).collect(Collectors.joining("\n\r")));
    }

}
