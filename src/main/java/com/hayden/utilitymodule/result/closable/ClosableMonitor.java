package com.hayden.utilitymodule.result.closable;

import java.util.function.Consumer;

public class ClosableMonitor {

    public <T extends AutoCloseable> void afterClose(Consumer<T> e) {
    }

    public <T extends AutoCloseable> void onInitialize(Consumer<T> e) {
    }

}
