package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimitiveVolumeSamplerTest {

    @Test
    void thinTorusInteriorScatterDoesNotRandomFail() {
        TorusGeometryData torus = new TorusGeometryData(
            new Vector3d(),
            new Vector3d(0.0d, 1.0d, 0.0d),
            100.0d,
            0.1d
        );
        int[] seeds = {1, 7, 13, 99, 12345};
        for (int seed : seeds) {
            List<Vector3d> points = PrimitiveVolumeSampler.scatter(
                torus,
                16,
                seed,
                0.0d,
                MinDistanceScatterSelector.DistributionMode.RANDOM
            );
            assertNotNull(points, "seed=" + seed);
            assertEquals(16, points.size(), "seed=" + seed);
            for (Vector3d point : points) {
                assertTrue(PrimitiveVolumeSampler.containsPoint(torus, point), "seed=" + seed + " point=" + point);
            }
        }
    }
}
