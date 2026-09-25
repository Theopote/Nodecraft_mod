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
        assertTrue(Double.isNaN(TrigMathOps.tan(90.0d).value()));

        ScalarResult near = TrigMathOps.tan(89.999999d);
        assertTrue(near.valid());
        assertTrue(Double.isFinite(near.value()));
    }

    @Test
    void hyperbolicOverflowIsInvalid() {
        assertFalse(TrigMathOps.sinh(1000.0d).valid());
        assertFalse(TrigMathOps.cosh(1000.0d).valid());
        assertTrue(Double.isNaN(TrigMathOps.sinh(1000.0d).value()));
    }

    @Test
    void tanhLargeInputStaysFinite() {
        ScalarResult result = TrigMathOps.tanh(1000.0d);
        assertTrue(result.valid());
        assertTrue(Double.isFinite(result.value()));
    }

    @Test
    void atan2RejectsNonFiniteInputs() {
        assertFalse(TrigMathOps.atan2(Double.NaN, 1.0d).valid());
        assertFalse(TrigMathOps.atan2(1.0d, Double.NaN).valid());
    }

    @Test
    void asinRejectsOutOfDomain() {
        assertFalse(TrigMathOps.asin(1.0000000002d).valid());
    }
}
