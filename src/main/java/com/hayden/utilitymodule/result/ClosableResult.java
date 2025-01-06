package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.result.closable.ClosableMonitor;

import java.util.function.Consumer;

public interface ClosableResult<T extends AutoCloseable, E> extends OneResult<T, E>, AutoCloseable {

    ClosableMonitor closableMonitor = new ClosableMonitor();

    default void onInitialize() {
        if (this.r().isPresent())
            closableMonitor.onInitialize(s -> this.r().get());
    }

    default ClosableResult doOnClosable(Consumer<T> e) {
        this.r().ifPresent(e);
        closableMonitor.afterClose(e);
        return this;
    }

    default void doOnEach(Consumer<T> e) {
        doOnClosable(e);
    }

}