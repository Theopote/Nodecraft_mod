package com.nodecraft.nodesystem.math;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericListReductionTest {

    @Test
    void sumOverflowFailsClosed() {
        List<Double> values = List.of(1e308, 1e308);
        assertFalse(NumericListReduction.sum(values).valid());
    }

    @Test
    void productOverflowFailsClosed() {
        List<Double> values = List.of(1e200, 1e200);
        assertFalse(NumericListReduction.product(values).valid());
    }

    @Test
    void averageLargeEqualValuesSucceeds() {
        ScalarResult result = NumericListReduction.average(List.of(1e308, 1e308));
        assertTrue(result.valid());
        assertEquals(1e308, result.value());
    }

    @Test
    void averageOppositeExtremesCurrentlyFailsClosed() {
        // Documented V128 limitation: online mean via (x_i - mean) overflows for [-1e308, 1e308]
        // even though the mathematical mean is 0. Improve only with an intentional algorithm change.
        ScalarResult result = NumericListReduction.average(List.of(-1e308, 1e308));
        assertFalse(result.valid());
    }

    @Test
    void medianEqualLargeValuesAvoidsMidpointOverflow() {
        List<Double> sorted = List.of(1e308, 1e308);
        ScalarResult result = NumericListReduction.medianSorted(sorted);
        assertTrue(result.valid());
        assertEquals(1e308, result.value());
    }

    @Test
    void parseRejectsNonFiniteElements() {
        NumericListReduction.ParseResult parsed =
                NumericListReduction.parseFiniteNumbers(List.of(1.0, Double.NaN));
        assertFalse(parsed.valid());
    }

    @Test
    void parseRejectsEmptyList() {
        assertFalse(NumericListReduction.parseFiniteNumbers(List.of()).valid());
    }
}
