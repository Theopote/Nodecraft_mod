package com.nodecraft.nodesystem.datatypes;

import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents an axis-aligned or oriented box geometry in local space.
 */
public class BoxGeometryData implements GeometryData {
    private static final int[][] FACE_CORNER_INDICES = {
        {0, 1, 5, 4}, // bottom (-Y)
        {3, 7, 6, 2}, // top (+Y)
        {0, 4, 7, 3}, // left (-X)
        {1, 2, 6, 5}, // right (+X)
        {0, 3, 2, 1}, // back (-Z)
        {4, 5, 6, 7}  // front (+Z)
    };

    private static final String[] FACE_NAMES = {
        "Bottom",
        "Top",
        "Left",
        "Right",
        "Back",
        "Front"
    };

    private static final Vector3d[] FACE_LOCAL_NORMALS = {
        new Vector3d(0.0d, -1.0d, 0.0d),
        new Vector3d(0.0d, 1.0d, 0.0d),
        new Vector3d(-1.0d, 0.0d, 0.0d),
        new Vector3d(1.0d, 0.0d, 0.0d),
        new Vector3d(0.0d, 0.0d, -1.0d),
        new Vector3d(0.0d, 0.0d, 1.0d)
    };

    private final Vector3d center;
    private final Vector3d halfExtents;
    private final Matrix3d orientationMatrix;
    private final boolean oriented;

    public BoxGeometryData(Vector3d center, Vector3d halfExtents) {
        this(center, halfExtents, new Matrix3d().identity(), false);
    }

    public BoxGeometryData(Vector3d center, Vector3d halfExtents, Matrix3d orientationMatrix, boolean oriented) {
        this.center = new Vector3d(center);
        this.halfExtents = new Vector3d(halfExtents);
        this.orientationMatrix = new Matrix3d(orientationMatrix);
        this.oriented = oriented;
    }

    public Vector3d getCenter() {
        return new Vector3d(center);
    }

    public Vector3d getHalfExtents() {
        return new Vector3d(halfExtents);
    }

    public Matrix3d getOrientationMatrix() {
        return new Matrix3d(orientationMatrix);
    }

    public boolean isOriented() {
        return oriented;
    }

    public List<Vector3d> getCorners() {
        List<Vector3d> corners = new ArrayList<>(8);
        corners.add(transformLocalCorner(-halfExtents.x, -halfExtents.y, -halfExtents.z));
        corners.add(transformLocalCorner(halfExtents.x, -halfExtents.y, -halfExtents.z));
        corners.add(transformLocalCorner(halfExtents.x, halfExtents.y, -halfExtents.z));
        corners.add(transformLocalCorner(-halfExtents.x, halfExtents.y, -halfExtents.z));
        corners.add(transformLocalCorner(-halfExtents.x, -halfExtents.y, halfExtents.z));
        corners.add(transformLocalCorner(halfExtents.x, -halfExtents.y, halfExtents.z));
        corners.add(transformLocalCorner(halfExtents.x, halfExtents.y, halfExtents.z));
        corners.add(transformLocalCorner(-halfExtents.x, halfExtents.y, halfExtents.z));
        return List.copyOf(corners);
    }

    public List<BoxFaceData> getFaces() {
        List<Vector3d> corners = getCorners();
        List<BoxFaceData> faces = new ArrayList<>(FACE_CORNER_INDICES.length);

        for (int faceIndex = 0; faceIndex < FACE_CORNER_INDICES.length; faceIndex++) {
            int[] indices = FACE_CORNER_INDICES[faceIndex];
            List<Integer> cornerIndices = List.of(indices[0], indices[1], indices[2], indices[3]);
            List<Vector3d> faceCorners = List.of(
                new Vector3d(corners.get(indices[0])),
                new Vector3d(corners.get(indices[1])),
                new Vector3d(corners.get(indices[2])),
                new Vector3d(corners.get(indices[3]))
            );

            Vector3d faceCenter = new Vector3d();
            for (Vector3d corner : faceCorners) {
                faceCenter.add(corner);
            }
            faceCenter.div(4.0d);

            Vector3d normal = new Vector3d(FACE_LOCAL_NORMALS[faceIndex]);
            orientationMatrix.transform(normal);
            if (normal.lengthSquared() > 1.0e-12d) {
                normal.normalize();
            }

            faces.add(new BoxFaceData(
                faceIndex,
                FACE_NAMES[faceIndex],
                cornerIndices,
                faceCorners,
                faceCenter,
                normal
            ));
        }

        return List.copyOf(faces);
    }

    private Vector3d transformLocalCorner(double x, double y, double z) {
        Vector3d corner = new Vector3d(x, y, z);
        orientationMatrix.transform(corner);
        corner.add(center);
        return corner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BoxGeometryData that)) return false;
        return oriented == that.oriented
            && Objects.equals(center, that.center)
            && Objects.equals(halfExtents, that.halfExtents)
            && Objects.equals(orientationMatrix, that.orientationMatrix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(center, halfExtents, orientationMatrix, oriented);
    }

    @Override
    public String toString() {
        return "BoxGeometryData{center=" + center
            + ", halfExtents=" + halfExtents
            + ", oriented=" + oriented + "}";
    }
}
