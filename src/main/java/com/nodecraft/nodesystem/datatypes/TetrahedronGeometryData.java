package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

import java.util.List;
import java.util.Objects;

/**
 * Represents a regular tetrahedron centered at a point with a given edge length.
 */
public class TetrahedronGeometryData implements GeometryData {
    private final Vector3d center;
    private final double edgeLength;

    public TetrahedronGeometryData(Vector3d center, double edgeLength) {
        if (edgeLength < 0.0d) {
            throw new IllegalArgumentException("Tetrahedron edge length cannot be negative");
        }
        this.center = new Vector3d(center);
        this.edgeLength = edgeLength;
    }

    public Vector3d getCenter() {
        return new Vector3d(center);
    }

    public double getEdgeLength() {
        return edgeLength;
    }

    public double getCircumradius() {
        return edgeLength * Math.sqrt(6.0d) / 4.0d;
    }

    public List<Vector3d> getVertices() {
        double circumR = getCircumradius();
        double h = circumR;
        double hBottom = circumR / 3.0d;
        double frontZ = 2.0d * circumR * Math.sqrt(2.0d) / 3.0d;
        double backZ = -circumR * Math.sqrt(2.0d) / 3.0d;
        double sideX = circumR * Math.sqrt(6.0d) / 3.0d;

        return List.of(
            new Vector3d(center.x, center.y + h, center.z),
            new Vector3d(center.x, center.y - hBottom, center.z + frontZ),
            new Vector3d(center.x - sideX, center.y - hBottom, center.z + backZ),
            new Vector3d(center.x + sideX, center.y - hBottom, center.z + backZ)
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TetrahedronGeometryData that)) return false;
        return Double.compare(that.edgeLength, edgeLength) == 0
            && Objects.equals(center, that.center);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, edgeLength);
    }

    @Override
    public String toString() {
        return "TetrahedronGeometryData{center=" + center + ", edgeLength=" + edgeLength + "}";
    }
}
