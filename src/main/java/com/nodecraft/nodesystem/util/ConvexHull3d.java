package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 3D convex hull for moderate point counts. Facets are discovered by brute-force plane tests,
 * grouped by supporting plane, projected to 2D, and fan-triangulated without overlapping coplanar soup.
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

    private record SupportingPlane(Vector3d outwardNormal, Vector3d pointOnPlane) {
    }

    private ConvexHull3d() {
    }

    /**
     * Light de-duplication of nearly coincident points (stable order).
     */
    public static List<Vector3d> dedupePoints(List<Vector3d> points) {
        Objects.requireNonNull(points, "points");
        return dedupe(points);
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
        List<SupportingPlane> supportingPlanes = discoverSupportingPlanes(pts, centroid);
        if (supportingPlanes.isEmpty()) {
            return new HullResult(pts, List.of());
        }

        Map<String, SupportingPlane> planesByKey = new LinkedHashMap<>();
        for (SupportingPlane plane : supportingPlanes) {
            planesByKey.putIfAbsent(planeKey(plane.outwardNormal(), plane.pointOnPlane()), plane);
        }

        List<int[]> facets = new ArrayList<>();
        for (SupportingPlane plane : planesByKey.values()) {
            facets.addAll(triangulateFacet(pts, plane));
        }
        if (facets.isEmpty()) {
            return new HullResult(pts, List.of());
        }
        return compact(pts, facets);
    }

    private static List<SupportingPlane> discoverSupportingPlanes(List<Vector3d> pts, Vector3d centroid) {
        List<SupportingPlane> planes = new ArrayList<>();
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    Vector3d a = pts.get(i);
                    Vector3d b = pts.get(j);
                    Vector3d c = pts.get(k);
                    Vector3d ab = new Vector3d(b).sub(a);
                    Vector3d ac = new Vector3d(c).sub(a);
                    Vector3d normal = new Vector3d(ab).cross(ac);
                    double lenSq = normal.lengthSquared();
                    if (lenSq < EPS) {
                        continue;
                    }
                    normal.normalize();
                    if (normal.dot(new Vector3d(centroid).sub(a)) > 0.0d) {
                        normal.negate();
                    }
                    if (!allOnClosedNegativeHalfSpace(pts, a, normal)) {
                        continue;
                    }
                    planes.add(new SupportingPlane(new Vector3d(normal), new Vector3d(a)));
                }
            }
        }
        return planes;
    }

    private static List<int[]> triangulateFacet(List<Vector3d> pts, SupportingPlane plane) {
        Vector3d normal = plane.outwardNormal();
        double distance = normal.dot(plane.pointOnPlane());
        List<Integer> onPlane = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            if (Math.abs(normal.dot(pts.get(i)) - distance) <= PLANE_EPS) {
                onPlane.add(i);
            }
        }
        if (onPlane.size() < 3) {
            return List.of();
        }

        PlaneData planeData = PlaneData.canonical(plane.pointOnPlane(), normal);
        if (planeData == null) {
            return List.of();
        }
        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(planeData);
        List<Vector2d> projected = new ArrayList<>(onPlane.size());
        for (int index : onPlane) {
            projected.add(axes.to2d(pts.get(index)));
        }

        List<Integer> hullLocal = ConvexHull2d.convexHullIndices(projected);
        if (hullLocal.size() < 3) {
            return List.of();
        }

        List<Integer> hullIndices = new ArrayList<>(hullLocal.size());
        for (int localIndex : hullLocal) {
            hullIndices.add(onPlane.get(localIndex));
        }

        if (!hasOutwardWinding(pts, hullIndices, normal)) {
            hullIndices = new ArrayList<>(hullIndices);
            java.util.Collections.reverse(hullIndices);
        }

        List<int[]> triangles = new ArrayList<>(Math.max(0, hullIndices.size() - 2));
        int anchor = hullIndices.getFirst();
        for (int t = 1; t < hullIndices.size() - 1; t++) {
            triangles.add(new int[] {anchor, hullIndices.get(t), hullIndices.get(t + 1)});
        }
        return triangles;
    }

    private static boolean hasOutwardWinding(List<Vector3d> pts, List<Integer> hullIndices, Vector3d outwardNormal) {
        int i0 = hullIndices.get(0);
        int i1 = hullIndices.get(1);
        int i2 = hullIndices.get(2);
        Vector3d a = pts.get(i0);
        Vector3d b = pts.get(i1);
        Vector3d c = pts.get(i2);
        Vector3d faceNormal = new Vector3d(b).sub(a).cross(new Vector3d(c).sub(a), new Vector3d());
        return faceNormal.dot(outwardNormal) > 0.0d;
    }

    private static HullResult compact(List<Vector3d> pts, List<int[]> facets) {
        Set<Integer> used = new LinkedHashSet<>();
        for (int[] facet : facets) {
            used.add(facet[0]);
            used.add(facet[1]);
            used.add(facet[2]);
        }
        List<Integer> oldIndices = new ArrayList<>(used);
        Map<Integer, Integer> remap = new LinkedHashMap<>();
        List<Vector3d> compactVertices = new ArrayList<>(oldIndices.size());
        for (int i = 0; i < oldIndices.size(); i++) {
            int oldIndex = oldIndices.get(i);
            remap.put(oldIndex, i);
            compactVertices.add(new Vector3d(pts.get(oldIndex)));
        }

        List<int[]> compactFacets = new ArrayList<>(facets.size());
        for (int[] facet : facets) {
            compactFacets.add(new int[] {
                    remap.get(facet[0]),
                    remap.get(facet[1]),
                    remap.get(facet[2])
            });
        }
        return new HullResult(compactVertices, compactFacets);
    }

    private static String planeKey(Vector3d normal, Vector3d pointOnPlane) {
        double distance = normal.dot(pointOnPlane);
        return quant(normal.x) + "/" + quant(normal.y) + "/" + quant(normal.z) + "/" + quant(distance);
    }

    private static String quant(double value) {
        long q = Math.round(value / PLANE_EPS);
        return Long.toString(q);
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
