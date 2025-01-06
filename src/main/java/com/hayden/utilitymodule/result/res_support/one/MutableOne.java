package com.hayden.utilitymodule.result.res_support.one;

import com.hayden.utilitymodule.result.ClosableResult;
import com.hayden.utilitymodule.result.MutableResult;
import com.hayden.utilitymodule.result.OneResult;
import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.ok.ClosableOk;
import com.hayden.utilitymodule.result.ok.MutableOk;

public record MutableOne<T, E>(MutableOk<T> r, Err<E> e)
        implements MutableResult<T, E> {

    @Override
    public void set(T value) {
        this.r.set(value);
    }
}