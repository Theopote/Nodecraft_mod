package com.nodecraft.nodesystem.util;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointListKnn3dTest {

    @Test
    void queryMatchesBruteForceOracleIncludingCellEdgeCase() {
        List<Vector3d> points = new ArrayList<>();
        points.add(new Vector3d(0.999d, 0.0d, 0.0d));
        for (int i = 0; i < 8; i++) {
            points.add(new Vector3d(-0.1d, i * 0.05d, 0.0d));
        }
        points.add(new Vector3d(3.5d, 0.0d, 0.0d));
        points.add(new Vector3d(0.0d, 0.0d, 4.0d));

        int k = 3;
        int[] expected = bruteForce(points, 0, k);
        PointListKnn3d.Index index = PointListKnn3d.Index.build(points);
        int[] actual = new int[k];
        index.queryKNearest(0, k, actual);
        Arrays.sort(expected);
        Arrays.sort(actual);
        assertEquals(Arrays.toString(expected), Arrays.toString(actual));
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.SECONDS)
    void relaxDefaultItersCompletesAt1k4k8k() {
        assertRelaxFinishes(1024);
        assertRelaxFinishes(4096);
        assertRelaxFinishes(8192);
    }

    private static void assertRelaxFinishes(int count) {
        List<Vector3d> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new Vector3d(i % 64, (i / 64) % 64, i / 4096.0d));
        }
        PointListKnn3d.Index index = PointListKnn3d.Index.build(points);
        int k = 6;
        int[] buf = new int[k];
        for (int iter = 0; iter < 4; iter++) {
            for (int i = 0; i < count; i++) {
                index.queryKNearest(i, k, buf);
            }
            index = PointListKnn3d.Index.build(points);
        }
        index.queryKNearest(0, k, buf);
        int found = 0;
        for (int idx : buf) {
            if (idx >= 0) {
                found++;
            }
        }
        assertTrue(found > 0);
    }

    private static int[] bruteForce(List<Vector3d> points, int query, int k) {
        int[] out = new int[k];
        Arrays.fill(out, -1);
        boolean[] used = new boolean[points.size()];
        used[query] = true;
        for (int t = 0; t < k; t++) {
            int best = -1;
            double bestD = Double.POSITIVE_INFINITY;
            for (int i = 0; i < points.size(); i++) {
                if (used[i]) {
                    continue;
                }
                double d = PointUtils.safeDistance(points.get(query), points.get(i));
                if (Double.isFinite(d) && d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            used[best] = true;
            out[t] = best;
        }
        return out;
    }
}
