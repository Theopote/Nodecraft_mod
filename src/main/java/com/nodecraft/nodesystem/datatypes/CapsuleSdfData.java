package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import com.nodecraft.nodesystem.util.VectorUtils;
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
        PrimitiveGeometryValidator.requireValid(PrimitiveGeometryValidator.validateCapsule(a, b, radius));
        this.a = new Vector3d(a);
        this.b = new Vector3d(b);
        this.radius = radius;
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
        Vector3d pa = VectorUtils.safeSubtract(point, a);
        Vector3d ba = VectorUtils.safeSubtract(b, a);
        double hDen = VectorUtils.safeDot(ba, ba);
        if (pa == null || ba == null || !Double.isFinite(hDen)) {
            return Double.NaN;
        }
        double h = hDen <= EPS ? 0.0d : clamp01(VectorUtils.safeDot(pa, ba) / hDen);
        Vector3d closest = VectorUtils.safeScale(ba, h);
        Vector3d delta = VectorUtils.safeSubtract(pa, closest);
        double length = VectorUtils.safeLength(delta);
        if (!Double.isFinite(length)) {
            return Double.NaN;
        }
        return length - radius;
    }

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) {
            return Double.NaN;
        }
        return Math.max(0.0d, Math.min(1.0d, v));
    }
}
