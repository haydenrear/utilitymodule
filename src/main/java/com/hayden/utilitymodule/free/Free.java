package com.hayden.utilitymodule.free;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public sealed interface Free<F extends Effect, A> {

    Logger log = LoggerFactory.getLogger(Free.class);

    static <F extends Effect, A> Free<F, A> pure(A a) {
        return new Pure<>(a);
    }

    static <F extends Effect, A> Free<F, A> liftF(F f) {
        return new Suspend<>(f);
    }

    static <F extends Effect, A> A parse(Free<F, A> p, Interpreter<F, A> interpreter) {
        while (true) {
            switch (p) {
                // Preserve F & A in the pattern:
                case Free.Pure<F, A> v -> { return v.a(); }

                // Keep F so no cast is needed:
                case Free.Suspend<F, ? extends A> s -> {
                    p = interpreter.apply(s.f());
                }

                // Capture the wildcard via a helper so we don’t cast mapper/result:
                case Free.FlatMapped<F, ?, A> fm -> {
                    p = stepFlatMapped(fm, interpreter);
                }
            }
        }
    }

    // Helper to capture X in FlatMapped<F, X, A>
    private static <F extends Effect, X, A> Free<F, A> stepFlatMapped(
            Free.FlatMapped<F, X, A> fm,
            Interpreter<F, A> interpreter
    ) {
        var sub = fm.f();
        return switch (sub) {
            case Free.Pure<F, X> v      -> fm.mapper().apply(v.a());
            case Free.Suspend<F, X> s   -> {
                Function<X, Free<F, A>> mapper = fm.mapper();
                yield interpreter.apply(s.f()).flatMap(mapped -> {
                    try {
                        return mapper.apply((X) mapped);
                    } catch (ClassCastException castException) {
                        log.error("Could not cast {}.", mapped.getClass(), castException);
                        throw castException;
                    }
                });
            }
            case Free.FlatMapped<F, ?, ?> ignored ->
                    throw new IllegalStateException("Nested FlatMapped is not expected here");
        };
    }

    record Pure<F extends Effect, A>(A a) implements Free<F, A> {
        @Override public <B> Free<F, B> flatMap(Function<A, Free<F, B>> f) { return f.apply(a); }
    }

    record Suspend<F extends Effect, A>(F f) implements Free<F, A> {
        @Override public <B> Free<F, B> flatMap(Function<A, Free<F, B>> g) { return new FlatMapped<>(this, g); }
    }

    record FlatMapped<F extends Effect, A, B>(Free<F, A> f, Function<A, Free<F, B>> mapper)
            implements Free<F, B> {
        @Override public <C> Free<F, C> flatMap(Function<B, Free<F, C>> g) {
            return new FlatMapped<>(f, x -> mapper.apply(x).flatMap(g));
        }
    }

    <B> Free<F, B> flatMap(Function<A, Free<F, B>> f);
}
