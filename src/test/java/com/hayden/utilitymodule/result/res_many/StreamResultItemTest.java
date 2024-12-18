package com.hayden.utilitymodule.result.res_many;

import com.hayden.utilitymodule.result.res_support.many.stream.StreamResultOptions;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class StreamResultItemTest {

    @Test
    public void testOkStreamResultItem() {
        var s = Stream.of("one", "two");
        StreamResultItem<String> stringStreamResultItem = new StreamResultItem<>(s);
        var sWrapper = new StreamResultItem.ResultTyStreamWrapper<>(StreamResultOptions.builder().build(),
                s, stringStreamResultItem);

        List<String> assertList = new ArrayList<>();
        sWrapper.cacheResultsIfNotCachedWithList(assertList::add);

        assertThat(assertList).containsExactly("one", "two");

        var firstFound = sWrapper.first();

        assertNotNull(firstFound);
        assertEquals("one", firstFound);
        assertTrue(sWrapper.isAnyNonNull());
        assertFalse(sWrapper.isCompletelyEmpty());
    }

    @Test
    public void testOkStreamResultItemEmpty() {
        var s = Stream.<String>empty();
        StreamResultItem<String> stringStreamResultItem = new StreamResultItem<>(s);
        var sWrapper = new StreamResultItem.ResultTyStreamWrapper<>(StreamResultOptions.builder().build(),
                s, stringStreamResultItem);

        List<String> assertList = new ArrayList<>();
        sWrapper.cacheResultsIfNotCachedWithList(assertList::add);

        assertThat(assertList).isEmpty();

        var firstFound = sWrapper.first();

        assertNull(firstFound);
        assertFalse(sWrapper.isAnyNonNull());
        assertTrue(sWrapper.isCompletelyEmpty());
    }

}