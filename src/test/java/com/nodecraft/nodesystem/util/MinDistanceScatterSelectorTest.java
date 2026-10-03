package com.nodecraft.nodesystem.util;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinDistanceScatterSelectorTest {

    @Test
    void blueNoiseFallbackScanFindsLegalCandidateAfterProbeMisses() {
        List<Vector3d> candidates = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            candidates.add(new Vector3d());
        }
        candidates.add(new Vector3d(10.0d, 0.0d, 0.0d));

        List<Vector3d> selected = MinDistanceScatterSelector.select(
            candidates,
            2,
            1.0d,
            MinDistanceScatterSelector.DistributionMode.BLUE_NOISE_APPROX,
            new Random(0)
        );

        assertTrue(selected.size() >= 1);
        boolean hasCluster = false;
        boolean hasFar = false;
        for (Vector3d point : selected) {
            if (point.distanceSquared(new Vector3d()) < 1.0e-9d) {
                hasCluster = true;
            }
            if (point.distanceSquared(new Vector3d()) > 1.0d) {
                hasFar = true;
            }
        }
        if (hasCluster) {
            assertTrue(hasFar, "fallback scan must accept the remaining legal far candidate");
            assertEquals(2, selected.size());
        }
    }
}
