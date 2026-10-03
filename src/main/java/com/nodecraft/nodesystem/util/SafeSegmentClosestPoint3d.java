package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Overflow-safe closest point of a query onto a finite 3D segment.
 */
public final class SafeSegmentClosestPoint3d {

    private SafeSegmentClosestPoint3d() {
    }

    /**
     * @param unitTangent unit {@code B-A} when the segment is usable; {@code null} when degenerate
     */
    public record Hit(Vector3d closest, @Nullable Vector3d unitTangent, double distance, double t) {
    }

    /**
     * Clamp-t projection. Returns {@code null} when inputs or intermediates are non-finite.
     */
    public static @Nullable Hit onSegment(@Nullable Vector3d a, @Nullable Vector3d b, @Nullable Vector3d query) {
        if (!VectorUtils.isFinite(a) || !VectorUtils.isFinite(b) || !VectorUtils.isFinite(query)) {
            return null;
        }
        Vector3d ab = VectorUtils.safeSubtract(b, a);
        double abLen = VectorUtils.safeLength(ab);
        if (!Double.isFinite(abLen)) {
            return null;
        }
        if (abLen <= VectorUtils.EPS) {
            double distance = PointUtils.safeDistance(query, a);
            if (!Double.isFinite(distance)) {
                return null;
            }
            return new Hit(new Vector3d(a), null, distance, 0.0d);
        }
        Vector3d unit = VectorUtils.safeNormalize(ab);
        Vector3d qa = VectorUtils.safeSubtract(query, a);
        double along = VectorUtils.safeDot(qa, unit);
        if (unit == null || qa == null || !Double.isFinite(along)) {
            return null;
        }
        double t = along / abLen;
        if (!Double.isFinite(t)) {
            return null;
        }
        t = Math.max(0.0d, Math.min(1.0d, t));
        Vector3d closest = VectorUtils.safeLerp(a, b, t);
        double distance = PointUtils.safeDistance(query, closest);
        if (closest == null || !Double.isFinite(distance)) {
            return null;
        }
        return new Hit(closest, unit, distance, t);
    }
}
