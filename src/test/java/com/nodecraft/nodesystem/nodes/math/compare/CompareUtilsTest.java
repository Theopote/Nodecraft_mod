package com.nodecraft.nodesystem.nodes.math.compare;

import com.nodecraft.nodesystem.math.ComparisonResult;
import com.nodecraft.nodesystem.util.NumericComparison;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompareUtilsTest {

    @Test
    void numericComparisonIsExactForIntegersAndDoubles() {
        assertTrue(NumericComparison.numbersEqual(1, 1.0d));
        assertFalse(NumericComparison.numbersEqual(1.0d, 1.00000000001d));
        assertTrue(NumericComparison.numbersEqual(+0.0d, -0.0d));
    }

    @Test
    void largeIntegersDoNotCollapseViaDoubleRounding() {
        long a = 9007199254740992L;
        long b = 9007199254740993L;
        assertFalse(NumericComparison.numbersEqual(a, b));
    }

    @Test
    void compareLessIsExactWithoutEpsilon() {
        ComparisonResult result = CompareUtils.compareLess(0.0d, 5.0e-11d, true, true);
        assertTrue(result.valid());
        assertTrue(result.result());
        result = CompareUtils.compareLess(0.0d, 0.0d, true, true);
        assertTrue(result.valid());
        assertFalse(result.result());
    }

    @Test
    void genericEqualRejectsCrossTypeCoercion() {
        assertFalse(resultOf(CompareUtils.compareEqual("1", 1, true, true)));
        assertFalse(resultOf(CompareUtils.compareEqual("01", 1, true, true)));
        assertFalse(resultOf(CompareUtils.compareEqual("true", Boolean.TRUE, true, true)));
        assertTrue(resultOf(CompareUtils.compareEqual("abc", "abc", true, true)));
    }

    @Test
    void nonFiniteNumericComparisonsFailClosed() {
        assertFalse(resultOf(CompareUtils.compareLess(Double.NaN, 1.0d, true, true)));
        assertFalse(validOf(CompareUtils.compareLess(Double.NaN, 1.0d, true, true)));
        assertFalse(resultOf(CompareUtils.compareGreater(1.0d, Double.NaN, true, true)));
        assertFalse(validOf(CompareUtils.compareGreater(1.0d, Double.NaN, true, true)));
        assertFalse(resultOf(CompareUtils.compareLessOrEqual(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, true, true)));
        assertFalse(validOf(CompareUtils.compareLessOrEqual(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, true, true)));
        assertFalse(resultOf(CompareUtils.compareEqual(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, true, true)));
        assertTrue(validOf(CompareUtils.compareEqual(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, true, true)));
        assertFalse(resultOf(CompareUtils.compareEqual(Double.NaN, Double.NaN, true, true)));
        assertTrue(validOf(CompareUtils.compareEqual(Double.NaN, Double.NaN, true, true)));
    }

    @Test
    void notEqualsRejectsBothNonFiniteOperands() {
        ComparisonResult result = CompareUtils.compareNotEqual(Double.NaN, Double.NaN, true, true);
        assertFalse(result.valid());
        assertFalse(result.result());

        result = CompareUtils.compareNotEqual(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, true, true);
        assertFalse(result.valid());
        assertFalse(result.result());

        result = CompareUtils.compareNotEqual(Double.NaN, 1.0d, true, true);
        assertTrue(result.valid());
        assertTrue(result.result());
    }

    @Test
    void nullEqualityWhenDriven() {
        assertTrue(resultOf(CompareUtils.compareEqual(null, null, true, true)));
        assertTrue(validOf(CompareUtils.compareEqual(null, null, true, true)));
        assertFalse(resultOf(CompareUtils.compareEqual(null, 1, true, true)));
        assertFalse(resultOf(CompareUtils.compareEqual(1, null, true, true)));
    }

    @Test
    void bothUndrivenIsInvalid() {
        ComparisonResult result = CompareUtils.compareEqual(null, null, false, false);
        assertFalse(result.valid());
        assertFalse(result.result());

        result = CompareUtils.compareNotEqual(null, null, false, false);
        assertFalse(result.valid());
        assertFalse(result.result());

        result = CompareUtils.compareLess(null, null, false, false);
        assertFalse(result.valid());
        assertFalse(result.result());
    }

    @Test
    void orderingRejectsNonDoubleNumbers() {
        ComparisonResult result = CompareUtils.compareLess(1, 2.0d, true, true);
        assertFalse(result.valid());
        assertFalse(result.result());
    }

    private static boolean resultOf(ComparisonResult result) {
        return result.result();
    }

    private static boolean validOf(ComparisonResult result) {
        return result.valid();
    }
}
