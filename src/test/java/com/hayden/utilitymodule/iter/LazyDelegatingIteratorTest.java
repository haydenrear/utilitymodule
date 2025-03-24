package com.hayden.utilitymodule.iter;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
class LazyDelegatingIteratorTest {

    @Test
    public void testLazyDelegatingIterator() {
        LazyDelegatingIterator<Integer> iterator = new LazyDelegatingIterator<>(
                IntStream.range(0, 10).boxed().map(i -> IntStream.range(0, i).boxed().toList().iterator())
                        .map(LazyDelegatingIterator::new)
                        .toList(),
                IntStream.range(0, 10).boxed().toList().iterator());

        int count = 0;
        while(iterator.hasNext()) {
            var next = Assertions.assertDoesNotThrow(iterator::next);
            log.info("{}", next);
            count += 1;
        }

        Assertions.assertEquals(1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10, count);

        var nIterator = new LazyDelegatingIterator<Integer>(
                new ArrayList<>(),
                List.<Integer>of().iterator());
        count = 0;
        while (nIterator.hasNext()) {
            count += 1;
        }

        Assertions.assertEquals(0, count);
    }

}