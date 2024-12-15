package com.hayden.utilitymodule.result.agg;

import com.google.common.collect.Sets;
import com.hayden.utilitymodule.result.error.ErrorCollect;

import java.util.Set;

public interface AggregateError<T extends ErrorCollect> extends ErrorCollect, Agg, AggregateParamError<T> {

    interface StdAggregateError extends AggregateError<ErrorCollect> {}

    default void addItem(T o) {
        this.addError(o);
    }

    default void addItem(ParameterizedAgg t) {
        this.addAgg(t);
    }


    @Override
    default void addAgg(Agg t) {
        AggregateParamError.super.addItem(t);
    }

    record StandardAggregateError(Set<ErrorCollect> messages) implements StdAggregateError {

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
