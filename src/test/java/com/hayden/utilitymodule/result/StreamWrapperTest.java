package com.hayden.utilitymodule.result;

import com.hayden.utilitymodule.result.stream_cache.CachingOperations;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StreamWrapperTest {


    private static class TestResultStreamWrapper<R, E> extends StreamWrapper<StreamResult<R, E>, Result<R, E>> {

        public TestResultStreamWrapper(StreamResultOptions options, Stream<Result<R, E>> underlying, StreamResult<R, E> res) {
            super(options, underlying, CachingOperations.ResultStreamCacheOperation.class, res);
        }
    }

    @Test
    public void doTest() {
        Stream<Result<String, Object>> ok1 = Stream.of(Result.ok(""));
        var ok = Result.from(ok1);
        var t = new TestResultStreamWrapper<>(StreamResultOptions.builder().build(), ok1, (StreamResult<String, Object>) ok);
        t.forEach(System.out::println);
        t.cacheResultsIfNotCached();
        assertNotNull(t.get(CachingOperations.IsCompletelyEmpty.class));
    }

}