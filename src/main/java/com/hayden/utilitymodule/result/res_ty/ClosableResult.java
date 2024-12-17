package com.hayden.utilitymodule.result.res_ty;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.res_single.ISingleResultItem;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * If R is AutoClosable, assumes there is a terminating operation:
 * ifPresent(...)
 * ifPresentOrElse(..., ...)
 *
 * @param r
 * @param <R>
 */
@Slf4j
public record ClosableResult<R extends AutoCloseable>(Optional<R> r, @Nullable Callable<Void> onClose)
        implements ISingleResultItem<R> {

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ClosableResult(Optional<R> r) {
        this(r, null);
    }


    @Override
    public Stream<R> stream() {
        // TODO: should this fail?
        log.warn("Calling close after terminate on stream for result AutoClosable.");
        return r.stream().onClose(this::doClose);
    }

    @Override
    public Flux<R> flux() {
        // TODO: should this fail?
        log.warn("Calling close after terminate on flux for result AutoClosable.");
        return r.map(Flux::just)
                .orElse(Flux.empty())
                .doAfterTerminate(this::doClose);
    }

    @Override
    public Mono<R> firstMono() {
        // TODO: should this fail?
        log.warn("Calling close after terminate on mono for result AutoClosable.");
        return Mono.justOrEmpty(r).doAfterTerminate(this::doClose);
    }

    @Override
    public Optional<R> firstOptional() {
        // TODO: should this fail?
        Result.logClosableMaybeNotClosed();
        return r();
    }

    @Override
    public Stream<R> detachedStream() {
        return this.stream();
    }

    @Override
    public <T> IResultItem<T> from(T r) {
        if (r instanceof AutoCloseable a) {
            return (IResultItem<T>) new ClosableResult<>(Optional.of(a));
        }

        return (IResultItem<T>) new ClosableResult<>(Optional.empty());
    }

    @Override
    public boolean isZeroOrOneAbstraction() {
        return true;
    }

    @Override
    public <T> IResultItem<T> from(Optional<T> r) {
        if (r.isEmpty())
            return (IResultItem<T>) new ClosableResult<>(Optional.empty());
        else if (r.get() instanceof AutoCloseable a) {
            return (IResultItem<T>) new ClosableResult<>(Optional.of(a));
        }

        return (IResultItem<T>) new ClosableResult<>(Optional.empty());
    }

    @Override
    public void forEach(Consumer<? super R> consumer) {
        ifPresent(consumer);
    }

    @Override
    public IResultItem<R> filter(Predicate<R> p) {
        return from(r.filter(p));
    }

    @Override
    public R get() {
        log.warn("Calling or else on closable. This probably means you have to close yourself...");
        Result.logClosableMaybeNotClosed();
        return this.r.orElse(null);
    }

    @Override
    public <T> IResultItem<T> flatMap(Function<R, IResultItem<T>> toMap) {
        return from(r.flatMap(t -> {
            var applied = toMap.apply(t);
            return applied.firstOptional();
        }));
    }

    @Override
    public <T> IResultItem<T> map(Function<R, T> toMap) {
        return from(r.map(toMap));
    }

    @Override
    public Optional<R> optional() {
        return r;
    }

    @Override
    public R orElse(R o) {
        log.warn("Calling or else on closable. This probably means you have to close yourself...");
        Result.logClosableMaybeNotClosed();
        return r.orElse(o);
    }

    @Override
    public R orElseGet(Supplier<R> o) {
        log.warn("Calling or else on closable. This probably means you have to close yourself...");
        Result.logClosableMaybeNotClosed();
        return r.orElseGet(o);
    }

    @Override
    public void ifPresent(Consumer<? super R> consumer) {
        r.ifPresent(consumer);
        doClose();
    }

    private void doClose() {
        try {
            log.debug("Doing close on ClosableResult closable.");
            this.r.ifPresent(rFound -> {
                try {
                    rFound.close();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            Optional.ofNullable(onClose)
                    .ifPresent(c -> {
                        try {
                            c.call();
                        } catch (
                                Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public ClosableResult<R> peek(Consumer<? super R> consumer) {
        r.ifPresent(consumer);
        return this;
    }

    @Override
    public boolean isMany() {
        return false;
    }

    @Override
    public boolean isOne() {
        return true;
    }
}
