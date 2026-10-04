package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import org.joml.Vector3d;

/**
 * Sphere SDF primitive. Canonical: finite center, radius finite {@code > 0}.
 */
public record SphereSdfData(Vector3d center, double radius) implements SignedDistanceFieldData {
    public SphereSdfData(Vector3d center, double radius) {
        PrimitiveGeometryValidator.requireValid(PrimitiveGeometryValidator.validateSphere(center, radius));
        this.center = new Vector3d(center);
        this.radius = radius;
    }

    @Override
    public Vector3d center() {
        return new Vector3d(center);
    }

    @Override
    public double sampleDistance(Vector3d point) {
        return new Vector3d(point).sub(center).length() - radius;
    }
}
