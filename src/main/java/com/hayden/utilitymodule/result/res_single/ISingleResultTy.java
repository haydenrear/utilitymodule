package com.hayden.utilitymodule.result.res_single;

import com.hayden.utilitymodule.result.res_ty.IResultTy;

import java.util.Optional;
import java.util.function.Supplier;

public interface ISingleResultTy<R> extends IResultTy<R> {


    Optional<R> optional();

    R orElse(R r);

    R orElseGet(Supplier<R> r);

    default boolean isEmpty() {
        return optional().isEmpty();
    }

    default boolean isPresent() {
        return optional().isPresent();
    }

    default ISingleResultTy<R> single() {
        return this;
    }

}
