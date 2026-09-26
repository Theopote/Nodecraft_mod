package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Shared plane construction and validation helpers used across plane nodes.
 */
public final class PlaneUtils {

    public static final double EPS = 1.0e-12d;

    private PlaneUtils() {
    }

    public static boolean isFinite(@Nullable Vector3d vector) {
        return FrameUtils.isFinite(vector);
    }

    public static boolean isUsableNormal(@Nullable Vector3d normal) {
        return isFinite(normal) && normal.lengthSquared() > EPS;
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
        Vector3d ab = new Vector3d(b).sub(a);
        Vector3d ac = new Vector3d(c).sub(a);
        Vector3d cross = ab.cross(ac, new Vector3d());
        double crossSq = cross.lengthSquared();
        double abSq = ab.lengthSquared();
        double acSq = ac.lengthSquared();
        if (crossSq <= EPS * abSq * acSq) {
            return null;
        }
        return PlaneData.canonical(a, cross);
    }
}
