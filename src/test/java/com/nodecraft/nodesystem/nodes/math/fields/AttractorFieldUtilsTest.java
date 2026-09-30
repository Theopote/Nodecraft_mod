package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.math.FieldMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttractorFieldUtilsTest {

    @Test
    void falloffUsesResolvedParametersWithoutHiddenFloors() {
        double atMinimumRadius = AttractorFieldUtils.falloff(
                1.0d,
                FieldMath.MIN_ATTRACTOR_FALLOFF_RADIUS,
                2.0d,
                AttractorFieldUtils.FalloffMode.INVERSE
        );
        double atDefaultRadius = AttractorFieldUtils.falloff(
                1.0d,
                8.0d,
                2.0d,
                AttractorFieldUtils.FalloffMode.INVERSE
        );
        assertTrue(Double.isFinite(atMinimumRadius));
        assertTrue(Double.isFinite(atDefaultRadius));
        assertNotEquals(atMinimumRadius, atDefaultRadius, 1.0e-12);
    }

    @Test
    void falloffReturnsFiniteForExtremeExponent() {
        double weight = AttractorFieldUtils.falloff(
                2.0d,
                8.0d,
                512.0d,
                AttractorFieldUtils.FalloffMode.INVERSE
        );
        assertTrue(Double.isFinite(weight));
    }
}
