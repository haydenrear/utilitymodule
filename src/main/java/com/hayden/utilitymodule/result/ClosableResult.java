package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.assert_util.AssertUtil;
import com.hayden.utilitymodule.result.closable.ClosableMonitor;
import com.hayden.utilitymodule.result.ok.ClosableOk;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface ClosableResult<T extends AutoCloseable, E> extends OneResult<T, E>, AutoCloseable {

    /**
     * for debugging
     */
    ClosableMonitor closableMonitor = new ClosableMonitor();

    ClosableOk<T> r();

    default Result<T, E> except(Function<Exception, T> toDo) {
        return Result.ok(r().except(toDo));
    }

    default Result<T, E> exceptRuntime() {
        return except(exc -> {
           if(exc instanceof RuntimeException r)
               throw r;

           throw new RuntimeException(exc);
        });
    }

    default Result<T, E> exceptErr(Function<Exception, E> toDo) {
        return r().exceptErr(toDo)
                .map(Result::<T, E>err)
                .orElse(this);
    }

    static boolean hasOpenResources() {
        return closableMonitor.hasOpenResources();
    }

    static void closeAllOpenResources() {
        closableMonitor.closeAll();
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