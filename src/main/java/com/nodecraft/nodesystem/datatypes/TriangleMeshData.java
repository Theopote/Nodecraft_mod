package com.nodecraft.nodesystem.datatypes;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Indexed triangle mesh: shared vertices plus triangle index triples.
 */
public record TriangleMeshData(List<Vector3d> vertices, List<int[]> triangles) {

    private static final double EPS = 1.0e-12d;

    public TriangleMeshData {
        vertices = vertices == null ? List.of() : List.copyOf(vertices);
        triangles = triangles == null ? List.of() : List.copyOf(triangles);
    }

    /**
     * @return validated mesh, or {@code null} when invalid
     */
    public static @Nullable TriangleMeshData tryCreate(
            @Nullable List<Vector3d> vertices,
            @Nullable List<int[]> triangles
    ) {
        if (vertices == null || vertices.isEmpty() || triangles == null || triangles.isEmpty()) {
            return null;
        }
        for (Vector3d vertex : vertices) {
            if (vertex == null || !isFinite(vertex)) {
                return null;
            }
        }
        int vertexCount = vertices.size();
        for (int[] triangle : triangles) {
            if (triangle == null || triangle.length != 3) {
                return null;
            }
            for (int index : triangle) {
                if (index < 0 || index >= vertexCount) {
                    return null;
                }
            }
            if (triangleArea(vertices, triangle) <= EPS) {
                return null;
            }
        }
        List<Vector3d> copiedVertices = new ArrayList<>(vertices.size());
        for (Vector3d vertex : vertices) {
            copiedVertices.add(new Vector3d(vertex));
        }
        List<int[]> copiedTriangles = new ArrayList<>(triangles.size());
        for (int[] triangle : triangles) {
            copiedTriangles.add(new int[] {triangle[0], triangle[1], triangle[2]});
        }
        return new TriangleMeshData(copiedVertices, copiedTriangles);
    }

    @Override
    public List<Vector3d> vertices() {
        List<Vector3d> copied = new ArrayList<>(vertices.size());
        for (Vector3d vertex : vertices) {
            copied.add(new Vector3d(vertex));
        }
        return List.copyOf(copied);
    }

    @Override
    public List<int[]> triangles() {
        List<int[]> copied = new ArrayList<>(triangles.size());
        for (int[] triangle : triangles) {
            copied.add(new int[] {triangle[0], triangle[1], triangle[2]});
        }
        return List.copyOf(copied);
    }

    public int triangleCount() {
        return triangles.size();
    }

    private static boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static double triangleArea(List<Vector3d> vertices, int[] triangle) {
        Vector3d a = vertices.get(triangle[0]);
        Vector3d b = vertices.get(triangle[1]);
        Vector3d c = vertices.get(triangle[2]);
        Vector3d ab = new Vector3d(b).sub(a);
        Vector3d ac = new Vector3d(c).sub(a);
        return new Vector3d(ab).cross(ac).length();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TriangleMeshData that)) {
            return false;
        }
        return Objects.equals(vertices, that.vertices) && Objects.equals(triangles, that.triangles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vertices, triangles);
    }

    @Override
    public String toString() {
        return "TriangleMeshData{vertices=" + vertices.size() + ", triangles=" + triangles.size() + "}";
    }
}
