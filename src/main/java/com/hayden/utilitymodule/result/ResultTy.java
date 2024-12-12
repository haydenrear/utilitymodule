package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.result.async.FluxResult;
import com.hayden.utilitymodule.result.async.MonoResult;
import com.hayden.utilitymodule.result.res_ty.ClosableResult;
import com.hayden.utilitymodule.result.res_ty.IResultTy;
import com.hayden.utilitymodule.result.res_ty.ResultTyResult;
import com.hayden.utilitymodule.result.res_ty.StreamResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Shared for Ok and Err
 * @param <U>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class ResultTy<U> {

    @Delegate
    protected IResultTy<U> t;

    public void set(U u) {
        this.t = from(u);
    }

    public ResultTy(Stream<U> t) {
        this.t = new StreamResult<>(t);
    }

    public ResultTy(Mono<U> t) {
        this.t = new MonoResult<>(t);
    }

    public ResultTy(Flux<U> t) {
        this.t = new FluxResult<>(t);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public ResultTy(Optional<U> t) {
        if (t.isEmpty()) {
            this.t = new ResultTyResult<>(Optional.empty());
        } else {
            var to = t.get();

            if (to instanceof AutoCloseable a) {
                this.t = (IResultTy<U>) new ClosableResult<>(Optional.of(a));
            } else {
                this.t = new ResultTyResult<>(Optional.of(to));
            }

        }
    }

    public ResultTy(U t) {
        if (t instanceof AutoCloseable a) {
            this.t = (IResultTy<U>) new ClosableResult<>(Optional.of(a));
        } else {
            this.t = new ResultTyResult<>(Optional.ofNullable(t));
        }
    }

}
