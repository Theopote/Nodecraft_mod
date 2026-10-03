package com.nodecraft.nodesystem.util;

import org.joml.Vector3d;

import java.util.List;

/**
 * Closest point on a polyline (ordered vertices) in 3D, optionally with segment tangent at the closest location.
 */
public final class PolylineClosestPoint3d {

    private PolylineClosestPoint3d() {
    }

    public static boolean closestPoint(List<Vector3d> polyline, Vector3d query, Vector3d destClosest) {
        return closestPointAndTangent(polyline, query, destClosest, null);
    }

    /**
     * Finds the closest point on the polyline to {@code query}.
     * Returns {@code false} when any segment projection overflows or the polyline is unusable.
     */
    public static boolean closestPointAndTangent(
            List<Vector3d> polyline,
            Vector3d query,
            Vector3d destClosest,
            Vector3d destTangent
    ) {
        if (polyline == null || polyline.isEmpty() || !VectorUtils.isFinite(query)) {
            return false;
        }
        if (polyline.size() == 1) {
            if (!VectorUtils.isFinite(polyline.getFirst())) {
                return false;
            }
            destClosest.set(polyline.getFirst());
            if (destTangent != null) {
                destTangent.set(0.0d, 0.0d, 0.0d);
            }
            return true;
        }
        double bestD = Double.POSITIVE_INFINITY;
        Vector3d best = null;
        Vector3d bestTan = null;
        for (int i = 0; i < polyline.size() - 1; i++) {
            SafeSegmentClosestPoint3d.Hit hit = SafeSegmentClosestPoint3d.onSegment(
                polyline.get(i), polyline.get(i + 1), query);
            if (hit == null) {
                return false;
            }
            if (hit.distance() < bestD) {
                bestD = hit.distance();
                best = hit.closest();
                bestTan = hit.unitTangent();
            }
        }
        if (best == null) {
            return false;
        }
        destClosest.set(best);
        if (destTangent != null) {
            if (bestTan != null) {
                destTangent.set(bestTan);
            } else {
                destTangent.set(0.0d, 0.0d, 0.0d);
            }
        }
        return true;
    }
}
