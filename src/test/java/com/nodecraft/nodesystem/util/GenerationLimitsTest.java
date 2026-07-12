package com.nodecraft.nodesystem.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void clampGridCountsScalesDownWhenProductWouldOverflow() {
        GenerationLimits.GridAxisCounts counts = GenerationLimits.clampGridCounts(
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            1
        );

        long outputSize = (long) (counts.xCount() + 1)
            * (counts.yCount() + 1)
            * (counts.zCount() + 1);
        assertTrue(outputSize <= GenerationLimits.MAX_LIST_ELEMENTS);
        assertTrue(counts.xCount() <= GenerationLimits.MAX_GRID_AXIS);
        assertTrue(counts.yCount() <= GenerationLimits.MAX_GRID_AXIS);
        assertTrue(counts.zCount() <= GenerationLimits.MAX_GRID_AXIS);
    }

    @Test
    void clampSpacingInstanceCountCapsTinySpacing() {
        assertEquals(GenerationLimits.MAX_LIST_ELEMENTS,
            GenerationLimits.clampSpacingInstanceCount(1.0e12d, 1.0e-6d));
    }

    @Test
    void clampLoopIterationsCapsHugeValues() {
        assertEquals(GenerationLimits.MAX_LOOP_ITERATIONS, GenerationLimits.clampLoopIterations(Integer.MAX_VALUE));
        assertEquals(1, GenerationLimits.clampLoopIterations(0));
        assertEquals(1, GenerationLimits.clampLoopIterations(-5));
    }

    @Test
    void clampAttemptBudgetCapsHugeValues() {
        assertTrue(GenerationLimits.clampAttemptBudget(Integer.MAX_VALUE, Integer.MAX_VALUE)
            <= GenerationLimits.MAX_LIST_ELEMENTS * 100L);
    }
}
