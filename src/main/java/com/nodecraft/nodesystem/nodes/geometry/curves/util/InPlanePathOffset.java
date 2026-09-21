package com.nodecraft.nodesystem.nodes.geometry.curves.util;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.nodes.geometry.curves.MiterJoinCalculator;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared in-plane parallel offset for path vertices (line / polyline / curve samples).
 */
public final class InPlanePathOffset {

    private static final double EPS = 1.0e-9d;

    private InPlanePathOffset() {
    }

    public record Result(List<Vector3d> points, boolean closed, PolylineData polyline) {
    }

    public static @Nullable Result offset(@Nullable List<Vector3d> worldVerts,
                                          PlaneData plane,
                                          double offset,
                                          double miterLimit) {
        if (worldVerts == null || worldVerts.size() < 2 || Math.abs(offset) < EPS) {
            return null;
        }

        boolean closed = PathUtils.isClosed(worldVerts);
        List<Vector3d> unique = closed ? worldVerts.subList(0, worldVerts.size() - 1) : worldVerts;
        if (unique.size() < 2) {
            return null;
        }

        PlaneProjectionUtils.PlaneAxes axes = PlaneProjectionUtils.PlaneAxes.from(plane);
        List<Vector2d> pts2d = new ArrayList<>(unique.size());
        for (Vector3d point : unique) {
            pts2d.add(axes.to2d(plane.projectPoint(point)));
        }

        List<Vector2d> offset2d = offsetPolyline2d(pts2d, closed, offset, miterLimit);
        if (offset2d == null || offset2d.size() < 2) {
            return null;
        }

        List<Vector3d> offsetPoints = new ArrayList<>(offset2d.size());
        for (Vector2d point : offset2d) {
            offsetPoints.add(axes.from2d(point));
        }

        List<Vec3d> polyPoints = PathUtils.toVec3dList(offsetPoints, closed);
        PolylineData polyline = PathUtils.createPolylineOrNull(polyPoints);
        if (polyline == null) {
            return null;
        }
        return new Result(offsetPoints, closed, polyline);
    }

    static @Nullable List<Vector2d> offsetPolyline2d(List<Vector2d> pts,
                                                     boolean closed,
                                                     double offset,
                                                     double miterLimit) {
        int n = pts.size();
        if (n < 2) {
            return null;
        }
        int segCount = closed ? n : n - 1;
        Vector2d[] left = new Vector2d[segCount];
        for (int i = 0; i < segCount; i++) {
            Vector2d a = pts.get(i);
            Vector2d b = pts.get((i + 1) % n);
            Vector2d d = new Vector2d(b).sub(a);
            double len = d.length();
            if (len < EPS) {
                return null;
            }
            d.mul(1.0d / len);
            left[i] = new Vector2d(-d.y, d.x).mul(offset);
        }

        List<Vector2d> out = new ArrayList<>(n);
        if (!closed) {
            out.add(new Vector2d(pts.getFirst()).add(left[0]));
            for (int i = 1; i < n - 1; i++) {
                Vector2d corner = MiterJoinCalculator.intersectOrBevel(
                    pts.get(i - 1), pts.get(i), left[i - 1],
                    pts.get(i), pts.get(i + 1), left[i],
                    pts.get(i), miterLimit, offset);
                if (corner == null) {
                    return null;
                }
                out.add(corner);
            }
            out.add(new Vector2d(pts.get(n - 1)).add(left[n - 2]));
            return out;
        }

        for (int i = 0; i < n; i++) {
            int prev = (i - 1 + n) % n;
            int next = (i + 1) % n;
            Vector2d corner = MiterJoinCalculator.intersectOrBevel(
                pts.get(prev), pts.get(i), left[prev],
                pts.get(i), pts.get(next), left[i],
                pts.get(i), miterLimit, offset);
            if (corner == null) {
                return null;
            }
            out.add(corner);
        }
        return out;
    }
}
