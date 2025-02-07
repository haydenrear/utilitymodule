package com.hayden.utilitymodule.result.error;

import com.hayden.utilitymodule.result.res_many.ListResultItem;
import com.hayden.utilitymodule.result.res_support.one.ResultTy;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.MapBindingResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Data
public class BindingResultErr extends ResultTy<BindingResult> implements Err<BindingResult> {

    public BindingResultErr(BindingResult r) {
        super(r);
    }

    @Override
    public IResultItem<BindingResult> t() {
        return this.t;
    }

   public  Err<BindingResult> addErrors(List<BindingResult> e) {
        Err<BindingResult> res = this;
        for (var r : e) {
            res = res.addError(r);
        }

        return res;
    }

    @Override
    public Err<BindingResult> addError(Err<BindingResult> e) {
        if (e.isPresent()) {
            var binder = this.t.orElse(new MapBindingResult(new HashMap<>(), "BindingError"));
            e.ifPresent(br -> br.getAllErrors().forEach(binder::addError));
            return Err.err(binder);
        }

        return this;
    }
}
