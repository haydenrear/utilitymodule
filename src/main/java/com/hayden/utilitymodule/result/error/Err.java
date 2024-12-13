package com.hayden.utilitymodule.result.error;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.ResultTy;
import com.hayden.utilitymodule.result.agg.Responses;
import com.hayden.utilitymodule.result.res_many.StreamResult;
import com.hayden.utilitymodule.result.res_ty.IResultTy;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Data
public final class Err<R> extends ResultTy<R> {

    public Err(Stream<R> r) {
        super(r);
    }

    public Err(IResultTy<R> r) {
        super(r);
    }

    public Err(Mono<R> r) {
        super(r);
    }

    public Err(Flux<R> r) {
        super(r);
    }

    public static <R> Err<R> stream(Stream<R> r) {
        return new Err<>(r);
    }

    public static <R> Err<R> err(IResultTy<R> r) {
        return new Err<>(r);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <R> Err<R> err(Optional<R> r) {
        return new Err<>(r);
    }

    public Err(R r) {
        super(r);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public Err(Optional<R> r) {
        super(r);
    }

    public static <R> Err<R> empty() {
        return new Err<>(Optional.empty());
    }

    public static <R> Err<R> err(R r) {
        return new Err<>(Optional.ofNullable(r));
    }

    public synchronized <U> void extractError(Result<U, R> flattened) {
        if (this.t != null) {
            Stream.Builder<R> built = Stream.builder();
            this.t.forEach(built::add);
            flattened.e().t.forEach(built::add);
            this.t = new StreamResult<>(built.build());
        }
    }

    public <S> Err<S> mapErr(Function<R, S> toMap) {
        if (this.t.isPresent())
            return Err.err(toMap.apply(t.get()));

        return Err.empty();
    }

    public <S> Err<S> flatMapErr(Function<R, Err<S>> toMap) {
        if (this.t.isPresent())
            return toMap.apply(t.get());

        return Err.empty();
    }

    public Err<R> filterErr(Predicate<R> b) {
        if (this.t.isPresent() && b.test(t.get())) {
            return this;
        }

        return Err.empty();
    }

    public <U> Err<U> cast() {
        if (t.isEmpty())
            return Err.empty();
        try {
            return this.mapErr(s -> (U) s);
        } catch (ClassCastException c) {
            return Err.empty();
        }
    }

    public R orElseErr(R orRes) {
        return this.t.orElse(orRes);
    }

    public R orElseGetErr(Supplier<R> orRes) {
        return this.t.orElseGet(orRes);
    }

    public Err<R> orErr(Supplier<Err<R>> orRes) {
        if (this.t.isPresent())
            return this;

        return orRes.get();
    }

}
