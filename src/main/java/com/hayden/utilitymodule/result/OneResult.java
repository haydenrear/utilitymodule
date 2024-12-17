package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.result.error.Err;
import com.hayden.utilitymodule.result.res_support.many.stream.StreamResult;
import com.hayden.utilitymodule.result.res_ty.IResultItem;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface OneResult<R, E> extends Result<R, E>, ManyResult<R, E> {

    default IResultItem<R> toResultItem() {
        return this.r().isMany()
                ? this.r().many()
               : this.r().single();
    }

    default Stream<R> stream() {
        return this.optional().stream();
    }

    default OneResult<R, E> one() {
        return this;
    }

    default R orElseGet(Supplier<R> o) {
        if (this.r().isPresent())
            return this.r().get();

        return o.get();
    }

    default R get() {
        return this.r().get();
    }

    default boolean isPresent() {
        return r().isPresent();
    }

    default Result<R, E> or(Supplier<Result<R, E>> s) {
        if (this.r().isPresent())
            return this;

        Result<R, E> teResult = s.get();
        return Result.from(teResult.r(), teResult.e().addError(this.e()));
    }
    default R orElseRes(R or) {
        if (this.r().isPresent())
            return this.r().get();

        return or;
    }

    default R orElseErrRes(Function<Err<E>, R> or) {
        if (this.r().isPresent())
            return this.r().get();

        return or.apply(this.e());
    }

    default Optional<R> toOptional() {
        return r().firstOptional();
    }

    default Optional<R> optional() {
        return r().firstOptional();
    }

    default Result<R, E> orError(Supplier<Err<E>> s) {
        if (e().isPresent())
            return this;

        return Result.from(this.r(), s.get());
    }

    @Override
    default ManyResult<R, E> many() {
        return new StreamResult<>(Stream.of(Result.from(this.r(), this.e())));
    }

    @Override
    default Result<R, E> hasAnyOr(Supplier<Result<R, E>> s) {
        return this.or(s);
    }

    @Override
    default Result<R, E> last(Consumer<Result<R, E>> last) {
        return this;
    }

    @Override
    default R hasFirstErrOrElseGet(Function<Err<E>, R> or) {
        if (this.e().isPresent()) {
            return this.r().get();
        }

        return or.apply(this.e());
    }

    @Override
    default R firstResOrElse(R or) {
        if (this.r().isPresent()) {
            return this.r().get();
        }

        return or;
    }

    @Override
    default Result<R, E> firstErrOr(Supplier<Err<E>> s) {
        if (this.e().isPresent()) {
            return this;
        }

        return Result.from(this.r(), s.get());
    }
}
