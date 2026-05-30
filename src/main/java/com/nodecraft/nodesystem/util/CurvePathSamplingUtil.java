package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared helpers for nodes that treat curves, polylines, and lines as sampled paths.
 */
public final class CurvePathSamplingUtil {

    private static final double EPS = 1.0e-9d;

    private CurvePathSamplingUtil() {
    }

    public static @Nullable List<Vector3d> resolveVertices(@Nullable Object curveObj,
                                                           @Nullable Object polyObj,
                                                           @Nullable Object lineObj) {
        if (curveObj instanceof Curve curve) {
            List<Vec3d> pts = curve.getSamplePoints();
            if (pts.size() < 2) {
                return null;
            }
            return toVector3dList(pts);
        }
        if (polyObj instanceof PolylineData poly) {
            return toVector3dList(poly.getPoints());
        }
        if (lineObj instanceof LineData line) {
            Vec3d a = line.getStart();
            Vec3d b = line.getEnd();
            return List.of(new Vector3d(a.x, a.y, a.z), new Vector3d(b.x, b.y, b.z));
        }
        return null;
    }

    public static boolean isClosedPolyline(List<Vector3d> verts) {
        if (verts.size() < 3) {
            return false;
        }
        Vector3d first = verts.get(0);
        Vector3d last = verts.get(verts.size() - 1);
        return first.distance(last) < 1.0e-6d;
    }

    public static @Nullable double[] buildCumulative(List<Vector3d> unique, boolean closed) {
        int segCount = closed ? unique.size() : unique.size() - 1;
        if (segCount < 1) {
            return null;
        }
        double[] cumulative = new double[segCount + 1];
        cumulative[0] = 0.0d;
        double acc = 0.0d;
        for (int i = 0; i < segCount; i++) {
            Vector3d a = unique.get(i);
            Vector3d b = unique.get((i + 1) % unique.size());
            acc += a.distance(b);
            cumulative[i + 1] = acc;
        }
        return cumulative;
    }

    public static Vector3d sampleAtDistance(List<Vector3d> unique,
                                            boolean closed,
                                            double[] cumulative,
                                            double targetDistance) {
        double clamped = Math.max(0.0d, Math.min(targetDistance, cumulative[cumulative.length - 1]));
        for (int i = 0; i < cumulative.length - 1; i++) {
            double s0 = cumulative[i];
            double s1 = cumulative[i + 1];
            if (clamped <= s1 || i == cumulative.length - 2) {
                Vector3d p0 = unique.get(i);
                Vector3d p1 = unique.get((i + 1) % unique.size());
                double segLen = s1 - s0;
                if (segLen <= EPS) {
                    return new Vector3d(p0);
                }
                double t = (clamped - s0) / segLen;
                return new Vector3d(p0).lerp(p1, t);
            }
        }
        return new Vector3d(unique.get(0));
    }

    public static List<Vec3d> toVec3dList(List<Vector3d> points, boolean closed) {
        List<Vec3d> out = new ArrayList<>(points.size() + (closed ? 1 : 0));
        for (Vector3d point : points) {
            out.add(new Vec3d(point.x, point.y, point.z));
        }
        if (closed && !out.isEmpty()) {
            out.add(out.get(0));
        }
        return out;
    }

    public static @Nullable PolylineData createPolylineOrNull(List<Vec3d> points) {
        if (points.size() < 2) {
            return null;
        }
        for (Vec3d point : points) {
            if (point == null) {
                return null;
            }
        }
        return new PolylineData(points);
    }

    private static List<Vector3d> toVector3dList(List<Vec3d> pts) {
        List<Vector3d> out = new ArrayList<>(pts.size());
        for (Vec3d v : pts) {
            out.add(new Vector3d(v.x, v.y, v.z));
        }
        return out;
    }
}