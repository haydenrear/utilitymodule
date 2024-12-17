package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.result.error.Err;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface ManyResult<R, E> extends Result<R, E> {

    @Override
    default ManyResult<R, E> many() {
        return this;
    }

    Result<R, E> hasAnyOr(Supplier<Result<R, E>> s);

    Result<R, E> last(Consumer<Result<R, E>> last);

    R hasFirstErrOrElseGet(Function<Err<E>, R> or);

    R firstResOrElse(R or);

    Result<R, E> firstErrOr(Supplier<Err<E>> s);


}
