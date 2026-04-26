package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.PlatonicSolidTables;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Regular icosahedron centered at a point with a given edge length (same convention as {@link TetrahedronGeometryData}).
 */
public class IcosahedronGeometryData implements GeometryData {
    private final Vector3d center;
    private final double edgeLength;

    public IcosahedronGeometryData(Vector3d center, double edgeLength) {
        if (edgeLength < 0.0d) {
            throw new IllegalArgumentException("Icosahedron edge length cannot be negative");
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

    /** Circumscribed-sphere radius. */
    public double getCircumradius() {
        return PlatonicSolidTables.icosahedronCircumradiusFromEdge(edgeLength);
    }

    public List<Vector3d> getVertices() {
        Vector3d[] local = PlatonicSolidTables.icosahedronVertices(getCircumradius());
        List<Vector3d> world = new ArrayList<>(12);
        for (Vector3d v : local) {
            world.add(new Vector3d(center).add(v));
        }
        return world;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IcosahedronGeometryData that)) return false;
        return Double.compare(that.edgeLength, edgeLength) == 0
            && Objects.equals(center, that.center);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, edgeLength);
    }

    @Override
    public String toString() {
        return "IcosahedronGeometryData{center=" + center + ", edgeLength=" + edgeLength + "}";
    }
}
