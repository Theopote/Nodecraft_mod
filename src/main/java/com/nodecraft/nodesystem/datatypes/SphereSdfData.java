package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

/**
 * Sphere SDF primitive.
 */
public record SphereSdfData(Vector3d center, double radius) implements SignedDistanceFieldData {
    public SphereSdfData(Vector3d center, double radius) {
        this.center = new Vector3d(center);
        this.radius = Math.max(0.0d, radius);
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
