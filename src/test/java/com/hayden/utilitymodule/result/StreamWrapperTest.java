package com.hayden.utilitymodule.result;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StreamWrapperTest {


    private static class TestResultStreamWrapper<R, E> extends StreamWrapper<StreamResult<R, E>, Result<R, E>> {

        public TestResultStreamWrapper(StreamResultOptions options, Stream<Result<R, E>> underlying) {
            super(options, underlying, ResultStreamCacheOperation.class);
        }
    }

    @Test
    public void doTest() {
        var t = new TestResultStreamWrapper<>(StreamResultOptions.builder().cache(true).build(), Stream.of(Result.ok("")));
        t.cacheResults(new StreamResult<>(Stream.of(Result.ok(""))));
        assertNotNull(t.get(StreamWrapper.IsCompletelyEmpty.class));
    }

}