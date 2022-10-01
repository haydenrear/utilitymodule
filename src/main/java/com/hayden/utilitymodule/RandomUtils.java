package com.hayden.utilitymodule;

import java.util.Map;
import java.util.Random;

public class RandomUtils {

    enum RandomFound {
        Random(new Random());

        Random random;

        RandomFound(Random random) {
            this.random = random;
        }
    }

    public static int numberBetween(int starting, int ending) {
        return RandomFound.Random.random.ints(starting, ending)
                .findFirst()
                .orElseThrow();
    }
}
