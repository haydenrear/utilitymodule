package com.hayden.utilitymodule.result.error;

import com.hayden.utilitymodule.result.res_support.one.ResultTy;
import com.hayden.utilitymodule.result.res_many.IManyResultItem;
import com.hayden.utilitymodule.result.res_many.ListResultItem;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.utils.Lists;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Data
public class Err<R> extends ResultTy<R> {

    public Err(Stream<R> r) {
        super(r);
    }

    public Err(IResultItem<R> r) {
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

    public static <R> Err<R> emptyStream() {
        return new Err<>(Stream.empty());
    }

    public static <R> Err<R> err(IResultItem<R> r) {
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

    public Err<R> addError(Err<R> e) {
        var list = this.toMutableList();
        e.forEach(list::add);
        return Err.err(new ListResultItem<>(list));
    }

    public Err<R> addError(R e) {
        return addError(Err.err(e));
    }

    public Err<R> addErrors(List<R> e) {
        return this.addError(new Err<>(new ListResultItem<>(e)));
    }

    public static <R> Err<R> empty() {
        return new Err<>(Optional.empty());
    }

    public static <R> Err<R> err(R r) {
        return new Err<>(Optional.ofNullable(r));
    }

    public <S> Err<S> mapErr(Function<R, S> toMap) {
        return switch(this.t) {
            case IManyResultItem<R> s ->
                    Err.err(s.map(toMap));
            default -> {
                if (this.t.isPresent())
                    yield Err.err(toMap.apply(t.get()));

                yield Err.empty();
            }

        };
    }

    public <S> Err<S> flatMapErr(Function<R, Err<S>> toMap) {
        return switch(this.t) {
            case IManyResultItem<R> s ->
                    Err.err(s.flatMap(st-> {
                        var mapped = toMap.apply(st);
                        return mapped.t;
                    }));
            default -> {
                if (this.t.isPresent())
                    yield toMap.apply(t.get());

                yield Err.empty();
            }

        };
    }

    public Err<R> filterErr(Predicate<R> b) {
        return switch(this.t) {
            case IManyResultItem<R> s ->
                    Err.err(s.filter(b));
            default -> {
                if (this.t.isPresent() && b.test(t.get())) {
                    yield this;
                }

                yield Err.empty();
            }

        };

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

    public Err<R> orErr(Supplier<Err<R>> orRes) {
        if (this.t.isPresent())
            return this;

        return orRes.get();
    }

}
