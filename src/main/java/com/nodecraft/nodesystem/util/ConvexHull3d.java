package com.nodecraft.nodesystem.util;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Brute-force 3D convex hull facet enumeration for moderate point counts (general position).
 */
public final class ConvexHull3d {

    private static final double EPS = 1.0e-8d;
    private static final double PLANE_EPS = 1.0e-6d;

    /**
     * Deduplicated vertices (stable order) and triangle facet vertex indices into {@link #vertices()}.
     */
    public record HullResult(List<Vector3d> vertices, List<int[]> facetIndices) {
        public HullResult {
            vertices = List.copyOf(vertices);
            facetIndices = List.copyOf(facetIndices);
        }
    }

    private ConvexHull3d() {
    }

    /**
     * @return hull vertices and each facet as three vertex indices, or empty facets when degenerate
     */
    public static HullResult compute(List<Vector3d> points) {
        Objects.requireNonNull(points, "points");
        List<Vector3d> pts = dedupe(points);
        if (pts.size() < 4) {
            return new HullResult(pts, List.of());
        }
        if (isCoplanar(pts)) {
            return new HullResult(pts, List.of());
        }

        Vector3d centroid = centroid(pts);
        List<int[]> facets = new ArrayList<>();
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    Vector3d a = pts.get(i);
                    Vector3d b = pts.get(j);
                    Vector3d c = pts.get(k);
                    Vector3d ab = new Vector3d(b).sub(a);
                    Vector3d ac = new Vector3d(c).sub(a);
                    Vector3d nrm = new Vector3d(ab).cross(ac);
                    double lenSq = nrm.lengthSquared();
                    if (lenSq < EPS) {
                        continue;
                    }
                    nrm.normalize();
                    if (nrm.dot(new Vector3d(centroid).sub(a)) > 0.0d) {
                        nrm.negate();
                    }
                    if (!allOnClosedNegativeHalfSpace(pts, a, nrm)) {
                        continue;
                    }
                    facets.add(new int[] {i, j, k});
                }
            }
        }
        return new HullResult(pts, dedupeFacets(facets));
    }

    private static List<int[]> dedupeFacets(List<int[]> facets) {
        Set<String> seen = new LinkedHashSet<>();
        List<int[]> out = new ArrayList<>();
        for (int[] f : facets) {
            int a = f[0];
            int b = f[1];
            int c = f[2];
            int x = Math.min(a, Math.min(b, c));
            int z = Math.max(a, Math.max(b, c));
            int y = a + b + c - x - z;
            String key = x + "/" + y + "/" + z;
            if (seen.add(key)) {
                out.add(new int[] {a, b, c});
            }
        }
        return out;
    }

    private static boolean allOnClosedNegativeHalfSpace(List<Vector3d> pts, Vector3d origin, Vector3d outwardNormal) {
        for (Vector3d p : pts) {
            double h = outwardNormal.dot(new Vector3d(p).sub(origin));
            if (h > PLANE_EPS) {
                return false;
            }
        }
        return true;
    }

    private static Vector3d centroid(List<Vector3d> pts) {
        Vector3d c = new Vector3d();
        for (Vector3d p : pts) {
            c.add(p);
        }
        return c.div(pts.size());
    }

    private static boolean isCoplanar(List<Vector3d> pts) {
        Vector3d a = pts.getFirst();
        Vector3d v1 = null;
        for (int i = 1; i < pts.size(); i++) {
            Vector3d vi = new Vector3d(pts.get(i)).sub(a);
            if (vi.lengthSquared() < EPS) {
                continue;
            }
            v1 = vi;
            break;
        }
        if (v1 == null) {
            return true;
        }
        Vector3d n = null;
        for (int i = 1; i < pts.size(); i++) {
            Vector3d vi = new Vector3d(pts.get(i)).sub(a);
            Vector3d cross = new Vector3d(v1).cross(vi);
            if (cross.lengthSquared() > EPS) {
                n = cross.normalize();
                break;
            }
        }
        if (n == null) {
            return true;
        }
        for (Vector3d p : pts) {
            if (Math.abs(n.dot(new Vector3d(p).sub(a))) > PLANE_EPS) {
                return false;
            }
        }
        return true;
    }

    private static List<Vector3d> dedupe(List<Vector3d> points) {
        List<Vector3d> out = new ArrayList<>(points.size());
        for (Vector3d p : points) {
            boolean dup = false;
            for (Vector3d q : out) {
                if (p.distanceSquared(q) < EPS) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                out.add(new Vector3d(p));
            }
        }
        return out;
    }
}
