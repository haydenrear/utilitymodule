package com.hayden.utilitymodule.result.res_many;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public interface IStreamResultTy<R> extends IManyResultTy<R> {

    Logger log = LoggerFactory.getLogger(IStreamResultTy.class);

    default boolean isPresent() {
        return !isEmpty();
    }

    default boolean isStream() {
        return true;
    }
}
