package com.nodecraft.nodesystem.math;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomOpsTest {

    @Test
    void missingSeedEqualsZero() {
        assertEquals(0, RandomOps.resolveSeed(null));
        assertEquals(0, RandomOps.resolveSeed("1"));
        assertEquals(0, RandomOps.resolveSeed(1.0d));
        assertEquals(0, RandomOps.resolveSeed(1L));
        assertEquals(7, RandomOps.resolveSeed(7));
        assertEquals(0, RandomOps.resolveSeed(0));
    }

    @Test
    void resolveCountIntegerOnlyThenClamp() {
        assertEquals(3, RandomOps.resolveCount(3, 10));
        assertEquals(10, RandomOps.resolveCount(1.9d, 10));
        assertEquals(10, RandomOps.resolveCount("5", 10));
        assertEquals(0, RandomOps.resolveCount(-1, 10));
    }

    @Test
    void seedZeroIsDeterministic() {
        Random a = RandomOps.rng(0);
        Random b = RandomOps.rng(0);
        assertEquals(a.nextDouble(), b.nextDouble(), 0.0d);
        assertEquals(a.nextDouble(), b.nextDouble(), 0.0d);
    }

    @Test
    void sampleDoubleFiniteOnNormalDomain() {
        Random random = RandomOps.rng(42);
        for (int i = 0; i < 100; i++) {
            double v = RandomOps.sampleDouble(0.0d, 1.0d, random);
            assertTrue(Double.isFinite(v));
            assertTrue(v >= 0.0d && v <= 1.0d);
        }
    }

    @Test
    void sampleDoubleNaNOnNonFiniteOrOverflowSpan() {
        Random random = RandomOps.rng(1);
        assertTrue(Double.isNaN(RandomOps.sampleDouble(Double.NaN, 1.0d, random)));
        assertTrue(Double.isNaN(RandomOps.sampleDouble(0.0d, Double.POSITIVE_INFINITY, random)));
        assertTrue(Double.isNaN(RandomOps.sampleDouble(-Double.MAX_VALUE, Double.MAX_VALUE, random)));
    }

    @Test
    void sampleDoubleEqualBoundsReturnsBound() {
        assertEquals(3.5d, RandomOps.sampleDouble(3.5d, 3.5d, RandomOps.rng(0)), 0.0d);
    }

    @Test
    void sampleVectorUsesJoml() {
        Vector3d result = RandomOps.sampleVector(
                new Vector3d(0, 0, 0),
                new Vector3d(1, 1, 1),
                RandomOps.rng(0)
        );
        assertTrue(Double.isFinite(result.x));
        assertTrue(Double.isFinite(result.y));
        assertTrue(Double.isFinite(result.z));
    }

    @Test
    void valueNoiseDeterministicAndCoherent() {
        double a = RandomOps.valueNoise3(1.0d, 2.0d, 3.0d, 0);
        double b = RandomOps.valueNoise3(1.0d, 2.0d, 3.0d, 0);
        assertEquals(a, b, 0.0d);
        assertTrue(Double.isFinite(a));
        assertTrue(a >= -1.0d && a <= 1.0d);

        double nearby = RandomOps.valueNoise3(1.001d, 2.0d, 3.0d, 0);
        assertTrue(Double.isFinite(nearby));
        // Coherent: small step should not jump across most of the [-1,1] range
        assertTrue(Math.abs(a - nearby) < 0.5d,
                "nearby samples should be continuous, delta=" + Math.abs(a - nearby));
    }

    @Test
    void valueNoiseDifferentSeedsDiffer() {
        double a = RandomOps.valueNoise3(1.0d, 2.0d, 3.0d, 0);
        double b = RandomOps.valueNoise3(1.0d, 2.0d, 3.0d, 1);
        assertNotEquals(a, b);
    }

    @Test
    void valueNoiseNonFiniteIsNaN() {
        assertTrue(Double.isNaN(RandomOps.valueNoise3(Double.NaN, 0.0d, 0.0d, 0)));
        assertTrue(Double.isNaN(RandomOps.valueNoise3(0.0d, Double.POSITIVE_INFINITY, 0.0d, 0)));
    }

    @Test
    void valueNoiseHugeFiniteCoordinateIsNaNOrBounded() {
        double huge = RandomOps.valueNoise3(1e20d, 0.0d, 0.0d, 0);
        assertTrue(Double.isNaN(huge) || (Double.isFinite(huge) && huge >= -1.0d && huge <= 1.0d),
                "huge finite coords must not silently overflow, got " + huge);
        // Current policy: lattice overflow / non-unit fraction → NaN
        assertTrue(Double.isNaN(huge));

        double hugeNeg = RandomOps.valueNoise3(-1e20d, 0.0d, 0.0d, 0);
        assertTrue(Double.isNaN(hugeNeg));

        double max = RandomOps.valueNoise3(Double.MAX_VALUE, 0.0d, 0.0d, 0);
        assertTrue(Double.isNaN(max));

        // Still coherent / finite for large-but-safe world-scale coords
        double world = RandomOps.valueNoise3(1e6d, 2e6d, 3e6d, 0);
        assertTrue(Double.isFinite(world));
        assertTrue(world >= -1.0d && world <= 1.0d);
    }

    @Test
    void resolveVectorAcceptsJomlAndLegacyVec3d() {
        Vector3d joml = RandomOps.resolveVector(new Vector3d(1, 2, 3), RandomOps.defaultMinCorner());
        assertEquals(1.0d, joml.x, 0.0d);
        assertEquals(2.0d, joml.y, 0.0d);
        assertEquals(3.0d, joml.z, 0.0d);

        Vector3d fromMc = RandomOps.resolveVector(
                new net.minecraft.util.math.Vec3d(4, 5, 6),
                RandomOps.defaultMinCorner());
        assertEquals(4.0d, fromMc.x, 0.0d);

        Vector3d fallback = RandomOps.resolveVector("nope", RandomOps.defaultMaxCorner());
        assertEquals(1.0d, fallback.x, 0.0d);
    }
}
