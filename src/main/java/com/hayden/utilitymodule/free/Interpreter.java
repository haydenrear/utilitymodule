package com.hayden.utilitymodule.free;

import java.util.function.Function;

public interface Interpreter<F extends Effect, A> extends Function<F, Free<F, A>> {
}
