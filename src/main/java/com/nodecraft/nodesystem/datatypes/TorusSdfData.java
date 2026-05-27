package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector2d;
import org.joml.Vector3d;

/**
 * Torus SDF primitive around Y axis.
 */
public class TorusSdfData implements SignedDistanceFieldData {
    private final Vector3d center;
    private final double majorRadius;
    private final double minorRadius;

    public TorusSdfData(Vector3d center, double majorRadius, double minorRadius) {
        this.center = new Vector3d(center);
        this.majorRadius = Math.max(0.0d, majorRadius);
        this.minorRadius = Math.max(0.0d, minorRadius);
    }

    public Vector3d getCenter() {
        return new Vector3d(center);
    }

    public double getMajorRadius() {
        return majorRadius;
    }

    public double getMinorRadius() {
        return minorRadius;
    }

    @Override
    public double sampleDistance(Vector3d point) {
        Vector3d p = new Vector3d(point).sub(center);
        Vector2d q = new Vector2d(new Vector2d(p.x, p.z).length() - majorRadius, p.y);
        return q.length() - minorRadius;
    }
}
