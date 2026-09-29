package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimitiveGeometrySurfaceSamplerTest {

    private static final int SAMPLE_COUNT = 10_000;

    @Test
    void coneLateralSamplingIsAreaUniform() {
        ConeGeometryData cone = new ConeGeometryData(
            new Vector3d(0, 0, 0),
            new Vector3d(0, 10, 0),
            5.0d
        );
        Random random = new Random(42_105L);
        int apexThird = 0;
        int baseThird = 0;
        double height = 10.0d;

        for (int i = 0; i < SAMPLE_COUNT; i++) {
            PrimitiveGeometrySurfaceSampler.SurfaceSample sample =
                PrimitiveGeometrySurfaceSampler.sampleRandomSurfacePointForTest(cone, random);
            assertNotNull(sample);
            double t = (height - sample.point().y) / height;
            if (t < 0.33d) {
                apexThird++;
            } else if (t > 0.67d) {
                baseThird++;
            }
        }

        double apexFraction = apexThird / (double) SAMPLE_COUNT;
        double baseFraction = baseThird / (double) SAMPLE_COUNT;
        assertTrue(apexFraction < 0.20d,
            "apex third should be under-represented with area-uniform sampling, got " + apexFraction);
        assertTrue(baseFraction > 0.45d,
            "base third should be over-represented with area-uniform sampling, got " + baseFraction);
    }

    @Test
    void torusSurfaceSamplingIsAreaUniform() {
        TorusGeometryData torus = new TorusGeometryData(
            new Vector3d(),
            new Vector3d(0, 1, 0),
            3.0d,
            0.5d
        );
        Random random = new Random(84_210L);
        int innerHalf = 0;
        int outerHalf = 0;

        double major = torus.majorRadius();
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            PrimitiveGeometrySurfaceSampler.SurfaceSample sample =
                PrimitiveGeometrySurfaceSampler.sampleRandomSurfacePointForTest(torus, random);
            assertNotNull(sample);
            Vector3d point = sample.point();
            double horizontalDist = Math.sqrt(point.x * point.x + point.z * point.z);
            if (horizontalDist < major) {
                innerHalf++;
            } else {
                outerHalf++;
            }
        }

        int total = innerHalf + outerHalf;
        assertTrue(total > SAMPLE_COUNT / 2);
        double innerFraction = innerHalf / (double) total;
        assertTrue(innerFraction > 0.35d && innerFraction < 0.65d,
            "inner/outer minor ring halves should be balanced, inner fraction=" + innerFraction);
    }

    @Test
    void coneAndTorusSamplingDeterministicWithSeed() {
        ConeGeometryData cone = new ConeGeometryData(
            new Vector3d(0, 0, 0),
            new Vector3d(0, 10, 0),
            5.0d
        );
        TorusGeometryData torus = new TorusGeometryData(
            new Vector3d(),
            new Vector3d(0, 1, 0),
            3.0d,
            0.5d
        );

        List<Vector3d> coneA = samplePoints(cone, 12345, 32);
        List<Vector3d> coneB = samplePoints(cone, 12345, 32);
        List<Vector3d> torusA = samplePoints(torus, 67890, 32);
        List<Vector3d> torusB = samplePoints(torus, 67890, 32);

        assertEquals(coneA.size(), coneB.size());
        assertEquals(torusA.size(), torusB.size());
        for (int i = 0; i < coneA.size(); i++) {
            assertTrue(coneA.get(i).equals(coneB.get(i), 1.0e-9d));
            assertTrue(torusA.get(i).equals(torusB.get(i), 1.0e-9d));
        }
    }

    private static List<Vector3d> samplePoints(
        com.nodecraft.nodesystem.datatypes.GeometryData geometry,
        int seed,
        int count
    ) {
        Random random = new Random(seed);
        List<Vector3d> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            PrimitiveGeometrySurfaceSampler.SurfaceSample sample =
                PrimitiveGeometrySurfaceSampler.sampleRandomSurfacePointForTest(geometry, random);
            assertNotNull(sample);
            points.add(new Vector3d(sample.point()));
        }
        return points;
    }
}
