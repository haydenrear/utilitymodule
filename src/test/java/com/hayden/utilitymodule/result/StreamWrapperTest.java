package com.hayden.utilitymodule.result;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StreamWrapperTest {


    @Test
    public void doTest() {
        Stream<Result<String, Object>> ok1 = Stream.of(Result.ok(""));
        StreamResult<String, Object> ok = (StreamResult<String, Object>) Result.from(ok1);
        var t = new StreamResult.StreamResultStreamWrapper<>(StreamResultOptions.builder().build(), ok1, ok);
        // terminal op
        t.forEach(System.out::println);

        assertFalse(t.isCompletelyEmpty(ok));
        assertTrue(t.isAnyNonNull(ok));
        assertTrue(t.hasAnyResult(ok));
        assertFalse(t.hasAnyError(ok));

        var okFound = t.getOk();

        assertNotNull(okFound);
        assertEquals("", okFound.get());
    }

}