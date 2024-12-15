package com.hayden.utilitymodule.result;

import java.util.stream.Stream;

public interface CachableStream<R, SELF extends CachableStream<R, SELF>> {

    Stream<R> stream();

    SELF swap(Stream<R> toCache);


}
