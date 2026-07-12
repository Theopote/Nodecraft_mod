package com.nodecraft.nodesystem.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenerationLimitsTest {

    @Test
    void clampNonNegativeCountCapsHugeValues() {
        assertEquals(0, GenerationLimits.clampNonNegativeCount(-5));
        assertEquals(GenerationLimits.MAX_LIST_ELEMENTS, GenerationLimits.clampNonNegativeCount(Integer.MAX_VALUE));
    }

    @Test
    void clampRepeatCountRespectsExpansionFactor() {
        assertEquals(1024, GenerationLimits.clampRepeatCount(10_000, 1024));
        assertEquals(0, GenerationLimits.clampRepeatCount(10, GenerationLimits.MAX_LIST_ELEMENTS + 1));
    }
}
