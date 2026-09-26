package com.nodecraft.nodesystem.nodes.geometry.curves;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.List;

final class Polyline2DUtils {

    private static final double EPS = 1.0e-9d;

    private Polyline2DUtils() {
    }

    static @Nullable Vector2d intersectLines(Vector2d p, Vector2d r, Vector2d q, Vector2d s) {
        double rxs = cross2(r, s);
        if (Math.abs(rxs) < EPS) {
            return null;
        }
        Vector2d qp = new Vector2d(q).sub(p);
        double t = cross2(qp, s) / rxs;
        return new Vector2d(p).add(new Vector2d(r).mul(t));
    }

    static double cross2(Vector2d a, Vector2d b) {
        return a.x * b.y - a.y * b.x;
    }

    static void appendIfFar(List<Vector2d> out, Vector2d point) {
        Vector2d last = out.getLast();
        if (last.distanceSquared(point) > EPS * EPS) {
            out.add(new Vector2d(point));
        }
    }

    static List<Vector2d> sampleArc(Vector2d center, double radius,
                                    Vector2d start, Vector2d end,
                                    boolean ccw, int segments) {
        double a1 = Math.atan2(start.y - center.y, start.x - center.x);
        double a2 = Math.atan2(end.y - center.y, end.x - center.x);
        double delta = normalizeAngleDelta(a1, a2, ccw);
        List<Vector2d> arc = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double ang = a1 + delta * t;
            double x = center.x + Math.cos(ang) * radius;
            double y = center.y + Math.sin(ang) * radius;
            arc.add(new Vector2d(x, y));
        }
        return arc;
    }

    static double normalizeAngleDelta(double a1, double a2, boolean ccw) {
        double d = a2 - a1;
        while (d <= -Math.PI) {
            d += Math.PI * 2.0d;
        }
        while (d > Math.PI) {
            d -= Math.PI * 2.0d;
        }
        if (ccw && d < 0.0d) {
            d += Math.PI * 2.0d;
        }
        if (!ccw && d > 0.0d) {
            d -= Math.PI * 2.0d;
        }
        return d;
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}