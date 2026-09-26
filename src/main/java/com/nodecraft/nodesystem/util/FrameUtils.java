package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Shared frame construction helpers used across frame/plane nodes.
 */
public final class FrameUtils {

    public static final double EPS = 1.0e-12d;

    private static final Vector3d WORLD_X = new Vector3d(1.0d, 0.0d, 0.0d);
    private static final Vector3d WORLD_Y = new Vector3d(0.0d, 1.0d, 0.0d);
    private static final Vector3d WORLD_Z = new Vector3d(0.0d, 0.0d, 1.0d);

    private FrameUtils() {
    }

    public static @Nullable Vector3d resolvePoint(@Nullable Object value) {
        return SpatialValueResolver.resolvePoint(value);
    }

    public static @Nullable Vector3d resolveVector(@Nullable Object value) {
        return SpatialValueResolver.resolveVector(value);
    }

    public static boolean isFinite(@Nullable Vector3d vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    public static boolean isUsableAxis(@Nullable Vector3d axis) {
        return isFinite(axis) && axis.lengthSquared() > EPS;
    }

    public static @Nullable Vector3d normalizedDirection(@Nullable Vector3d from, @Nullable Vector3d to) {
        if (!isFinite(from) || !isFinite(to)) {
            return null;
        }
        Vector3d axis = new Vector3d(to).sub(from);
        if (!isUsableAxis(axis)) {
            return null;
        }
        return axis.normalize();
    }

    /**
     * Returns true when both axes are usable and parallel within epsilon.
     */
    public static boolean areParallel(@Nullable Vector3d a, @Nullable Vector3d b) {
        if (!isUsableAxis(a) || !isUsableAxis(b)) {
            return false;
        }
        Vector3d an = new Vector3d(a).normalize();
        Vector3d bn = new Vector3d(b).normalize();
        double crossLengthSq = an.cross(bn, new Vector3d()).lengthSquared();
        return crossLengthSq <= EPS;
    }

    /**
     * Builds a right-handed orthonormal frame on a plane. Z aligns with the plane normal;
     * X is {@code xHint} projected onto the plane, with a deterministic cardinal fallback.
     */
    public static @Nullable FrameData fromPlane(PlaneData plane, @Nullable Vector3d xHint) {
        if (plane == null) {
            return null;
        }
        return fromNormal(plane.getPoint(), plane.getNormal(), xHint);
    }

    /**
     * Builds a right-handed orthonormal frame tangent to a surface with the given normal.
     * Z aligns with {@code normal}; X is {@code xHint} projected onto the tangent plane.
     */
    public static @Nullable FrameData fromNormal(
            @Nullable Vector3d origin,
            @Nullable Vector3d normal,
            @Nullable Vector3d xHint
    ) {
        if (!isFinite(origin) || !isUsableAxis(normal)) {
            return null;
        }
        Vector3d z = new Vector3d(normal).normalize();

        Vector3d x = projectOntoPlane(resolveHint(xHint), z);
        if (!isUsableAxis(x)) {
            x = projectOntoPlane(leastAlignedCardinal(z), z);
        }
        if (!isUsableAxis(x)) {
            for (Vector3d cardinal : new Vector3d[] {WORLD_X, WORLD_Y, WORLD_Z}) {
                x = projectOntoPlane(cardinal, z);
                if (isUsableAxis(x)) {
                    break;
                }
            }
        }
        if (!isUsableAxis(x)) {
            return null;
        }
        x.normalize();

        Vector3d y = new Vector3d(z).cross(x);
        if (!isUsableAxis(y)) {
            return null;
        }
        y.normalize();

        return FrameData.orthonormal(origin, x, y, z);
    }

    private static @Nullable Vector3d resolveHint(@Nullable Vector3d xHint) {
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
}
