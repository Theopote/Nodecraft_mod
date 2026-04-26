package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

/**
 * Sphere SDF primitive.
 */
public class SphereSdfData implements SignedDistanceFieldData {
    private final Vector3d center;
    private final double radius;

    public SphereSdfData(Vector3d center, double radius) {
        this.center = new Vector3d(center);
        this.radius = Math.max(0.0d, radius);
    }

    public Vector3d getCenter() {
        return new Vector3d(center);
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public double sampleDistance(Vector3d point) {
        return new Vector3d(point).sub(center).length() - radius;
    }
}
