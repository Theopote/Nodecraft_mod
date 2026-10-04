package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import org.joml.Vector3d;

/**
 * Axis-aligned box SDF primitive. Canonical: finite center, each half extent finite {@code > 0}.
 */
public class BoxSdfData implements SignedDistanceFieldData {
    private final Vector3d center;
    private final Vector3d halfExtents;

    public BoxSdfData(Vector3d center, Vector3d halfExtents) {
        PrimitiveGeometryValidator.requireValid(PrimitiveGeometryValidator.validateBox(center, halfExtents));
        this.center = new Vector3d(center);
        this.halfExtents = new Vector3d(halfExtents);
    }

    public Vector3d getCenter() {
        return new Vector3d(center);
    }

    public Vector3d getHalfExtents() {
        return new Vector3d(halfExtents);
    }

    @Override
    public double sampleDistance(Vector3d point) {
        Vector3d p = new Vector3d(point).sub(center);
        Vector3d q = new Vector3d(Math.abs(p.x), Math.abs(p.y), Math.abs(p.z)).sub(halfExtents);
        double outside = new Vector3d(Math.max(q.x, 0.0d), Math.max(q.y, 0.0d), Math.max(q.z, 0.0d)).length();
        double inside = Math.min(Math.max(q.x, Math.max(q.y, q.z)), 0.0d);
        return outside + inside;
    }
}
