package com.nodecraft.nodesystem.datatypes;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Geometry value that represents a voxel-evaluated boolean intersection.
 * Only voxels occupied by both operands are kept.
 */
public record IntersectionGeometryData(GeometryData left, GeometryData right) implements GeometryData {

    public IntersectionGeometryData(GeometryData left, GeometryData right) {
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntersectionGeometryData(GeometryData left1, GeometryData right1))) return false;
        return Objects.equals(left, left1) && Objects.equals(right, right1);
    }

    @Override
    public @NonNull String toString() {
        return "IntersectionGeometryData{left=" + left + ", right=" + right + "}";
    }
}
