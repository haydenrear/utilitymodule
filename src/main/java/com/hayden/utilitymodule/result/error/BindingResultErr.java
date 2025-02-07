package com.hayden.utilitymodule.result.error;

import com.hayden.utilitymodule.result.res_support.one.ResultTy;
import com.hayden.utilitymodule.result.res_ty.IResultItem;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

}
