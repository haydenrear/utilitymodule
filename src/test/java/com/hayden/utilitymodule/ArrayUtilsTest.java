package com.hayden.utilitymodule;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArrayUtilsTest {

    @Test
    void toPrimitive() {
        double[][] doubles = Assertions.assertDoesNotThrow(() -> ArrayUtilUtilities.toPrimitive(new Double[][]{{1.0, 2.0}}));
        assertThat(doubles).isEqualTo(new double[][] {{1.0, 2.0}});
    }

}