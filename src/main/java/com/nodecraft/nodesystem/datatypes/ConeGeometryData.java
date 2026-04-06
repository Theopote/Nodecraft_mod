package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

import java.util.Objects;

/**
 * Represents a cone defined by a base center, apex, and base radius.
 */
public class ConeGeometryData implements GeometryData {
    private final Vector3d baseCenter;
    private final Vector3d apex;
    private final double baseRadius;

    public ConeGeometryData(Vector3d baseCenter, Vector3d apex, double baseRadius) {
        if (baseRadius < 0.0d) {
            throw new IllegalArgumentException("Cone base radius cannot be negative");
        }
        this.baseCenter = new Vector3d(baseCenter);
        this.apex = new Vector3d(apex);
        this.baseRadius = baseRadius;
    }

    public Vector3d getBaseCenter() {
        return new Vector3d(baseCenter);
    }

    public Vector3d getApex() {
        return new Vector3d(apex);
    }

    public double getBaseRadius() {
        return baseRadius;
    }

    public Vector3d getAxisVector() {
        return new Vector3d(apex).sub(baseCenter);
    }

    public double getHeight() {
        return baseCenter.distance(apex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConeGeometryData that)) return false;
        return Double.compare(that.baseRadius, baseRadius) == 0
            && Objects.equals(baseCenter, that.baseCenter)
            && Objects.equals(apex, that.apex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseCenter, apex, baseRadius);
    }

    @Override
    public String toString() {
        return "ConeGeometryData{baseCenter=" + baseCenter
            + ", apex=" + apex
            + ", baseRadius=" + baseRadius + "}";
    }
}
