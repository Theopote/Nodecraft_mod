package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.FrameUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Typed POINT value: finite 3D location in continuous space.
 */
public record PointData(Vector3d position) {
    public PointData(Vector3d position) {
        this.position = position == null ? null : new Vector3d(position);
        if (!FrameUtils.isFinite(this.position)) {
            throw new IllegalArgumentException("POINT position must be finite");
        }
    }

    public PointData(double x, double y, double z) {
        this(new Vector3d(x, y, z));
    }

    /**
     * Builds a canonical point, or {@code null} when components are not finite.
     */
    public static @Nullable PointData canonical(@Nullable Vector3d position) {
        if (!FrameUtils.isFinite(position)) {
            return null;
        }
        return new PointData(position);
    }

    @Override
    public Vector3d position() {
        return new Vector3d(position);
    }

    public double getX() {
        return position.x;
    }

    public double getY() {
        return position.y;
    }

    public double getZ() {
        return position.z;
    }

    @Override
    public @NonNull String toString() {
        return "Point[" + position.x + ", " + position.y + ", " + position.z + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PointData pointData = (PointData) o;
        return Objects.equals(position, pointData.position);
    }
}
