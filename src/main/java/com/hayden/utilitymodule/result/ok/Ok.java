package com.hayden.utilitymodule.result.ok;

import com.hayden.utilitymodule.result.res_support.one.ResultTy;
import com.hayden.utilitymodule.result.res_many.IManyResultItem;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import lombok.Data;
import lombok.EqualsAndHashCode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

@EqualsAndHashCode(callSuper = true)
@Data
public class Ok<R> extends ResultTy<R> {

    public Ok(R r) {
        super(r);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public Ok(Optional<R> r) {
        super(r);
    }

    public Ok(Stream<R> r) {
        super(r);
    }

    public Ok(IResultItem<R> r) {
        super(r);
    }

    public Ok(Mono<R> r) {
        super(r);
    }

    public Ok(Flux<R> r) {
        super(r);
    }

    public static <R> Ok<R> ok(R r) {
        return new Ok<>(r);
    }

    public static <R> Ok<R> stream(Stream<R> r) {
        return new Ok<>(r);
    }

    public static <R> Ok<R> ok(IResultItem<R> r) {
        return new Ok<>(r);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <R> Ok<R> ok(Optional<R> r) {
        return new Ok<>(r);
    }

    public static <R> Ok<R> empty() {
        return new Ok<>(Optional.empty());
    }

    public <S> Ok<S> mapResult(Function<R, S> toMap) {
        return switch (this.t) {
            case IManyResultItem<R> s ->
                    Ok.ok(s.map(toMap));
            default -> {
                if (this.t.isPresent())
                    yield Ok.ok(toMap.apply(t.get()));

                yield Ok.empty();
            }

        };

    }

    public <S> Ok<S> flatMapResult(Function<R, Ok<S>> toMap) {
        return switch (this.t) {
            case IManyResultItem<R> s ->
                    Ok.ok(s.flatMap(st -> {
                        var mapped = toMap.apply(st);
                        return mapped.t;
                    }));
            default -> {
                if (this.t.isPresent())
                    yield toMap.apply(t.get());

                yield Ok.empty();
            }

        };
    }

    public <U> Ok<U> cast() {
        return switch (this.t) {
            case IManyResultItem<R> s ->
                    Ok.ok(s.map(r -> (U) r));
            default -> {
                if (t.isEmpty())
                    yield Ok.empty();
                try {
                    yield this.mapResult(s -> (U) s);
                } catch (ClassCastException c) {
                    yield Ok.empty();
                }
            }
        };
    }

    public R orElseRes(R orRes) {
        return this.t.orElse(orRes);
    }

    @Override
    public Stream<R> detachedStream() {
        return this.t.detachedStream();
    }


}
