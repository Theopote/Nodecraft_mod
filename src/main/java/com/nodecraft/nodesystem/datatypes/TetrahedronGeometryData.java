package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.PolyhedronOrientationUtil;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a regular tetrahedron centered at a point with a given edge length and optional orientation.
 */
public class TetrahedronGeometryData implements GeometryData {
    private final Vector3d center;
    private final double edgeLength;
    private final Matrix3d orientation;

    public TetrahedronGeometryData(Vector3d center, double edgeLength) {
        this(center, edgeLength, new Matrix3d().identity());
    }

    public TetrahedronGeometryData(Vector3d center, double edgeLength, Matrix3d orientation) {
        if (edgeLength < 0.0d) {
            throw new IllegalArgumentException("Tetrahedron edge length cannot be negative");
        }
        this.center = new Vector3d(center);
        this.edgeLength = edgeLength;
        this.orientation = PolyhedronOrientationUtil.copyValidatedRotation(orientation);
    }

    public Vector3d getCenter() {
        return new Vector3d(center);
    }

    public double getEdgeLength() {
        return edgeLength;
    }

    public Matrix3d getOrientationMatrix() {
        return new Matrix3d(orientation);
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

        Vector3d[] local = {
            new Vector3d(0.0d, h, 0.0d),
            new Vector3d(0.0d, -hBottom, frontZ),
            new Vector3d(-sideX, -hBottom, backZ),
            new Vector3d(sideX, -hBottom, backZ)
        };
        List<Vector3d> world = new ArrayList<>(4);
        Vector3d tmp = new Vector3d();
        for (Vector3d v : local) {
            orientation.transform(v, tmp);
            world.add(new Vector3d(center).add(tmp));
        }
        return world;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TetrahedronGeometryData that)) return false;
        return Double.compare(that.edgeLength, edgeLength) == 0
            && Objects.equals(center, that.center)
            && PolyhedronOrientationUtil.rotationEquals(orientation, that.orientation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, edgeLength, PolyhedronOrientationUtil.hashMatrix3(orientation));
    }

    @Override
    public String toString() {
        return "TetrahedronGeometryData{center=" + center + ", edgeLength=" + edgeLength + ", orientation=" + orientation + "}";
    }
}
