package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.PrimitiveGeometryValidator;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.joml.Vector3d;

import java.util.Objects;

/**
 * Analytic capsule: finite axis segment plus radius. Voxelizer/preview expand to
 * cylinder + two hemispheres; graph composites still count this as one leaf.
 */
public final class CapsuleGeometryData implements GeometryData {

    private final Vector3d start;
    private final Vector3d end;
    private final double radius;
    private final Vector3d unitAxis;

    public CapsuleGeometryData(Vector3d start, Vector3d end, double radius) {
        PrimitiveGeometryValidator.requireValid(
            PrimitiveGeometryValidator.validateCapsule(start, end, radius));
        Vector3d axis = PrimitiveGeometryValidator.requirePositiveAxis(start, end);
        double length = PrimitiveGeometryValidator.requirePositiveAxisLength(start, end);
        Vector3d unit = VectorUtils.normalizeByLength(axis, length);
        if (unit == null) {
            throw new IllegalArgumentException("Capsule axis length must be > 0");
        }
        this.start = new Vector3d(start);
        this.end = new Vector3d(end);
        this.radius = radius;
        this.unitAxis = unit;
    }

    public Vector3d getStart() {
        return new Vector3d(start);
    }

    public Vector3d getEnd() {
        return new Vector3d(end);
    }

    public double getRadius() {
        return radius;
    }

    public Vector3d getUnitAxis() {
        return new Vector3d(unitAxis);
    }

    public CylinderGeometryData cylinder() {
        return new CylinderGeometryData(start, end, radius);
    }

    public HemisphereGeometryData startHemisphere() {
        return new HemisphereGeometryData(start, new Vector3d(unitAxis).negate(), radius);
    }

    public HemisphereGeometryData endHemisphere() {
        return new HemisphereGeometryData(end, unitAxis, radius);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CapsuleGeometryData that)) {
            return false;
        }
        return Double.compare(that.radius, radius) == 0
            && Objects.equals(start, that.start)
            && Objects.equals(end, that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end, radius);
    }

    @Override
    public String toString() {
        return "CapsuleGeometryData{start=" + start + ", end=" + end + ", radius=" + radius + "}";
    }
}
