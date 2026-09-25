package com.nodecraft.nodesystem.math;

import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScalarMathOpsTest {

    @Test
    void divAcceptsTinyDivisorAndRejectsExactZero() {
        assertTrue(ScalarMathOps.div(1.0d, 1.0e-11d).valid());
        assertFalse(ScalarMathOps.div(1.0d, 0.0d).valid());
        assertTrue(Double.isNaN(ScalarMathOps.div(1.0d, 0.0d).value()));
    }

    @Test
    void roundUsesRintNotLongSaturation() {
        assertEquals(1.0e100d, ScalarMathOps.round(1.0e100d).value(), 0.0d);
        assertEquals(2.0d, ScalarMathOps.round(2.5d).value(), 0.0d);
        assertEquals(2.0d, ScalarMathOps.round(1.5d).value(), 0.0d);
    }

    @Test
    void remapRejectsDegenerateSourceExactly() {
        NumericRangeData source = new NumericRangeData(1.0d, 1.0d);
        NumericRangeData target = new NumericRangeData(0.0d, 10.0d);
        assertFalse(ScalarMathOps.remap(0.5d, source, target, true).valid());
    }

    @Test
    void lerpOverflowIsInvalid() {
        assertFalse(ScalarMathOps.lerp(1.0e308d, -1.0e308d, 1.0d).valid());
    }
}
