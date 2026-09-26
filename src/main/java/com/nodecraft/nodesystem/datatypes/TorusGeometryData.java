package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Represents an oriented torus geometry.
 */
public record TorusGeometryData(Vector3d center, Vector3d axis, double majorRadius,
                                double minorRadius) implements GeometryData {
    public TorusGeometryData(Vector3d center, Vector3d axis, double majorRadius, double minorRadius) {
        this.center = new Vector3d(center);
        this.axis = new Vector3d(axis).normalize();
        this.majorRadius = majorRadius;
        this.minorRadius = minorRadius;
    }

    @Override
    public Vector3d center() {
        return new Vector3d(center);
    }

    @Override
    public Vector3d axis() {
        return new Vector3d(axis);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TorusGeometryData that)) return false;
        return Double.compare(majorRadius, that.majorRadius) == 0
                && Double.compare(minorRadius, that.minorRadius) == 0
                && Objects.equals(center, that.center)
                && Objects.equals(axis, that.axis);
    }

    @Override
    public @NonNull String toString() {
        return "TorusGeometryData{center=" + center
                + ", axis=" + axis
                + ", majorRadius=" + majorRadius
                + ", minorRadius=" + minorRadius + "}";
    }
}
