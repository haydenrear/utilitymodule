package com.hayden.utilitymodule.result.res_many;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface IStreamResultItem<R> extends IManyResultItem<R> {

    Logger log = LoggerFactory.getLogger(IStreamResultItem.class);

    default boolean isPresent() {
        return !isEmpty();
    }

    default boolean isStream() {
        return true;
    }
}
