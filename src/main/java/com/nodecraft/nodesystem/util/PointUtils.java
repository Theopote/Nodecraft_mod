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

    /** Returns a copy when finite; otherwise null (graph publish guard). */
    public static @Nullable Vector3d requireFinitePoint(@Nullable Vector3d point) {
        if (!isFinite(point)) {
            return null;
        }
        return new Vector3d(point);
    }

    /**
     * Overflow-safe Euclidean distance using chained {@link Math#hypot}.
     * Returns {@link Double#NaN} when inputs are non-finite or distance is non-finite.
     */
    public static double safeDistance(@Nullable Vector3d a, @Nullable Vector3d b) {
        if (!isFinite(a) || !isFinite(b)) {
            return Double.NaN;
        }
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        double distance = Math.hypot(Math.hypot(dx, dy), dz);
        return isFinite(distance) ? distance : Double.NaN;
    }

    /**
     * Overflow-safe midpoint: per-component {@code 0.5*a + 0.5*b}.
     * Returns null when inputs or result are non-finite.
     */
    public static @Nullable Vector3d safeMidpoint(@Nullable Vector3d a, @Nullable Vector3d b) {
        if (!isFinite(a) || !isFinite(b)) {
            return null;
        }
        Vector3d midpoint = new Vector3d(
            a.x * 0.5d + b.x * 0.5d,
            a.y * 0.5d + b.y * 0.5d,
            a.z * 0.5d + b.z * 0.5d
        );
        return isFinite(midpoint) ? midpoint : null;
    }

    /**
     * Displacement {@code to - from} with finite-result fence.
     * Returns null when inputs or result are non-finite.
     */
    public static @Nullable Vector3d safeDisplacement(@Nullable Vector3d from, @Nullable Vector3d to) {
        if (!isFinite(from) || !isFinite(to)) {
            return null;
        }
        Vector3d displacement = new Vector3d(to).sub(from);
        return isFinite(displacement) ? displacement : null;
    }

    /**
     * Numerically stable list center using incremental weighted mean
     * ({@code mean * (n-1)/n + point/n} with weights computed first so finite
     * magnitudes are never scaled above 1).
     * Returns null when empty, any entry non-finite, or result non-finite.
     */
    public static @Nullable Vector3d safeListCenter(@Nullable List<Vector3d> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        Vector3d mean = new Vector3d(points.getFirst());
        if (!isFinite(mean)) {
            return null;
        }
        for (int i = 1; i < points.size(); i++) {
            Vector3d point = points.get(i);
            if (!isFinite(point)) {
                return null;
            }
            double n = i + 1.0d;
            double oldWeight = (n - 1.0d) / n;
            double newWeight = 1.0d / n;
            mean.x = mean.x * oldWeight + point.x * newWeight;
            mean.y = mean.y * oldWeight + point.y * newWeight;
            mean.z = mean.z * oldWeight + point.z * newWeight;
            if (!isFinite(mean)) {
                return null;
            }
        }
        return new Vector3d(mean);
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

    /**
     * Strict POINT_LIST with a hard size budget. Size is checked before allocating the result.
     * Oversized collections return {@code null} (fail closed; no truncation).
     */
    public static @Nullable List<Vector3d> resolveStrictPointListBounded(
            @Nullable Object value,
            int maxElements
    ) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return null;
        }
        if (maxElements < 1 || collection.size() > maxElements) {
            return null;
        }
        return resolveStrictPointList(value);
    }
}
