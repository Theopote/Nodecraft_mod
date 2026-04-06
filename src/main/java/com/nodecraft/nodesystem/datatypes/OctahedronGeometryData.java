package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

import java.util.List;
import java.util.Objects;

/**
 * Represents a regular octahedron centered at a point with a vertex radius.
 */
public class OctahedronGeometryData implements GeometryData {
    private final Vector3d center;
    private final double vertexRadius;

    public OctahedronGeometryData(Vector3d center, double vertexRadius) {
        if (vertexRadius < 0.0d) {
            throw new IllegalArgumentException("Octahedron radius cannot be negative");
        }
        this.center = new Vector3d(center);
        this.vertexRadius = vertexRadius;
    }

    public Vector3d getCenter() {
        return new Vector3d(center);
    }

    public double getVertexRadius() {
        return vertexRadius;
    }

    public List<Vector3d> getVertices() {
        return List.of(
            new Vector3d(center.x + vertexRadius, center.y, center.z),
            new Vector3d(center.x - vertexRadius, center.y, center.z),
            new Vector3d(center.x, center.y + vertexRadius, center.z),
            new Vector3d(center.x, center.y - vertexRadius, center.z),
            new Vector3d(center.x, center.y, center.z + vertexRadius),
            new Vector3d(center.x, center.y, center.z - vertexRadius)
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OctahedronGeometryData that)) return false;
        return Double.compare(that.vertexRadius, vertexRadius) == 0
            && Objects.equals(center, that.center);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, vertexRadius);
    }

    @Override
    public String toString() {
        return "OctahedronGeometryData{center=" + center + ", vertexRadius=" + vertexRadius + "}";
    }
}
