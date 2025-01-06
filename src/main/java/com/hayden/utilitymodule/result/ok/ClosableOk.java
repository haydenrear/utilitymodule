package com.hayden.utilitymodule.result.ok;

import com.hayden.utilitymodule.result.res_support.one.ResultTy;
import com.hayden.utilitymodule.result.res_ty.ClosableResult;
import com.hayden.utilitymodule.result.res_ty.IResultItem;

import java.util.Optional;
import java.util.stream.Stream;

public class ClosableOk<R extends AutoCloseable> extends ResultTy<R> implements Ok<R> {

    public ClosableOk(R r) {
        super(r);
    }

    @Override
    public Stream<R> detachedStream() {
        return Stream.empty();
    }

    public ClosableOk(IResultItem<R> r) {
        super(r);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ClosableOk(Optional<R> r) {
        super(r);
    }

    public static <R extends AutoCloseable> ClosableOk<R> ok(R r) {
        return new ClosableOk<R>(r);
    }

    public static <R extends AutoCloseable> ClosableOk<R> ok(ClosableResult<R> r) {
        return new ClosableOk<>(r);
    }

    public static <R extends AutoCloseable> ClosableOk<R> emptyClosable() {
        return new ClosableOk<>(Optional.empty());
    }

    @Override
    public IResultItem<R> t() {
        return t;
    }
}
