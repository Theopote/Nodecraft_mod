package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PlaneProjectionUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.List;

/**
 * Overflow-safer planar metrics for {@link PolygonProfileData}: local-UV shoelace
 * area, world-edge perimeter, and world {@link PointUtils#safeListCenter}.
 */
public final class PolygonProfileMetrics {

    private PolygonProfileMetrics() {
    }

    public static double signedArea(@Nullable PolygonProfileData profile) {
        if (profile == null) {
            return Double.NaN;
        }
        List<Vector3d> closed = profile.closedPoints();
        if (closed.size() < 4) {
            return Double.NaN;
        }
        PlaneProjectionUtils.PlaneProjectionContext ctx =
            PlaneProjectionUtils.PlaneProjectionContext.from(profile.plane(), closed.getFirst());
        double area2 = 0.0d;
        for (int i = 0; i < closed.size() - 1; i++) {
            Vector2d a = ctx.toLocal(closed.get(i));
            Vector2d b = ctx.toLocal(closed.get(i + 1));
            if (!Double.isFinite(a.x) || !Double.isFinite(a.y)
                || !Double.isFinite(b.x) || !Double.isFinite(b.y)) {
                return Double.NaN;
            }
            double term = (a.x * b.y - b.x * a.y);
            if (!Double.isFinite(term)) {
                return Double.NaN;
            }
            area2 += term;
            if (!Double.isFinite(area2)) {
                return Double.NaN;
            }
        }
        double area = area2 * 0.5d;
        return Double.isFinite(area) ? area : Double.NaN;
    }

    public static double area(@Nullable PolygonProfileData profile) {
        double signed = signedArea(profile);
        return Double.isFinite(signed) ? Math.abs(signed) : Double.NaN;
    }

    public static double perimeter(@Nullable PolygonProfileData profile) {
        if (profile == null) {
            return Double.NaN;
        }
        List<Vector3d> closed = profile.closedPoints();
        if (closed.size() < 2) {
            return Double.NaN;
        }
        double perimeter = 0.0d;
        for (int i = 0; i < closed.size() - 1; i++) {
            double length = VectorUtils.safeDistance(closed.get(i), closed.get(i + 1));
            if (!Double.isFinite(length)) {
                return Double.NaN;
            }
            perimeter += length;
            if (!Double.isFinite(perimeter)) {
                return Double.NaN;
            }
        }
        return perimeter;
    }

    public static @Nullable Vector3d center(@Nullable PolygonProfileData profile) {
        if (profile == null) {
            return null;
        }
        return PointUtils.safeListCenter(profile.getUniquePoints());
    }
}
