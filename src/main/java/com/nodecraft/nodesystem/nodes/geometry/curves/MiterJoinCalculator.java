package com.nodecraft.nodesystem.nodes.geometry.curves;

import org.joml.Vector2d;

public final class MiterJoinCalculator {

    private static final double EPS = 1.0e-9d;

    private MiterJoinCalculator() {
    }

    public static Vector2d intersectOrBevel(Vector2d p0, Vector2d p1, Vector2d left0,
                                     Vector2d q0, Vector2d q1, Vector2d left1,
                                     Vector2d cornerWorld,
                                     double miterLimit,
                                     double offset) {
        Vector2d l0s = new Vector2d(p0).add(left0);
        Vector2d l0e = new Vector2d(p1).add(left0);
        Vector2d r0 = new Vector2d(l0e).sub(l0s);

        Vector2d l1s = new Vector2d(q0).add(left1);
        Vector2d l1e = new Vector2d(q1).add(left1);
        Vector2d r1 = new Vector2d(l1e).sub(l1s);

        Vector2d hit = Polyline2DUtils.intersectLines(l0s, r0, l1s, r1);
        if (hit == null) {
            return averageOffsetCorner(cornerWorld, left0, left1);
        }
        double miterDist = hit.distance(cornerWorld);
        if (miterDist > miterLimit * Math.abs(offset) + EPS) {
            return averageOffsetCorner(cornerWorld, left0, left1);
        }
        return hit;
    }

    private static Vector2d averageOffsetCorner(Vector2d cornerWorld, Vector2d left0, Vector2d left1) {
        Vector2d a = new Vector2d(cornerWorld).add(left0);
        Vector2d b = new Vector2d(cornerWorld).add(left1);
        return new Vector2d(a).add(b).mul(0.5d);
    }
}