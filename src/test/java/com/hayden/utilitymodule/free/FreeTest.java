package com.hayden.utilitymodule.free;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import lombok.Builder;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class FreeTest {
    @Test
    public void doTest() {
        var build = new RetrievePromptEffect.BuildNextCommit(null);
        var get = new RetrievePromptEffect.GetContext();
        var err = new RetrievePromptEffect.HandleError(null);

        var instructionSet = Free.<RetrievePromptEffect, RetrievePromptEffect.RetrievePromptArgs>liftF(build)
                                          .flatMap(a -> Free.<RetrievePromptEffect, RetrievePromptEffect.RetrievePromptArgs>liftF(get)
                                                  .flatMap(ae -> Free.<RetrievePromptEffect, RetrievePromptEffect.RetrievePromptArgs>liftF(get)))
                                          .flatMap(a -> Free.<RetrievePromptEffect, RetrievePromptEffect.RetrievePromptArgs>liftF(err))
                                          .flatMap(r -> Free.pure(RetrievePromptEffect.RetrievePromptArgs.builder().retryCount(10).build()))
                                          .flatMap(r -> Free.liftF(err))
                                          .flatMap(r -> Free.pure("hello"))
                                          .flatMap(r -> Free.<RetrievePromptEffect, String>pure("hello").flatMap(Free::pure))
                                          .flatMap(r -> Free.<RetrievePromptEffect, String>pure("hello")
                                                                     .flatMap(a -> Free.liftF(err)))
                                          .flatMap(r -> Free.pure(RetrievePromptEffect.RetrievePromptArgs.builder().retryCount(8).build()))
                                          .flatMap(r -> Free.<RetrievePromptEffect, RetrievePromptEffect.RetrievePromptArgs>liftF(err))
                                          .flatMap(r -> Free.<RetrievePromptEffect, RetrievePromptEffect.RetrievePromptArgs>liftF(err));

        AtomicInteger counter = new AtomicInteger();

        Function<RetrievePromptEffect, Free<RetrievePromptEffect, RetrievePromptEffect.RetrievePromptArgs>> interpreter
                = eff -> switch (eff) {
            case RetrievePromptEffect.BuildNextCommit b -> // simulate calling a service, etc.
            {
                counter.incrementAndGet();
                yield Free.pure(RetrievePromptEffect.RetrievePromptArgs.builder()
                                                                       .skipRetry(true)
                                                                       .build());
            }
            case RetrievePromptEffect.GetContext g -> {
                counter.incrementAndGet();
                yield Free.pure(RetrievePromptEffect.RetrievePromptArgs.builder()
                                                                       .allowCtx(true)
                                                                       .build());
            }
            case RetrievePromptEffect.HandleError h -> {
                counter.incrementAndGet();
                yield Free.liftF(get);
            }
            case RetrievePromptEffect.DoRetrievePrompt retrievePromptRecursiveEffect -> {
                counter.incrementAndGet();
                yield Free.<RetrievePromptEffect, RetrievePromptEffect.RetrievePromptArgs>liftF(err)
                          .flatMap(o -> Free.liftF(err))
                          .flatMap(oae -> Free.<RetrievePromptEffect, String>pure("hello")
                                              .flatMap(oaee -> Free.liftF(err)));
            }
            case RetrievePromptEffect.DoHandleRetry doHandleRetry -> {
                counter.incrementAndGet();
                yield Free.pure(RetrievePromptEffect.RetrievePromptArgs.builder()
                                                                       .skipRetry(true)
                                                                       .build());
            }
        };


        var e = Free.parse(instructionSet, interpreter::apply);

        assertThat(e).isNotNull();
        assertThat(e).isInstanceOf(RetrievePromptEffect.RetrievePromptArgs.class);
        assertThat(counter.get()).isNotZero();

    }

    public sealed interface RetrievePromptEffect extends Effect {

        @Builder
        record RetrievePromptArgs(int retryCount, boolean allowCtx,
                                  boolean skipRetry, boolean contextRecursive) {

            public RetrievePromptArgs withAllowCtx(boolean allowCtx) {
                return new RetrievePromptArgs(retryCount, allowCtx, skipRetry, contextRecursive);
            }


            public RetrievePromptArgs incrementedRetry() {
                return RetrievePromptArgs.builder()
                                         .retryCount(retryCount + 1)
                                         .allowCtx(allowCtx)
                                         .skipRetry(skipRetry)
                                         .contextRecursive(contextRecursive)
                                         .build();
            }

            public RetrievePromptArgs withoutCtx() {
                return new RetrievePromptArgs(retryCount, false, skipRetry, contextRecursive);
            }
        }



        record DoRetrievePrompt(RetrievePromptEffect.RetrievePromptArgs args) implements RetrievePromptEffect {

        }

        record HandleError(SingleError error) implements RetrievePromptEffect {}

        record GetContext() implements RetrievePromptEffect {}

        record DoHandleRetry(RetrievePromptEffect.RetrievePromptArgs requestMoreContext) implements RetrievePromptEffect {}

        record BuildNextCommit(RetrievePromptEffect.RetrievePromptArgs requestUpdateArgs) implements RetrievePromptEffect {}


    }
}
