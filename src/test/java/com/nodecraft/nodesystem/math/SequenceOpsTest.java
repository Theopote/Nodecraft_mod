package com.nodecraft.nodesystem.math;

import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequenceOpsTest {

    @Test
    void rangeIncludesExactEndpointWhenStepDividesEvenly() {
        SequenceResult result = SequenceOps.range(0.0d, 1.0d, 0.25d);
        assertTrue(result.valid());
        assertEquals(List.of(0.0d, 0.25d, 0.5d, 0.75d, 1.0d), result.values());
    }

    @Test
    void rangeDoesNotCrossEnd() {
        SequenceResult result = SequenceOps.range(0.0d, 1.0d, 0.3d);
        assertTrue(result.valid());
        List<Double> values = result.values();
        assertEquals(4, values.size());
        assertEquals(0.0d, values.get(0), 0.0d);
        assertEquals(0.3d, values.get(1), 0.0d);
        assertEquals(0.6d, values.get(2), 0.0d);
        assertEquals(0.0d + 3.0d * 0.3d, values.get(3), 0.0d);
        assertTrue(values.get(3) <= 1.0d);
        assertTrue(values.stream().noneMatch(v -> v > 1.0d));
    }

    @Test
    void rangeDescending() {
        SequenceResult result = SequenceOps.range(1.0d, 0.0d, -0.25d);
        assertTrue(result.valid());
        assertEquals(List.of(1.0d, 0.75d, 0.5d, 0.25d, 0.0d), result.values());
    }

    @Test
    void rangeDirectionMismatchIsEmpty() {
        assertTrue(SequenceOps.range(0.0d, 1.0d, -1.0d).values().isEmpty());
        assertTrue(SequenceOps.range(1.0d, 0.0d, 1.0d).values().isEmpty());
        assertTrue(SequenceOps.range(0.0d, 1.0d, -1.0d).valid());
    }

    @Test
    void rangeEqualEndpointsIsSingleton() {
        SequenceResult result = SequenceOps.range(5.0d, 5.0d, 1.0d);
        assertTrue(result.valid());
        assertEquals(List.of(5.0d), result.values());
    }

    @Test
    void rangeZeroStepIsEmpty() {
        assertTrue(SequenceOps.range(0.0d, 10.0d, 0.0d).values().isEmpty());
        assertTrue(SequenceOps.range(2.0d, 10.0d, -0.0d).values().isEmpty());
    }

    @Test
    void tinyStepIsNotTreatedAsZero() {
        SequenceResult result = SequenceOps.range(0.0d, 1.0e-10d, 5.0e-11d);
        assertTrue(result.valid());
        assertFalse(result.values().isEmpty());
        assertTrue(result.values().size() <= GenerationLimits.MAX_LIST_ELEMENTS);
        for (Double value : result.values()) {
            assertTrue(Double.isFinite(value));
        }
    }

    @Test
    void rangeFloatStallFailsClosed() {
        SequenceResult result = SequenceOps.range(1.0e308d, Double.MAX_VALUE, 1.0d);
        assertFalse(result.valid());
        assertTrue(result.values().isEmpty());
        assertEquals(SequenceOps.ERROR_FLOAT_PRECISION_STALL, result.error());
    }

    @Test
    void seriesBasicAndCountEdges() {
        SequenceResult result = SequenceOps.series(0.0d, 2.0d, 4);
        assertTrue(result.valid());
        assertEquals(List.of(0.0d, 2.0d, 4.0d, 6.0d), result.values());
        assertTrue(SequenceOps.series(0.0d, 1.0d, 0).values().isEmpty());
        assertTrue(SequenceOps.series(0.0d, 1.0d, -3).values().isEmpty());
    }

    @Test
    void seriesClampsHugeCountAndRejectsNonFiniteInputs() {
        SequenceResult huge = SequenceOps.series(0.0d, 1.0d, Integer.MAX_VALUE);
        assertTrue(huge.valid());
        assertEquals(GenerationLimits.MAX_LIST_ELEMENTS, huge.values().size());
        assertTrue(SequenceOps.series(Double.NaN, 1.0d, 4).values().isEmpty());
        assertTrue(SequenceOps.series(0.0d, Double.POSITIVE_INFINITY, 4).values().isEmpty());
    }

    @Test
    void seriesOverflowFailsClosed() {
        SequenceResult result = SequenceOps.series(1.0e308d, 1.0e308d, 10);
        assertFalse(result.valid());
        assertTrue(result.values().isEmpty());
        assertEquals(SequenceOps.ERROR_NON_FINITE_VALUE, result.error());
    }
}
