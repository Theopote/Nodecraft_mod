package com.nodecraft.nodesystem.datatypes;

import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.Objects;

/**
 * Represents an axis-aligned or oriented box geometry in local space.
 */
public class BoxGeometryData {
    private final Vector3d center;
    private final Vector3d halfExtents;
    private final Matrix3d orientationMatrix;
    private final boolean oriented;

    public BoxGeometryData(Vector3d center, Vector3d halfExtents) {
        this(center, halfExtents, new Matrix3d().identity(), false);
    }

    public BoxGeometryData(Vector3d center, Vector3d halfExtents, Matrix3d orientationMatrix, boolean oriented) {
        this.center = new Vector3d(center);
        this.halfExtents = new Vector3d(halfExtents);
        this.orientationMatrix = new Matrix3d(orientationMatrix);
        this.oriented = oriented;
    }

    public Vector3d getCenter() {
        return new Vector3d(center);
    }

    public Vector3d getHalfExtents() {
        return new Vector3d(halfExtents);
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
        if (!(o instanceof BoxGeometryData that)) return false;
        return oriented == that.oriented
            && Objects.equals(center, that.center)
            && Objects.equals(halfExtents, that.halfExtents)
            && Objects.equals(orientationMatrix, that.orientationMatrix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, halfExtents, orientationMatrix, oriented);
    }

    @Override
    public String toString() {
        return "BoxGeometryData{center=" + center
            + ", halfExtents=" + halfExtents
            + ", oriented=" + oriented + "}";
    }
}
