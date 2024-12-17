package com.hayden.utilitymodule.result.res_support.many.stream.stream_cache;

import java.util.stream.Stream;

/**
 * Implodable side effects
 * @param <R>
 * @param <SELF>
 */
public interface CachableStream<R, SELF extends CachableStream<R, SELF>> {

    Stream<R> stream();

    SELF swap(Stream<R> toCache);

}
