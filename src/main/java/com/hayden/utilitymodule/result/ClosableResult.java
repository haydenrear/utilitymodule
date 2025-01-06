package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.assert_util.AssertUtil;
import com.hayden.utilitymodule.result.closable.ClosableMonitor;
import com.hayden.utilitymodule.result.ok.ClosableOk;

import java.util.function.Consumer;

public interface ClosableResult<T extends AutoCloseable, E> extends OneResult<T, E>, AutoCloseable {

    /**
     * for debugging
     */
    ClosableMonitor closableMonitor = new ClosableMonitor();

    ClosableOk<T> r();

    static boolean hasOpenResources() {
        return closableMonitor.hasOpenResources();
    }

    default void onInitialize() {
        if (this.r().isPresent()) {
            AssertUtil.assertTrue(() -> this.r().isOne(), "On initialize failed - Closable result type was more than one - not implemented.");
            closableMonitor.onInitialize(() -> this.r().getClosableQuietly());
        }
    }

    default ClosableResult doOnClosable(Consumer<? super T> e) {
        this.r().ifPresent(a -> {
            e.accept(a);
            closableMonitor.afterClose(() -> a);
        });
        return this;
    }

    default void doOnEach(Consumer<? super T> e) {
        doOnClosable(e);
    }

    default void ifPresent(Consumer<? super T> consumer) {
        doOnClosable(consumer);
    }

}