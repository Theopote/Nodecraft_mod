package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

import java.util.Objects;

/**
 * Solid hemisphere: full sphere intersected with the closed half-space
 * {@code dot(p - center, axis) >= 0}. The flat circular face lies in the plane through {@code center}
 * with normal {@code axis}; the dome bulges in the {@code axis} direction.
 */
public class HemisphereGeometryData implements GeometryData {
    private final Vector3d center;
    private final Vector3d axis;
    private final double radius;

    public HemisphereGeometryData(Vector3d center, Vector3d axis, double radius) {
        if (radius < 0.0d) {
            throw new IllegalArgumentException("Hemisphere radius cannot be negative");
        }
        if (axis == null || axis.lengthSquared() <= 1.0e-18d) {
            throw new IllegalArgumentException("Hemisphere axis must be a non-zero direction");
        }
        this.center = new Vector3d(center);
        this.axis = new Vector3d(axis).normalize();
        this.radius = radius;
    }

    public Vector3d getCenter() {
        return new Vector3d(center);
    }

    /** Unit vector from the flat face into the dome (solid lies where dot(p - center, axis) >= 0). */
    public Vector3d getAxis() {
        return new Vector3d(axis);
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HemisphereGeometryData that)) return false;
        return Double.compare(that.radius, radius) == 0
            && Objects.equals(center, that.center)
            && Objects.equals(axis, that.axis);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, axis, radius);
    }

    @Override
    public String toString() {
        return "HemisphereGeometryData{center=" + center + ", axis=" + axis + ", radius=" + radius + "}";
    }
}
