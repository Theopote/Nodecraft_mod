package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Shared frame construction helpers used across frame/plane nodes.
 */
public final class FrameUtils {

    private static final double EPS = 1.0e-12d;

    private static final Vector3d WORLD_X = new Vector3d(1.0d, 0.0d, 0.0d);
    private static final Vector3d WORLD_Y = new Vector3d(0.0d, 1.0d, 0.0d);
    private static final Vector3d WORLD_Z = new Vector3d(0.0d, 0.0d, 1.0d);

    private FrameUtils() {
    }

    /**
     * Builds a right-handed orthonormal frame on a plane. Z aligns with the plane normal;
     * X is {@code xHint} projected onto the plane, with a deterministic cardinal fallback.
     */
    public static @Nullable FrameData fromPlane(PlaneData plane, @Nullable Vector3d xHint) {
        if (plane == null) {
            return null;
        }
        Vector3d origin = plane.getPoint();
        Vector3d normal = plane.getNormal();
        if (!isFinite(origin) || !isUsableAxis(normal)) {
            return null;
        }
        normal = new Vector3d(normal).normalize();

        Vector3d x = projectOntoPlane(resolveHint(xHint), normal);
        if (!isUsableAxis(x)) {
            x = projectOntoPlane(leastAlignedCardinal(normal), normal);
        }
        if (!isUsableAxis(x)) {
            for (Vector3d cardinal : new Vector3d[] {WORLD_X, WORLD_Y, WORLD_Z}) {
                x = projectOntoPlane(cardinal, normal);
                if (isUsableAxis(x)) {
                    break;
                }
            }
        }
        if (!isUsableAxis(x)) {
            return null;
        }
        x.normalize();

        Vector3d y = new Vector3d(normal).cross(x);
        if (!isUsableAxis(y)) {
            return null;
        }
        y.normalize();

        return FrameData.orthonormal(origin, x, y, normal);
    }

    private static Vector3d resolveHint(@Nullable Vector3d xHint) {
        if (xHint != null && isUsableAxis(xHint)) {
            return new Vector3d(xHint);
        }
        return null;
    }

    private static Vector3d projectOntoPlane(@Nullable Vector3d vector, Vector3d normal) {
        if (vector == null || !isUsableAxis(vector)) {
            return new Vector3d();
        }
        return new Vector3d(vector).sub(new Vector3d(normal).mul(vector.dot(normal)));
    }

    private static Vector3d leastAlignedCardinal(Vector3d axis) {
        double ax = Math.abs(axis.x);
        double ay = Math.abs(axis.y);
        double az = Math.abs(axis.z);
        if (ax <= ay && ax <= az) {
            return WORLD_X;
        }
        if (ay <= az) {
            return WORLD_Y;
        }
        return WORLD_Z;
    }

    private static boolean isFinite(Vector3d vector) {
        return vector != null
            && Double.isFinite(vector.x)
            && Double.isFinite(vector.y)
            && Double.isFinite(vector.z);
    }

    private static boolean isUsableAxis(Vector3d axis) {
        return isFinite(axis) && axis.lengthSquared() > EPS;
    }
}
