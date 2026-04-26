package com.nodecraft.nodesystem.util;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Lloyd relaxation for 3D sites using a uniform axis-aligned grid: each cell center votes for its
 * nearest site; sites move to the centroid of cells they own. This is an approximation, not an exact 3D Voronoi diagram.
 */
public final class Voronoi3DGridLloyd {

    private Voronoi3DGridLloyd() {
    }

    public static List<Vector3d> relax(
        Vector3d min,
        Vector3d max,
        List<Vector3d> sites,
        int cellsPerAxis,
        int iterations
    ) {
        if (min == null || max == null || sites == null || sites.isEmpty()) {
            return List.of();
        }
        int n = Math.max(4, Math.min(96, cellsPerAxis));
        int iters = Math.max(1, Math.min(32, iterations));

        List<Vector3d> working = new ArrayList<>(sites.size());
        for (Vector3d s : sites) {
            working.add(new Vector3d(s));
        }

        Vector3d span = new Vector3d(max).sub(min);
        if (span.lengthSquared() < 1.0e-18d) {
            return List.copyOf(working);
        }

        double invNx = 1.0d / n;
        double invNy = 1.0d / n;
        double invNz = 1.0d / n;

        for (int it = 0; it < iters; it++) {
            double[] sumX = new double[working.size()];
            double[] sumY = new double[working.size()];
            double[] sumZ = new double[working.size()];
            int[] counts = new int[working.size()];

            for (int ix = 0; ix < n; ix++) {
                for (int iy = 0; iy < n; iy++) {
                    for (int iz = 0; iz < n; iz++) {
                        double cx = min.x + (ix + 0.5d) * span.x * invNx;
                        double cy = min.y + (iy + 0.5d) * span.y * invNy;
                        double cz = min.z + (iz + 0.5d) * span.z * invNz;
                        int best = nearestIndex(cx, cy, cz, working);
                        sumX[best] += cx;
                        sumY[best] += cy;
                        sumZ[best] += cz;
                        counts[best]++;
                    }
                }
            }

            for (int s = 0; s < working.size(); s++) {
                if (counts[s] > 0) {
                    working.get(s).set(
                        sumX[s] / counts[s],
                        sumY[s] / counts[s],
                        sumZ[s] / counts[s]
                    );
                }
            }
        }
        return List.copyOf(working);
    }

    private static int nearestIndex(double x, double y, double z, List<Vector3d> sites) {
        int best = 0;
        double bestD2 = Double.POSITIVE_INFINITY;
        for (int i = 0; i < sites.size(); i++) {
            Vector3d p = sites.get(i);
            double dx = x - p.x;
            double dy = y - p.y;
            double dz = z - p.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = i;
            }
        }
        return best;
    }
}
