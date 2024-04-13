package com.hayden.utilitymodule;

import lombok.AllArgsConstructor;
import org.junit.jupiter.api.Test;

public class TestTest {

    interface Five {}

    class One implements Five {

    }

    @AllArgsConstructor
    class Two<T extends Five> {
        T t;
    }

    @Test
    public void testCasting() {
        Two<? extends Five>  five = new Two<>(new One());
        Two<One> o = (Two<One>) five;
        System.out.println(o);
    }

}
