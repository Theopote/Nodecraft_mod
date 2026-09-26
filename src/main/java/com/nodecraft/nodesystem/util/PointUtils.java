package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PointData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Shared point validation helpers used across reference point nodes.
 */
public final class PointUtils {

    public static final double EPS = SpatialTolerance.EPS;
    public static final double EPS_SQ = SpatialTolerance.EPS_SQ;

    private PointUtils() {
    }

    /** Strict POINT port: accepts {@link PointData} only. */
    public static @Nullable Vector3d toPointPosition(@Nullable Object value) {
        if (value instanceof PointData pointData) {
            return pointData.position();
        }
        return null;
    }

    public static boolean isFinite(@Nullable Vector3d point) {
        return FrameUtils.isFinite(point);
    }

    public static boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    public static double distanceSquared(Vector3d a, Vector3d b) {
        return a.distanceSquared(b);
    }

    /**
     * Strict POINT_LIST resolution: null/empty/non-Collection → null;
     * any non-PointData or non-finite entry → null; otherwise full list (no filtering).
     */
    public static @Nullable List<Vector3d> resolveStrictPointList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return null;
        }
        List<Vector3d> points = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            if (!(entry instanceof PointData pointData)) {
                return null;
            }
            Vector3d position = pointData.position();
            if (!isFinite(position)) {
                return null;
            }
            points.add(new Vector3d(position));
        }
        return List.copyOf(points);
    }
}
