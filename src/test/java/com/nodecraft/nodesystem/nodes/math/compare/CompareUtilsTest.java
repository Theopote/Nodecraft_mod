package com.nodecraft.nodesystem.nodes.math.compare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompareUtilsTest {

    @Test
    void numericEqualIsExact() {
        assertTrue(CompareUtils.numericEqual(1.0d, 1.0d));
        assertTrue(CompareUtils.numericEqual(1, 1.0d));
        assertFalse(CompareUtils.numericEqual(1.0d, 1.00000000001d));
    }

    @Test
    void numericLessIsExactWithoutEpsilon() {
        assertTrue(CompareUtils.numericLess(0.0d, 5.0e-11d));
        assertFalse(CompareUtils.numericLess(0.0d, 0.0d));
    }

    @Test
    void genericEqualRejectsCrossTypeCoercion() {
        assertFalse(CompareUtils.genericEqual("1", 1));
        assertFalse(CompareUtils.genericEqual("01", 1));
        assertFalse(CompareUtils.genericEqual("true", Boolean.TRUE));
        assertTrue(CompareUtils.genericEqual("abc", "abc"));
    }

    @Test
    void numericEqualHandlesSignedZeroAndNaN() {
        assertTrue(CompareUtils.numericEqual(+0.0d, -0.0d));
        assertFalse(CompareUtils.numericEqual(Double.NaN, Double.NaN));
    }

    @Test
    void nonFiniteNumericComparisonsFailClosed() {
        assertFalse(CompareUtils.numericLess(Double.NaN, 1.0d));
        assertFalse(CompareUtils.numericGreater(1.0d, Double.NaN));
        assertFalse(CompareUtils.numericLessOrEqual(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertFalse(CompareUtils.genericEqual(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertFalse(CompareUtils.numericEqual(Double.NaN, 1.0d));
    }

    @Test
    void nullEquality() {
        assertTrue(CompareUtils.genericEqual(null, null));
        assertFalse(CompareUtils.genericEqual(null, 1));
        assertFalse(CompareUtils.genericEqual(1, null));
    }
}
