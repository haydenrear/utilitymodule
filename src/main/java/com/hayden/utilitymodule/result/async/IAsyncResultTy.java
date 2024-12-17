package com.hayden.utilitymodule.result.async;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import com.hayden.utilitymodule.result.res_single.ISingleResultTy;
import com.hayden.utilitymodule.result.res_ty.IResultTy;

public interface IAsyncResultTy<R> extends IResultTy<R> {

    boolean didFinish();

    void doAsync(Consumer<? super R> consumer);

    R block() throws ExecutionException, InterruptedException;

    default R block(Duration wait) throws ExecutionException, InterruptedException {
        return block();
    }

    default boolean isAsyncSub() {
        return true;
    }

    default IAsyncManyResultTy<R> many() {
        if (this instanceof IAsyncManyResultTy<R> t) {
            return t;
        }

        return new FluxResult<>(this.firstMono().flux());
    }

}
