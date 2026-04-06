package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a prism defined by an ordered base polygon and an extrusion vector.
 */
public class PrismGeometryData implements GeometryData {
    private final List<Vector3d> baseVertices;
    private final Vector3d extrusionVector;

    public PrismGeometryData(List<Vector3d> baseVertices, Vector3d extrusionVector) {
        if (baseVertices == null || baseVertices.size() < 3) {
            throw new IllegalArgumentException("Prism requires at least three base vertices");
        }

        List<Vector3d> copiedVertices = new ArrayList<>(baseVertices.size());
        for (Vector3d vertex : baseVertices) {
            copiedVertices.add(new Vector3d(vertex));
        }

        this.baseVertices = List.copyOf(copiedVertices);
        this.extrusionVector = new Vector3d(extrusionVector);
    }

    public List<Vector3d> getBaseVertices() {
        List<Vector3d> copiedVertices = new ArrayList<>(baseVertices.size());
        for (Vector3d vertex : baseVertices) {
            copiedVertices.add(new Vector3d(vertex));
        }
        return List.copyOf(copiedVertices);
    }

    public List<Vector3d> getTopVertices() {
        List<Vector3d> topVertices = new ArrayList<>(baseVertices.size());
        for (Vector3d vertex : baseVertices) {
            topVertices.add(new Vector3d(vertex).add(extrusionVector));
        }
        return List.copyOf(topVertices);
    }

    public Vector3d getExtrusionVector() {
        return new Vector3d(extrusionVector);
    }

    public double getHeight() {
        return extrusionVector.length();
    }

    public int getSideCount() {
        return baseVertices.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrismGeometryData that)) return false;
        return Objects.equals(baseVertices, that.baseVertices)
            && Objects.equals(extrusionVector, that.extrusionVector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseVertices, extrusionVector);
    }

    @Override
    public String toString() {
        return "PrismGeometryData{sideCount=" + getSideCount()
            + ", height=" + getHeight() + "}";
    }
}
