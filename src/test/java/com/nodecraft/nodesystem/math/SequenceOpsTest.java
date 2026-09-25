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
        assertEquals(List.of(0.0d, 0.25d, 0.5d, 0.75d, 1.0d), SequenceOps.range(0.0d, 1.0d, 0.25d));
    }

    @Test
    void rangeDoesNotCrossEnd() {
        List<Double> values = SequenceOps.range(0.0d, 1.0d, 0.3d);
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
        assertEquals(List.of(1.0d, 0.75d, 0.5d, 0.25d, 0.0d), SequenceOps.range(1.0d, 0.0d, -0.25d));
    }

    @Test
    void rangeDirectionMismatchIsEmpty() {
        assertTrue(SequenceOps.range(0.0d, 1.0d, -1.0d).isEmpty());
        assertTrue(SequenceOps.range(1.0d, 0.0d, 1.0d).isEmpty());
    }

    @Test
    void rangeEqualEndpointsIsSingleton() {
        assertEquals(List.of(5.0d), SequenceOps.range(5.0d, 5.0d, 1.0d));
    }

    @Test
    void rangeZeroStepIsEmpty() {
        assertTrue(SequenceOps.range(0.0d, 10.0d, 0.0d).isEmpty());
        assertTrue(SequenceOps.range(2.0d, 10.0d, -0.0d).isEmpty());
    }

    @Test
    void tinyStepIsNotTreatedAsZero() {
        List<Double> values = SequenceOps.range(0.0d, 1.0e-10d, 5.0e-11d);
        assertFalse(values.isEmpty());
        assertTrue(values.size() <= GenerationLimits.MAX_LIST_ELEMENTS);
        for (Double value : values) {
            assertTrue(Double.isFinite(value));
        }
    }

    @Test
    void rangeStopsOnFloatStallWithoutHanging() {
        List<Double> values = SequenceOps.range(1.0e308d, Double.MAX_VALUE, 1.0d);
        assertFalse(values.isEmpty());
        assertTrue(values.size() <= GenerationLimits.MAX_LIST_ELEMENTS);
        for (Double value : values) {
            assertTrue(Double.isFinite(value));
        }
    }

    @Test
    void seriesBasicAndCountEdges() {
        assertEquals(List.of(0.0d, 2.0d, 4.0d, 6.0d), SequenceOps.series(0.0d, 2.0d, 4));
        assertTrue(SequenceOps.series(0.0d, 1.0d, 0).isEmpty());
        assertTrue(SequenceOps.series(0.0d, 1.0d, -3).isEmpty());
    }

    @Test
    void seriesClampsHugeCountAndRejectsNonFiniteInputs() {
        assertEquals(GenerationLimits.MAX_LIST_ELEMENTS, SequenceOps.series(0.0d, 1.0d, Integer.MAX_VALUE).size());
        assertTrue(SequenceOps.series(Double.NaN, 1.0d, 4).isEmpty());
        assertTrue(SequenceOps.series(0.0d, Double.POSITIVE_INFINITY, 4).isEmpty());
    }

    @Test
    void seriesStopsBeforeInfinity() {
        List<Double> values = SequenceOps.series(1.0e308d, 1.0e308d, 10);
        assertFalse(values.isEmpty());
        for (Double value : values) {
            assertTrue(Double.isFinite(value));
            assertFalse(Double.isInfinite(value));
        }
    }
}
