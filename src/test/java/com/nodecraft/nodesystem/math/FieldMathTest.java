package com.nodecraft.nodesystem.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldMathTest {

    @Test
    void resolveFiniteUsesFallbackForNonFinite() {
        assertEquals(3.0d, FieldMath.resolveFinite(3.0d, 5.0d), 0.0d);
        assertEquals(5.0d, FieldMath.resolveFinite(Double.NaN, 5.0d), 0.0d);
        assertEquals(5.0d, FieldMath.resolveFinite("x", 5.0d), 0.0d);
    }

    @Test
    void resolvePositiveRejectsNonPositive() {
        assertEquals(10.0d, FieldMath.resolvePositive(10.0d, 8.0d), 0.0d);
        assertEquals(8.0d, FieldMath.resolvePositive(0.0d, 8.0d), 0.0d);
        assertEquals(8.0d, FieldMath.resolvePositive(-1.0d, 8.0d), 0.0d);
        assertEquals(8.0d, FieldMath.resolvePositive(Double.NaN, 8.0d), 0.0d);
    }

    @Test
    void resolveAttractorRadiusRejectsBelowDocumentedMinimum() {
        assertEquals(8.0d, FieldMath.resolveAttractorRadius(1.0e-10, 8.0d), 0.0d);
        assertEquals(1.0e-9d, FieldMath.resolveAttractorRadius(1.0e-9, 8.0d), 0.0d);
        assertEquals(1.0e-9d, FieldMath.resolveAttractorRadius(Double.NaN, 1.0e-10), 0.0d);
    }

    @Test
    void resolveAttractorExponentRejectsBelowDocumentedMinimum() {
        assertEquals(2.0d, FieldMath.resolveAttractorExponent(1.0e-4, 2.0d), 0.0d);
        assertEquals(0.001d, FieldMath.resolveAttractorExponent(0.001d, 2.0d), 0.0d);
        assertEquals(0.001d, FieldMath.resolveAttractorExponent(Double.NaN, 1.0e-4), 0.0d);
    }

    @Test
    void combineScalarsDivByZeroIsNaN() {
        assertTrue(Double.isNaN(FieldMath.combineScalars(1.0d, 0.0d, FieldMath.ScalarCombineOp.DIV)));
    }

    @Test
    void combineScalarsAddMatchesScalarMathOps() {
        assertEquals(3.0d, FieldMath.combineScalars(1.0d, 2.0d, FieldMath.ScalarCombineOp.ADD), 0.0d);
    }
}
