package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.PlatonicSolidTables;
import com.nodecraft.nodesystem.util.PolyhedronOrientationUtil;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Regular dodecahedron centered at a point with a given edge length.
 */
public class DodecahedronGeometryData implements GeometryData {
    private final Vector3d center;
    private final double edgeLength;
    private final Matrix3d orientation;

    public DodecahedronGeometryData(Vector3d center, double edgeLength) {
        this(center, edgeLength, new Matrix3d().identity());
    }

    public DodecahedronGeometryData(Vector3d center, double edgeLength, Matrix3d orientation) {
        if (edgeLength < 0.0d) {
            throw new IllegalArgumentException("Dodecahedron edge length cannot be negative");
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
        return PlatonicSolidTables.dodecahedronCircumradiusFromEdge(edgeLength);
    }

    public List<Vector3d> getVertices() {
        Vector3d[] local = PlatonicSolidTables.dodecahedronVertices(getCircumradius());
        List<Vector3d> world = new ArrayList<>(20);
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
        if (!(o instanceof DodecahedronGeometryData that)) return false;
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
        return "DodecahedronGeometryData{center=" + center + ", edgeLength=" + edgeLength + ", orientation=" + orientation + "}";
    }
}
