package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Shared plane construction and validation helpers used across plane nodes.
 */
public final class PlaneUtils {

    public static final double EPS = SpatialTolerance.EPS;
    public static final double EPS_SQ = SpatialTolerance.EPS_SQ;
    public static final double ANGULAR_SIN_EPS = SpatialTolerance.ANGULAR_SIN_EPS;

    private PlaneUtils() {
    }

    public static boolean isFinite(@Nullable Vector3d vector) {
        return FrameUtils.isFinite(vector);
    }

    public static boolean isUsableNormal(@Nullable Vector3d normal) {
        return VectorUtils.isNonZero(normal);
    }

    public static @Nullable PlaneData fromOriginNormal(@Nullable Vector3d origin, @Nullable Vector3d normal) {
        return PlaneData.canonical(origin, normal);
    }

    /**
     * Builds a plane from three points. Normal = normalize((B-A) x (C-A)).
     * Returns null when points are not finite or nearly collinear.
     */
    public static @Nullable PlaneData fromThreePoints(
            @Nullable Vector3d a,
            @Nullable Vector3d b,
            @Nullable Vector3d c
    ) {
        if (!isFinite(a) || !isFinite(b) || !isFinite(c)) {
            return null;
        }
        Vector3d ab = VectorUtils.safeSubtract(b, a);
        Vector3d ac = VectorUtils.safeSubtract(c, a);
        if (ab == null || ac == null) {
            return null;
        }
        double abLen = VectorUtils.safeLength(ab);
        double acLen = VectorUtils.safeLength(ac);
        Vector3d cross = VectorUtils.safeCross(ab, ac);
        double crossLen = VectorUtils.safeLength(cross);
        if (!Double.isFinite(abLen) || !Double.isFinite(acLen) || !Double.isFinite(crossLen)) {
            return null;
        }
        double denom = abLen * acLen;
        if (!Double.isFinite(denom) || denom <= EPS) {
            return null;
        }
        double sinAbs = crossLen / denom;
        if (!Double.isFinite(sinAbs) || sinAbs <= ANGULAR_SIN_EPS) {
            return null;
        }
        return PlaneData.canonical(a, cross);
    }
}
