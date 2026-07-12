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
    void clampPositiveCountEnforcesMinimumOne() {
        assertEquals(1, GenerationLimits.clampPositiveCount(0));
        assertEquals(1, GenerationLimits.clampPositiveCount(-10));
    }

    @Test
    void clampGridAxisCapsLargeValues() {
        assertEquals(GenerationLimits.MAX_GRID_AXIS, GenerationLimits.clampGridAxis(Integer.MAX_VALUE));
    }
}
