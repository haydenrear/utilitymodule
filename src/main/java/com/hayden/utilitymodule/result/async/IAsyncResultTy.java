package com.hayden.utilitymodule.result.async;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import com.hayden.utilitymodule.result.res_ty.IResultTy;

public interface IAsyncResultTy<R> extends IResultTy<R> {

    boolean didFinish();

    void doAsync(Consumer<? super R> consumer);

    R block() throws ExecutionException, InterruptedException;

    default R block(Duration wait) throws ExecutionException, InterruptedException {
        return block();
    }

    default R blockFirst() throws ExecutionException, InterruptedException {
        return block();
    }

    default R blockLast() throws ExecutionException, InterruptedException {
        return block();
    }

    default List<R> blockAll() throws ExecutionException, InterruptedException {
        return List.of(block());
    }

    default List<R> blockAll(Duration duration) throws ExecutionException, InterruptedException {
        return blockAll();
    }

    default boolean isAsyncSub() {
        return true;
    }

}
