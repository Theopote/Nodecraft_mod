package com.nodecraft.nodesystem.datatypes;

import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.Objects;

/**
 * Represents an ellipsoid defined by center, per-axis radii, and optional orientation.
 * Orientation is required for correct Rotate / Mirror / Transform when radii are unequal.
 */
public class EllipsoidGeometryData implements GeometryData {
    private final Vector3d center;
    private final Vector3d radii;
    private final Matrix3d orientationMatrix;
    private final boolean oriented;

    public EllipsoidGeometryData(Vector3d center, Vector3d radii) {
        this(center, radii, new Matrix3d().identity(), false);
    }

    public EllipsoidGeometryData(Vector3d center, Vector3d radii, Matrix3d orientationMatrix, boolean oriented) {
        if (radii.x < 0.0d || radii.y < 0.0d || radii.z < 0.0d) {
            throw new IllegalArgumentException("Ellipsoid radii cannot be negative");
        }
        this.center = new Vector3d(center);
        this.radii = new Vector3d(radii);
        this.orientationMatrix = orientationMatrix == null
            ? new Matrix3d().identity()
            : new Matrix3d(orientationMatrix);
        this.oriented = oriented;
    }

    public Vector3d getCenter() {
        return new Vector3d(center);
    }

    public Vector3d getRadii() {
        return new Vector3d(radii);
    }

    public Matrix3d getOrientationMatrix() {
        return new Matrix3d(orientationMatrix);
    }

    public boolean isOriented() {
        return oriented;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EllipsoidGeometryData that)) return false;
        return oriented == that.oriented
            && Objects.equals(center, that.center)
            && Objects.equals(radii, that.radii)
            && orientationMatrix.equals(that.orientationMatrix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, radii, orientationMatrix, oriented);
    }

    @Override
    public String toString() {
        return "EllipsoidGeometryData{center=" + center + ", radii=" + radii
            + ", oriented=" + oriented + "}";
    }
}
