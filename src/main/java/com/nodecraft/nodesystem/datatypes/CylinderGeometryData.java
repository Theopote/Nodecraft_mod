package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

import java.util.Objects;

/**
 * Represents a cylinder defined by an axis segment and radius.
 */
public class CylinderGeometryData {
    private final Vector3d start;
    private final Vector3d end;
    private final double radius;

    public CylinderGeometryData(Vector3d start, Vector3d end, double radius) {
        this.start = new Vector3d(start);
        this.end = new Vector3d(end);
        this.radius = radius;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CylinderGeometryData that)) return false;
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
        return "CylinderGeometryData{start=" + start
            + ", end=" + end
            + ", radius=" + radius + "}";
    }
}
