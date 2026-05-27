package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

/**
 * Capsule SDF primitive defined by two endpoints and radius.
 */
public class CapsuleSdfData implements SignedDistanceFieldData {
    private static final double EPS = 1.0e-9d;

    private final Vector3d a;
    private final Vector3d b;
    private final double radius;

    public CapsuleSdfData(Vector3d a, Vector3d b, double radius) {
        this.a = new Vector3d(a);
        this.b = new Vector3d(b);
        this.radius = Math.max(0.0d, radius);
    }

    public Vector3d getEndpointA() {
        return new Vector3d(a);
    }

    public Vector3d getEndpointB() {
        return new Vector3d(b);
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public double sampleDistance(Vector3d point) {
        Vector3d pa = new Vector3d(point).sub(a);
        Vector3d ba = new Vector3d(b).sub(a);
        double hDen = ba.dot(ba);
        double h = hDen <= EPS ? 0.0d : clamp01(pa.dot(ba) / hDen);
        return pa.sub(ba.mul(h)).length() - radius;
    }

    private static double clamp01(double v) {
        return Math.max(0.0d, Math.min(1.0d, v));
    }
}
