package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single face of a box geometry with corner indices, world-space corners and derived plane data.
 *
 * <p>{@link #getNormal()} is always the <strong>outward</strong> face normal (leaving the box).
 * Architectural cutters must not treat {@code +normal} as “into the host”.
 */
public final class BoxFaceData {
    private final int index;
    private final String name;
    private final List<Integer> cornerIndices;
    private final List<Vector3d> corners;
    private final Vector3d center;
    private final Vector3d normal;
    private final PlaneData plane;

    public BoxFaceData(
        int index,
        String name,
        List<Integer> cornerIndices,
        List<Vector3d> corners,
        Vector3d center,
        Vector3d normal
    ) {
        this.index = index;
        this.name = Objects.requireNonNull(name, "name");
        this.cornerIndices = List.copyOf(cornerIndices);
        this.corners = copyVectors(corners);
        this.center = new Vector3d(center);
        this.normal = new Vector3d(normal);
        this.plane = new PlaneData(this.center, this.normal);
    }

    private static List<Vector3d> copyVectors(List<Vector3d> source) {
        List<Vector3d> result = new ArrayList<>(source.size());
        for (Vector3d vector : source) {
            result.add(new Vector3d(vector));
        }
        return List.copyOf(result);
    }

    public int getIndex() {
        return index;
    }

    public String getName() {
        return name;
    }

    public List<Integer> getCornerIndices() {
        return cornerIndices;
    }

    public int getEdgeCount() {
        return corners.size();
    }

    public List<int[]> getEdgeCornerIndexPairs() {
        List<int[]> result = new ArrayList<>(cornerIndices.size());
        for (int i = 0; i < cornerIndices.size(); i++) {
            result.add(new int[]{
                cornerIndices.get(i),
                cornerIndices.get((i + 1) % cornerIndices.size())
            });
        }
        return List.copyOf(result);
    }

    public List<Vector3d> getCorners() {
        return copyVectors(corners);
    }

    public Vector3d getCenter() {
        return new Vector3d(center);
    }

    public Vector3d getNormal() {
        return new Vector3d(normal);
    }

    public PlaneData getPlane() {
        return plane;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BoxFaceData that)) return false;
        return index == that.index
            && Objects.equals(name, that.name)
            && Objects.equals(cornerIndices, that.cornerIndices)
            && Objects.equals(corners, that.corners)
            && Objects.equals(center, that.center)
            && Objects.equals(normal, that.normal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, name, cornerIndices, corners, center, normal);
    }

    @Override
    public String toString() {
        return "BoxFaceData{index=" + index + ", name='" + name + "', center=" + center + ", normal=" + normal + "}";
    }
}
