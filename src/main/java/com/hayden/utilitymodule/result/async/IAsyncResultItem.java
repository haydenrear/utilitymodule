package com.hayden.utilitymodule.result.async;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import com.hayden.utilitymodule.result.res_ty.IResultItem;

public interface IAsyncResultItem<R> extends IResultItem<R> {

    boolean didFinish();

    void doAsync(Consumer<? super R> consumer);

    R block() throws ExecutionException, InterruptedException;

    default R block(Duration wait) throws ExecutionException, InterruptedException {
        return block();
    }

    default boolean isAsyncSub() {
        return true;
    }

    default IAsyncManyResultItem<R> many() {
        if (this instanceof IAsyncManyResultItem<R> t) {
            return t;
        }

        return new FluxResult<>(this.firstMono().flux());
    }

}
