package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.joml.Vector3d;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Solid hemisphere: full sphere intersected with the closed half-space
 * {@code dot(p - center, axis) >= 0}. The flat circular face lies in the plane through {@code center}
 * with normal {@code axis}; the dome bulges in the {@code axis} direction.
 */
public record HemisphereGeometryData(Vector3d center, Vector3d axis, double radius) implements GeometryData {
    public HemisphereGeometryData(Vector3d center, Vector3d axis, double radius) {
        PrimitiveGeometryValidator.requireValid(
            PrimitiveGeometryValidator.validateHemisphere(center, axis, radius));
        Vector3d unitAxis = VectorUtils.safeNormalize(axis);
        if (unitAxis == null) {
            throw new IllegalArgumentException("Hemisphere axis must be a usable direction");
        }
        this.center = new Vector3d(center);
        this.axis = unitAxis;
        this.radius = radius;
    }

    @Override
    public Vector3d center() {
        return new Vector3d(center);
    }

    /**
     * Unit vector from the flat face into the dome (solid lies where dot(p - center, axis) >= 0).
     */
    @Override
    public Vector3d axis() {
        return new Vector3d(axis);
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
    public @NonNull String toString() {
        return "HemisphereGeometryData{center=" + center + ", axis=" + axis + ", radius=" + radius + "}";
    }
}
