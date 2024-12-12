package com.hayden.utilitymodule.result.async;

import java.util.function.Consumer;

import com.hayden.utilitymodule.result.res_ty.IResultTy;

public interface IAsyncResultTy<R> extends IResultTy<R> {

    void subscribe(Consumer<? super R> consumer);

    default boolean isAsyncSub() {
        return true;
    }

}
