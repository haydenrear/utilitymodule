package com.hayden.utilitymodule.result.res_many;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Stream;

public interface IStreamResultTy<R> extends IManyResultTy<R> {

    Logger log = LoggerFactory.getLogger(IStreamResultTy.class);

    void swap(Stream<R> toSwap);

    @Override
    default boolean isEmpty() {
        log.warn("""
               Calling a terminating operation isEmpty on StreamResult. Will swap stream with new stream, but could result in many operations as every time it's called
               it has to perform a toList() to save the stream...
               """);
        List<R> list = this.stream()
                .toList();
        var isEmpty = list.isEmpty();
        swap(list.stream());
        return isEmpty;
    }

    @Override
    default boolean isPresent() {
        return !isEmpty();
    }

    default boolean isStream() {
        return true;
    }
}
