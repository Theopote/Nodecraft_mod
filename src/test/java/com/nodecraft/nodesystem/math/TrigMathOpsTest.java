package com.nodecraft.nodesystem.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrigMathOpsTest {

    @Test
    void sinCosUseDegrees() {
        assertEquals(1.0d, TrigMathOps.sin(90.0d).value(), 1.0e-12);
        assertEquals(1.0d, TrigMathOps.cos(0.0d).value(), 1.0e-12);
    }

    @Test
    void inverseTrigOutputsDegrees() {
        assertEquals(90.0d, TrigMathOps.asin(1.0d).value(), 1.0e-12);
        assertEquals(90.0d, TrigMathOps.atan2(1.0d, 0.0d).value(), 1.0e-12);
    }

    @Test
    void tanExactSingularityInvalidNearSingularityValid() {
        assertFalse(TrigMathOps.tan(90.0d).valid());
        assertFalse(TrigMathOps.tan(270.0d).valid());
        assertFalse(TrigMathOps.tan(-90.0d).valid());
        assertFalse(TrigMathOps.tan(450.0d).valid());
        assertFalse(TrigMathOps.tan(-270.0d).valid());
        assertTrue(Double.isNaN(TrigMathOps.tan(90.0d).value()));

        ScalarResult near = TrigMathOps.tan(89.999999d);
        assertTrue(near.valid());
        assertTrue(Double.isFinite(near.value()));
    }

    @Test
    void sinCosPeriodNormalization() {
        assertEquals(-1.0d, TrigMathOps.sin(-90.0d).value(), 1.0e-12);
        assertEquals(1.0d, TrigMathOps.cos(360.0d).value(), 1.0e-12);
        double large = 360.0d * 1_000_000.0d + 90.0d;
        assertEquals(1.0d, TrigMathOps.sin(large).value(), 1.0e-12);
    }

    @Test
    void hyperbolicOverflowIsInvalid() {
        assertFalse(TrigMathOps.sinh(1000.0d).valid());
        assertFalse(TrigMathOps.cosh(1000.0d).valid());
        assertTrue(Double.isNaN(TrigMathOps.sinh(1000.0d).value()));
    }

    @Test
    void tanhLargeInputStaysFinite() {
        ScalarResult positive = TrigMathOps.tanh(1000.0d);
        assertTrue(positive.valid());
        assertTrue(Double.isFinite(positive.value()));
        assertEquals(1.0d, positive.value(), 1.0e-6);

        ScalarResult negative = TrigMathOps.tanh(-1000.0d);
        assertTrue(negative.valid());
        assertEquals(-1.0d, negative.value(), 1.0e-6);
    }

    @Test
    void atan2RejectsNonFiniteInputs() {
        assertFalse(TrigMathOps.atan2(Double.NaN, 1.0d).valid());
        assertFalse(TrigMathOps.atan2(1.0d, Double.NaN).valid());
    }

    @Test
    void atan2ZeroVectorFollowsJava() {
        assertTrue(TrigMathOps.atan2(0.0d, 0.0d).valid());
        assertEquals(0.0d, TrigMathOps.atan2(0.0d, 0.0d).value(), 1.0e-12);

        assertTrue(TrigMathOps.atan2(-0.0d, -0.0d).valid());
        assertEquals(Math.toDegrees(Math.atan2(-0.0d, -0.0d)),
                TrigMathOps.atan2(-0.0d, -0.0d).value(), 1.0e-12);
    }

    @Test
    void asinRejectsOutOfDomain() {
        assertFalse(TrigMathOps.asin(1.0000000002d).valid());
    }

    @Test
    void acosRejectsOutOfDomain() {
        assertFalse(TrigMathOps.acos(1.0000000001d).valid());
    }
}
