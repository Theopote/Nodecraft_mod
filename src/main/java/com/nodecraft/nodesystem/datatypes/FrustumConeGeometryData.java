package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

import java.util.Objects;

/**
 * Circular frustum (truncated cone) with parallel circular faces at {@code baseCenter} and {@code topCenter}.
 */
public class FrustumConeGeometryData implements GeometryData {
    private static final double EPS = 1.0e-9d;

    private final Vector3d baseCenter;
    private final Vector3d topCenter;
    private final double baseRadius;
    private final double topRadius;

    public FrustumConeGeometryData(Vector3d baseCenter, Vector3d topCenter, double baseRadius, double topRadius) {
        if (baseRadius < 0.0d || topRadius < 0.0d) {
            throw new IllegalArgumentException("Frustum radii cannot be negative");
        }
        Vector3d axis = new Vector3d(topCenter).sub(baseCenter);
        if (axis.lengthSquared() <= EPS * EPS) {
            throw new IllegalArgumentException("Frustum axis length must be positive");
        }
        if (baseRadius <= EPS && topRadius <= EPS) {
            throw new IllegalArgumentException("Frustum must have a positive base or top radius");
        }
        this.baseCenter = new Vector3d(baseCenter);
        this.topCenter = new Vector3d(topCenter);
        this.baseRadius = baseRadius;
        this.topRadius = topRadius;
    }

    public Vector3d getBaseCenter() {
        return new Vector3d(baseCenter);
    }

    public Vector3d getTopCenter() {
        return new Vector3d(topCenter);
    }

    public double getBaseRadius() {
        return baseRadius;
    }

    public double getTopRadius() {
        return topRadius;
    }

    public Vector3d getAxisVector() {
        return new Vector3d(topCenter).sub(baseCenter);
    }

    public double getHeight() {
        return baseCenter.distance(topCenter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FrustumConeGeometryData that)) return false;
        return Double.compare(that.baseRadius, baseRadius) == 0
            && Double.compare(that.topRadius, topRadius) == 0
            && Objects.equals(baseCenter, that.baseCenter)
            && Objects.equals(topCenter, that.topCenter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseCenter, topCenter, baseRadius, topRadius);
    }

    @Override
    public String toString() {
        return "FrustumConeGeometryData{baseCenter=" + baseCenter
            + ", topCenter=" + topCenter
            + ", baseRadius=" + baseRadius
            + ", topRadius=" + topRadius + "}";
    }
}
