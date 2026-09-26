package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.FrameUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Objects;

/**
 * Typed VECTOR value: finite 3D direction or displacement. Zero vector is valid.
 */
public record VectorData(Vector3d components) {

    public VectorData(Vector3d components) {
        this.components = new Vector3d(components);
    }

    public VectorData(double x, double y, double z) {
        this(new Vector3d(x, y, z));
    }

    /**
     * Builds a canonical vector, or {@code null} when components are not finite.
     */
    public static @Nullable VectorData canonical(@Nullable Vector3d vector) {
        if (!FrameUtils.isFinite(vector)) {
            return null;
        }
        return new VectorData(vector);
    }

    @Override
    public Vector3d components() {
        return new Vector3d(components);
    }

    public double x() {
        return components.x;
    }

    public double y() {
        return components.y;
    }

    public double z() {
        return components.z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VectorData that = (VectorData) o;
        return Objects.equals(components, that.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(components);
    }

    @Override
    public String toString() {
        return "Vector[" + components.x + ", " + components.y + ", " + components.z + "]";
    }
}
