package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector2d;
import org.joml.Vector3d;

/**
 * Torus SDF primitive around Y axis.
 */
public record TorusSdfData(Vector3d center, double majorRadius, double minorRadius) implements SignedDistanceFieldData {
    public TorusSdfData(Vector3d center, double majorRadius, double minorRadius) {
        this.center = new Vector3d(center);
        this.majorRadius = Math.max(0.0d, majorRadius);
        this.minorRadius = Math.max(0.0d, minorRadius);
    }

    @Override
    public Vector3d center() {
        return new Vector3d(center);
    }

    @Override
    public double sampleDistance(Vector3d point) {
        Vector3d p = new Vector3d(point).sub(center);
        Vector2d q = new Vector2d(new Vector2d(p.x, p.z).length() - majorRadius, p.y);
        return q.length() - minorRadius;
    }
}
