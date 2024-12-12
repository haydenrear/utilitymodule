package com.hayden.utilitymodule.result.error;

import com.hayden.utilitymodule.result.ResultTy;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Data
public final class Err<R> extends ResultTy<R> {

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

    public Err<R> filterErr(Function<R, Boolean> b) {
        if (this.t.isPresent() && b.apply(t.get())) {
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
