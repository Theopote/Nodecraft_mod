package com.nodecraft.nodesystem.util;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * k nearest neighbors (excluding self) for a point cloud using a uniform 3D grid hash.
 * Falls back to full scan when the grid is degenerate or local candidates are insufficient.
 */
public final class PointListKnn3d {

    private static final double EPS = 1.0e-12d;

    private PointListKnn3d() {
    }

    /**
     * Fills {@code outIdx} with up to {@code k} neighbor indices closest to {@code points.get(query)},
     * never including {@code query}. Unused slots remain {@code -1}.
     */
    public static void fillKNearest(List<Vector3d> points, int query, int k, int[] outIdx) {
        int n = points.size();
        for (int t = 0; t < k; t++) {
            outIdx[t] = -1;
        }
        if (n < 2 || k < 1) {
            return;
        }

        Vector3d min = new Vector3d(points.getFirst());
        Vector3d max = new Vector3d(points.getFirst());
        for (Vector3d p : points) {
            min.min(p);
            max.max(p);
        }
        Vector3d span = new Vector3d(max).sub(min);
        double maxSpan = Math.max(span.x, Math.max(span.y, span.z));
        if (maxSpan < EPS) {
            bruteForce(points, query, k, outIdx);
            return;
        }

        int g = gridCellsPerAxis(n);

        Map<Long, List<Integer>> buckets = new HashMap<>(Math.min(n * 2, 65536));
        for (int j = 0; j < n; j++) {
            Vector3d p = points.get(j);
            int ix = cellIndex(p.x - min.x, span.x, g);
            int iy = cellIndex(p.y - min.y, span.y, g);
            int iz = cellIndex(p.z - min.z, span.z, g);
            buckets.computeIfAbsent(pack(ix, iy, iz), key -> new ArrayList<>(4)).add(j);
        }

        Vector3d pq = points.get(query);
        int qix = cellIndex(pq.x - min.x, span.x, g);
        int qiy = cellIndex(pq.y - min.y, span.y, g);
        int qiz = cellIndex(pq.z - min.z, span.z, g);

        ArrayList<Integer> cand = new ArrayList<>(Math.min(n, 512));
        collectNeighborCube(g, buckets, qix, qiy, qiz, query, 1, cand);
        if (cand.size() < k) {
            collectNeighborCube(g, buckets, qix, qiy, qiz, query, 2, cand);
        }

        if (cand.size() < k) {
            bruteForce(points, query, k, outIdx);
            return;
        }

        pickKSmallestByDistance(points, query, k, cand, outIdx);
    }

    /** Collects indices from all cells in a cube of half-width {@code halfWindow} around the query cell. */
    private static void collectNeighborCube(
        int g,
        Map<Long, List<Integer>> buckets,
        int qix,
        int qiy,
        int qiz,
        int query,
        int halfWindow,
        ArrayList<Integer> cand
    ) {
        cand.clear();
        for (int dx = -halfWindow; dx <= halfWindow; dx++) {
            for (int dy = -halfWindow; dy <= halfWindow; dy++) {
                for (int dz = -halfWindow; dz <= halfWindow; dz++) {
                    int ix = qix + dx;
                    int iy = qiy + dy;
                    int iz = qiz + dz;
                    if (ix < 0 || iy < 0 || iz < 0 || ix >= g || iy >= g || iz >= g) {
                        continue;
                    }
                    List<Integer> cell = buckets.get(pack(ix, iy, iz));
                    if (cell == null) {
                        continue;
                    }
                    for (Integer idx : cell) {
                        if (idx != query) {
                            cand.add(idx);
                        }
                    }
                }
            }
        }
    }

    private static void bruteForce(List<Vector3d> points, int query, int k, int[] outIdx) {
        ArrayList<Integer> all = new ArrayList<>(points.size());
        for (int j = 0; j < points.size(); j++) {
            if (j != query) {
                all.add(j);
            }
        }
        pickKSmallestByDistance(points, query, k, all, outIdx);
    }

    private static void pickKSmallestByDistance(List<Vector3d> points, int query, int k, List<Integer> cand, int[] outIdx) {
        Vector3d q = points.get(query);
        int found = 0;
        for (int t = 0; t < k; t++) {
            int best = -1;
            double bestD = Double.POSITIVE_INFINITY;
            for (int idx : cand) {
                if (idx < 0) {
                    continue;
                }
                boolean used = false;
                for (int u = 0; u < found; u++) {
                    if (outIdx[u] == idx) {
                        used = true;
                        break;
                    }
                }
                if (used) {
                    continue;
                }
                double d2 = q.distanceSquared(points.get(idx));
                if (d2 < bestD) {
                    bestD = d2;
                    best = idx;
                }
            }
            if (best < 0) {
                break;
            }
            outIdx[found++] = best;
        }
    }

    private static int cellIndex(double offset, double span, int g) {
        if (span < EPS) {
            return 0;
        }
        int ix = (int) Math.floor(offset / span * g);
        if (ix < 0) {
            ix = 0;
        }
        if (ix >= g) {
            ix = g - 1;
        }
        return ix;
    }

    private static long pack(int ix, int iy, int iz) {
        return (ix & 0x1FFFFFL) | ((iy & 0x1FFFFFL) << 21) | ((iz & 0x1FFFFFL) << 42);
    }

    private static int gridCellsPerAxis(int n) {
        double target = Math.cbrt(n / 6.0d);
        int g = (int) Math.ceil(target * 2.0d);
        return Math.max(4, Math.min(64, g));
    }
}
