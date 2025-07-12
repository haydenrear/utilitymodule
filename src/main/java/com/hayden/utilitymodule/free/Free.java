package com.hayden.utilitymodule.free;

import java.util.function.Function;

public sealed interface Free<F extends Effect, A> {

    static <F extends Effect, A> Free<F, A> pure(A free) {
        return new Pure<>(free);
    }

    static <F extends Effect, A> Free<F, A> liftF(F free) {
        return new Suspend(free);
    }

    static <F extends Effect, A> A parse(Free<F, A> p,
                                         Interpreter<F, A> interpreter) {
        A out = null;
        while(out == null) {
            switch(p) {
                case Free.FlatMapped v -> {
                    var sub = v.f();
                    switch(sub) {
                        case Free.Suspend suspend -> {
                            var result = interpreter.apply((F) suspend.f());
                            p = result.flatMap(v.mapper());
                        }
                        case Free.FlatMapped ignored ->
                                throw new RuntimeException("flatMapped found and not expected");
                        case Free.Pure ignored ->
                                throw new RuntimeException("pure found and not expected");
                    }
                }
                case Free.Pure<? extends Effect, A> v -> out = v.a();
                case Free.Suspend v -> p = interpreter.apply((F) v.f());
            }

        }

        return out;
    }

    record Pure<F extends Effect, A>(A a) implements Free<F, A> {
        @Override
        public <B> Free<F, B> flatMap(Function<A, Free<F, B>> free) {
            return free.apply(a);
        }
    }

    record Suspend<F extends Effect, A>(F f) implements Free<F, A> {
        @Override
        public <B> Free<F, B> flatMap(Function<A, Free<F, B>> free) {
            return new FlatMapped<>(this, free);
        }
    }

    record FlatMapped<F extends Effect, A, B>(Free<F, A> f, Function<A, Free<F, B>> mapper) implements Free<F, B> {

        @Override
        public <C> Free<F, C> flatMap(Function<B, Free<F, C>> free) {
            return new FlatMapped<>(f, x -> mapper.apply(x).flatMap(free));
        }
    }

    <B> Free<F, B> flatMap(Function<A, Free<F, B>> free);

}
