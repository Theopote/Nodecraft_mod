package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.datatypes.BoxFaceData;
import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.nodes.geometry.curves.util.PathUtils;
import com.nodecraft.nodesystem.util.VectorUtils;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.List;

/**
 * Shared frame / oriented-box helpers for architectural primitives.
 */
public final class ArchitecturalPrimitiveSupport {

    private static final double EPSILON = 1.0e-9d;

    private ArchitecturalPrimitiveSupport() {
    }

    public static @Nullable FaceFrame resolveFaceFrame(BoxFaceData face) {
        List<Vector3d> corners = face.getCorners();
        if (corners.size() < 4) {
            return null;
        }

        Vector3d c0 = corners.get(0);
        Vector3d c1 = corners.get(1);
        Vector3d c3 = corners.get(3);

        Vector3d xAxis = VectorUtils.safeSubtract(c1, c0);
        Vector3d yHint = VectorUtils.safeSubtract(c3, c0);
        double faceWidth = VectorUtils.safeLength(xAxis);
        double faceHeight = VectorUtils.safeLength(yHint);
        if (!Double.isFinite(faceWidth) || !Double.isFinite(faceHeight)
                || faceWidth <= EPSILON || faceHeight <= EPSILON) {
            return null;
        }

        xAxis = VectorUtils.safeNormalize(xAxis);
        if (xAxis == null) {
            return null;
        }
        Vector3d zAxis = VectorUtils.safeCross(xAxis, yHint);
        zAxis = VectorUtils.safeNormalize(zAxis);
        if (zAxis == null) {
            return null;
        }
        // FaceFrame.zAxis matches BoxFace.normal: always outward (leaving the box).
        if (zAxis.dot(face.getNormal()) < 0.0d) {
            zAxis.negate();
        }

        Vector3d yAxis = VectorUtils.safeNormalize(VectorUtils.safeCross(zAxis, xAxis));
        if (yAxis == null) {
            return null;
        }

        return new FaceFrame(face.getCenter(), xAxis, yAxis, zAxis, faceWidth, faceHeight);
    }

    public static @Nullable LineFrame resolveLineFrame(Vec3d start, Vec3d end) {
        Vector3d direction = VectorUtils.safeSubtract(
            new Vector3d(end.x, end.y, end.z),
            new Vector3d(start.x, start.y, start.z));
        double length = VectorUtils.safeLength(direction);
        if (!Double.isFinite(length) || length <= EPSILON) {
            return null;
        }

        Vector3d runAxis = VectorUtils.safeNormalize(direction);
        if (runAxis == null) {
            return null;
        }
        Vector3d upHint = Math.abs(runAxis.y) < 0.99d
            ? new Vector3d(0.0d, 1.0d, 0.0d)
            : new Vector3d(0.0d, 0.0d, 1.0d);

        Vector3d sideAxis = VectorUtils.safeCross(runAxis, upHint);
        if (VectorUtils.safeLength(sideAxis) <= EPSILON) {
            sideAxis = VectorUtils.safeCross(new Vector3d(1.0d, 0.0d, 0.0d), runAxis);
        }
        sideAxis = VectorUtils.safeNormalize(sideAxis);
        if (sideAxis == null) {
            return null;
        }

        Vector3d upAxis = VectorUtils.safeNormalize(VectorUtils.safeCross(sideAxis, runAxis));
        if (upAxis == null) {
            return null;
        }

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
    public static @Nullable LineFrame resolvePathChordFrame(@Nullable Object pathValue) {
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

    @Deprecated
    public static @Nullable LineFrame resolvePathAsLineFrame(@Nullable Object pathValue) {
        return resolvePathChordFrame(pathValue);
    }

    public static Matrix3d createOrientation(Vector3d xAxis, Vector3d yAxis, Vector3d zAxis) {
        return new Matrix3d(
            xAxis.x, yAxis.x, zAxis.x,
            xAxis.y, yAxis.y, zAxis.y,
            xAxis.z, yAxis.z, zAxis.z
        );
    }

    public static BoxGeometryData createOrientedBox(Vector3d center, Vector3d halfExtents, Vector3d xAxis, Vector3d yAxis, Vector3d zAxis) {
        return new BoxGeometryData(center, halfExtents, createOrientation(xAxis, yAxis, zAxis), true);
    }

    /**
     * Difference cutter for a face opening: thickness {@code depth} along the outward
     * face normal, <strong>centered on the face plane</strong> (extents {@code ±depth/2}).
     */
    public static BoxGeometryData createCenteredFaceOpening(
        Vector3d centerOnFace,
        FaceFrame frame,
        double width,
        double height,
        double depth
    ) {
        Vector3d halfExtents = new Vector3d(width / 2.0d, height / 2.0d, depth / 2.0d);
        return createOrientedBox(
            new Vector3d(centerOnFace),
            halfExtents,
            frame.xAxis(),
            frame.yAxis(),
            frame.zAxis()
        );
    }

    /**
     * @param zAxis outward face normal (same as {@link BoxFaceData#getNormal()})
     */
    public record FaceFrame(
        Vector3d center,
        Vector3d xAxis,
        Vector3d yAxis,
        Vector3d zAxis,
        double width,
        double height
    ) {
    }

    public record LineFrame(
        Vector3d start,
        Vector3d end,
        Vector3d runAxis,
        Vector3d upAxis,
        Vector3d sideAxis,
        double length
    ) {
    }
}