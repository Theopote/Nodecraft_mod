package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

import java.util.Objects;

/**
 * Represents an axis-aligned ellipsoid defined by center and per-axis radii.
 */
public class EllipsoidGeometryData implements GeometryData {
    private final Vector3d center;
    private final Vector3d radii;

    public EllipsoidGeometryData(Vector3d center, Vector3d radii) {
        if (radii.x < 0.0d || radii.y < 0.0d || radii.z < 0.0d) {
            throw new IllegalArgumentException("Ellipsoid radii cannot be negative");
        }
        this.center = new Vector3d(center);
        this.radii = new Vector3d(radii);
    }

    public Vector3d getCenter() {
        return new Vector3d(center);
    }

    public Vector3d getRadii() {
        return new Vector3d(radii);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EllipsoidGeometryData that)) return false;
        return Objects.equals(center, that.center)
            && Objects.equals(radii, that.radii);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, radii);
    }

    @Override
    public String toString() {
        return "EllipsoidGeometryData{center=" + center + ", radii=" + radii + "}";
    }
}
