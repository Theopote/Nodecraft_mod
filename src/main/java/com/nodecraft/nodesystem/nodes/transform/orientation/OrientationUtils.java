package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Finite / usable checks and plane projection helpers for orientation nodes.
 */
final class OrientationUtils {
    static final double EPS = 1.0e-12d;

    private OrientationUtils() {
    }

    record PointProjection(Vector3d projected, double signedDistance, double distance) {
    }

    static boolean isFinite(Vector3d vector) {
        return vector != null
            && Double.isFinite(vector.x)
            && Double.isFinite(vector.y)
            && Double.isFinite(vector.z);
    }

    static boolean isUsableDirection(Vector3d vector) {
        return isFinite(vector) && vector.lengthSquared() > EPS;
    }

    static @Nullable PlaneData resolveNormalizedPlane(@Nullable PlaneData plane) {
        if (plane == null) {
            return null;
        }
        return plane.normalized();
    }

    static @Nullable PointProjection projectPoint(@Nullable PlaneData plane, @Nullable Vector3d point) {
        if (!isFinite(point)) {
            return null;
        }
        PlaneData normalized = resolveNormalizedPlane(plane);
        if (normalized == null) {
            return null;
        }
        double signedDistance = normalized.signedDistanceTo(point);
        Vector3d projected = normalized.projectPoint(point);
        return new PointProjection(projected, signedDistance, Math.abs(signedDistance));
    }
}
