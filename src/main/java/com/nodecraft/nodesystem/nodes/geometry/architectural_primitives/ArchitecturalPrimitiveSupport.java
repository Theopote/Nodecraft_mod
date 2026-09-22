package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.List;

final class ArchitecturalPrimitiveSupport {

    private static final double EPSILON = 1.0e-9d;

    private ArchitecturalPrimitiveSupport() {
    }

    static @Nullable FaceFrame resolveFaceFrame(BoxFaceData face) {
        List<Vector3d> corners = face.getCorners();
        if (corners.size() < 4) {
            return null;
        }

        Vector3d c0 = corners.get(0);
        Vector3d c1 = corners.get(1);
        Vector3d c3 = corners.get(3);

        Vector3d xAxis = new Vector3d(c1).sub(c0);
        Vector3d yHint = new Vector3d(c3).sub(c0);
        double faceWidth = xAxis.length();
        double faceHeight = yHint.length();
        if (faceWidth <= EPSILON || faceHeight <= EPSILON) {
            return null;
        }

        xAxis.normalize();
        Vector3d zAxis = new Vector3d(xAxis).cross(yHint);
        if (zAxis.lengthSquared() <= EPSILON * EPSILON) {
            return null;
        }
        zAxis.normalize();
        if (zAxis.dot(face.getNormal()) < 0.0d) {
            zAxis.negate();
        }

        Vector3d yAxis = new Vector3d(zAxis).cross(xAxis);
        if (yAxis.lengthSquared() <= EPSILON * EPSILON) {
            return null;
        }
        yAxis.normalize();

        return new FaceFrame(face.getCenter(), xAxis, yAxis, zAxis, faceWidth, faceHeight);
    }

    static @Nullable LineFrame resolveLineFrame(Vec3d start, Vec3d end) {
        Vector3d direction = new Vector3d(end.x - start.x, end.y - start.y, end.z - start.z);
        double length = direction.length();
        if (length <= EPSILON) {
            return null;
        }

        Vector3d runAxis = direction.normalize(new Vector3d());
        Vector3d upHint = Math.abs(runAxis.y) < 0.99d
            ? new Vector3d(0.0d, 1.0d, 0.0d)
            : new Vector3d(0.0d, 0.0d, 1.0d);

        Vector3d sideAxis = new Vector3d(runAxis).cross(upHint);
        if (sideAxis.lengthSquared() <= EPSILON * EPSILON) {
            sideAxis.set(1.0d, 0.0d, 0.0d).cross(runAxis);
        }
        if (sideAxis.lengthSquared() <= EPSILON * EPSILON) {
            return null;
        }
        sideAxis.normalize();

        Vector3d upAxis = new Vector3d(sideAxis).cross(runAxis);
        if (upAxis.lengthSquared() <= EPSILON * EPSILON) {
            return null;
        }
        upAxis.normalize();

        return new LineFrame(
            new Vector3d(start.x, start.y, start.z),
            new Vector3d(end.x, end.y, end.z),
            runAxis,
            upAxis,
            sideAxis,
            length
        );
    }

    /**
     * Resolves a PATH into a straight chord frame using first and last vertices.
     * Prefer {@link ArchitecturalPathSupport} for path-following components.
     * Keep this only for layouts that intentionally need a straight approximation
     * (e.g. spiral/U stair plan orientation).
     */
    static @Nullable LineFrame resolvePathChordFrame(@Nullable Object pathValue) {
        List<Vector3d> points = PathUtils.resolvePath(pathValue);
        if (points == null || points.size() < 2) {
            return null;
        }
        Vector3d start = points.getFirst();
        Vector3d end = points.getLast();
        return resolveLineFrame(
            new Vec3d(start.x, start.y, start.z),
            new Vec3d(end.x, end.y, end.z)
        );
    }

    /** @deprecated Use {@link #resolvePathChordFrame(Object)}. */
    @Deprecated
    static @Nullable LineFrame resolvePathAsLineFrame(@Nullable Object pathValue) {
        return resolvePathChordFrame(pathValue);
    }

    static Matrix3d createOrientation(Vector3d xAxis, Vector3d yAxis, Vector3d zAxis) {
        return new Matrix3d(
            xAxis.x, yAxis.x, zAxis.x,
            xAxis.y, yAxis.y, zAxis.y,
            xAxis.z, yAxis.z, zAxis.z
        );
    }

    static BoxGeometryData createOrientedBox(Vector3d center, Vector3d halfExtents, Vector3d xAxis, Vector3d yAxis, Vector3d zAxis) {
        return new BoxGeometryData(center, halfExtents, createOrientation(xAxis, yAxis, zAxis), true);
    }

    static int resolvePositiveInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        return fallback;
    }

    static double resolvePositiveDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return Math.max(EPSILON, number.doubleValue());
        }
        return fallback;
    }

    static double resolveNonNegativeDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return Math.max(0.0d, number.doubleValue());
        }
        return fallback;
    }

    record FaceFrame(
        Vector3d center,
        Vector3d xAxis,
        Vector3d yAxis,
        Vector3d zAxis,
        double width,
        double height
    ) {
    }

    record LineFrame(
        Vector3d start,
        Vector3d end,
        Vector3d runAxis,
        Vector3d upAxis,
        Vector3d sideAxis,
        double length
    ) {
    }
}