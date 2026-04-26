package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.PolyhedronOrientationUtil;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.List;
import java.util.Objects;

/**
 * Represents a regular octahedron centered at a point with a vertex radius and optional orientation.
 */
public class OctahedronGeometryData implements GeometryData {
    private final Vector3d center;
    private final double vertexRadius;
    private final Matrix3d orientation;

    public OctahedronGeometryData(Vector3d center, double vertexRadius) {
        this(center, vertexRadius, new Matrix3d().identity());
    }

    public OctahedronGeometryData(Vector3d center, double vertexRadius, Matrix3d orientation) {
        if (vertexRadius < 0.0d) {
            throw new IllegalArgumentException("Octahedron radius cannot be negative");
        }
        this.center = new Vector3d(center);
        this.vertexRadius = vertexRadius;
        this.orientation = PolyhedronOrientationUtil.copyValidatedRotation(orientation);
    }

    public Vector3d getCenter() {
        return new Vector3d(center);
    }

    public double getVertexRadius() {
        return vertexRadius;
    }

    public Matrix3d getOrientationMatrix() {
        return new Matrix3d(orientation);
    }

    private Vector3d worldVertex(double lx, double ly, double lz) {
        Vector3d v = new Vector3d(lx * vertexRadius, ly * vertexRadius, lz * vertexRadius);
        orientation.transform(v);
        v.add(center);
        return v;
    }

    public List<Vector3d> getVertices() {
        return List.of(
            worldVertex(1.0d, 0.0d, 0.0d),
            worldVertex(-1.0d, 0.0d, 0.0d),
            worldVertex(0.0d, 1.0d, 0.0d),
            worldVertex(0.0d, -1.0d, 0.0d),
            worldVertex(0.0d, 0.0d, 1.0d),
            worldVertex(0.0d, 0.0d, -1.0d)
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OctahedronGeometryData that)) return false;
        return Double.compare(that.vertexRadius, vertexRadius) == 0
            && Objects.equals(center, that.center)
            && PolyhedronOrientationUtil.rotationEquals(orientation, that.orientation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, vertexRadius, PolyhedronOrientationUtil.hashMatrix3(orientation));
    }

    @Override
    public String toString() {
        return "OctahedronGeometryData{center=" + center + ", vertexRadius=" + vertexRadius + ", orientation=" + orientation + "}";
    }
}
