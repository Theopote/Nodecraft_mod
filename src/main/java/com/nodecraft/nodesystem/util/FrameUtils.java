package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared frame construction helpers used across frame/plane nodes.
 */
public final class FrameUtils {

    public static final double EPS = SpatialTolerance.EPS;
    public static final double EPS_SQ = SpatialTolerance.EPS_SQ;

    private static final Vector3d WORLD_X = new Vector3d(1.0d, 0.0d, 0.0d);
    private static final Vector3d WORLD_Y = new Vector3d(0.0d, 1.0d, 0.0d);
    private static final Vector3d WORLD_Z = new Vector3d(0.0d, 0.0d, 1.0d);

    private FrameUtils() {
    }

    /**
     * Strict FRAME_LIST resolution: null / non-List / empty → null;
     * any non-{@link FrameData} or non-canonical frame → null; otherwise a copy (no filtering).
     */
    public static @Nullable List<FrameData> resolveStrictFrameList(@Nullable Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<FrameData> frames = new ArrayList<>(list.size());
        for (Object entry : list) {
            if (!(entry instanceof FrameData frame) || !frame.isCanonical()) {
                return null;
            }
            frames.add(frame);
        }
        return List.copyOf(frames);
    }

    /**
     * Like {@link #resolveStrictFrameList} but fails closed when {@code list.size() > maxElements}
     * before allocating the copy.
     */
    public static @Nullable List<FrameData> resolveStrictFrameListBounded(
            @Nullable Object value,
            int maxElements
    ) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        if (maxElements < 1 || list.size() > maxElements) {
            return null;
        }
        return resolveStrictFrameList(value);
    }

    public static @Nullable Vector3d resolvePoint(@Nullable Object value) {
        return SpatialValueResolver.resolvePoint(value);
    }

    public static @Nullable Vector3d resolveVector(@Nullable Object value) {
        return SpatialValueResolver.resolveVector(value);
    }

    public static boolean isFinite(@Nullable Vector3d vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    public static boolean isUsableAxis(@Nullable Vector3d axis) {
        return VectorUtils.isNonZero(axis);
    }

    public static @Nullable Vector3d normalizedDirection(@Nullable Vector3d from, @Nullable Vector3d to) {
        return unit(VectorUtils.safeSubtract(to, from));
    }

    /**
     * Returns true when both axes are usable and parallel within epsilon.
     */
    public static boolean areParallel(@Nullable Vector3d a, @Nullable Vector3d b) {
        Vector3d an = unit(a);
        Vector3d bn = unit(b);
        if (an == null || bn == null) {
            return false;
        }
        Vector3d cross = VectorUtils.safeCross(an, bn);
        double crossLength = VectorUtils.safeLength(cross);
        return Double.isFinite(crossLength) && crossLength <= EPS;
    }

    /**
     * Builds a right-handed orthonormal frame on a plane. Z aligns with the plane normal;
     * X is {@code xHint} projected onto the plane, with a deterministic cardinal fallback.
     */
    public static @Nullable FrameData fromPlane(PlaneData plane, @Nullable Vector3d xHint) {
        if (plane == null) {
            return null;
        }
        return fromNormal(plane.getPoint(), plane.getNormal(), xHint);
    }

    /**
     * Like {@link #fromPlane} but requires {@code xHint} to project to a usable in-plane axis —
     * no cardinal fallback. Used when an X Hint port is connected (fail-closed).
     */
    public static @Nullable FrameData fromPlaneRequireHint(PlaneData plane, Vector3d xHint) {
        if (plane == null || !isUsableAxis(xHint)) {
            return null;
        }
        if (!isFinite(plane.getPoint()) || !isUsableAxis(plane.getNormal())) {
            return null;
        }
        Vector3d z = unit(plane.getNormal());
        if (z == null) {
            return null;
        }
        Vector3d x = unit(projectOntoPlane(xHint, z));
        if (x == null) {
            return null;
        }
        Vector3d y = unit(VectorUtils.safeCross(z, x));
        if (y == null) {
            return null;
        }
        return FrameData.orthonormal(plane.getPoint(), x, y, z);
    }

    /**
     * Builds a right-handed orthonormal frame tangent to a surface with the given normal.
     * Z aligns with {@code normal}; X is {@code xHint} projected onto the tangent plane.
     */
    public static @Nullable FrameData fromNormal(
            @Nullable Vector3d origin,
            @Nullable Vector3d normal,
            @Nullable Vector3d xHint
    ) {
        if (!isFinite(origin) || !isUsableAxis(normal)) {
            return null;
        }
        Vector3d z = unit(normal);
        if (z == null) {
            return null;
        }

        Vector3d x = projectOntoPlane(resolveHint(xHint), z);
        if (!isUsableAxis(x)) {
            x = projectOntoPlane(leastAlignedCardinal(z), z);
        }
        if (!isUsableAxis(x)) {
            for (Vector3d cardinal : new Vector3d[] {WORLD_X, WORLD_Y, WORLD_Z}) {
                x = projectOntoPlane(cardinal, z);
                if (isUsableAxis(x)) {
                    break;
                }
            }
        }
        x = unit(x);
        if (x == null) {
            return null;
        }

        Vector3d y = unit(VectorUtils.safeCross(z, x));
        if (y == null) {
            return null;
        }

        return FrameData.orthonormal(origin, x, y, z);
    }

    /**
     * Like {@link #fromNormal} but requires a usable projected {@code xHint} — no cardinal fallback.
     */
    public static @Nullable FrameData fromNormalRequireHint(
            @Nullable Vector3d origin,
            @Nullable Vector3d normal,
            @Nullable Vector3d xHint
    ) {
        if (!isFinite(origin) || !isUsableAxis(normal) || !isUsableAxis(xHint)) {
            return null;
        }
        Vector3d z = unit(normal);
        if (z == null) {
            return null;
        }
        Vector3d x = unit(projectOntoPlane(new Vector3d(xHint), z));
        if (x == null) {
            return null;
        }
        Vector3d y = unit(VectorUtils.safeCross(z, x));
        if (y == null) {
            return null;
        }
        return FrameData.orthonormal(origin, x, y, z);
    }

    /**
     * Projects {@code hint} onto the plane perpendicular to {@code normal}.
     * Returns null when the projection is zero-length.
     */
    public static @Nullable Vector3d projectOntoTangentPlane(@Nullable Vector3d hint, @Nullable Vector3d normal) {
        if (!isUsableAxis(hint) || !isUsableAxis(normal)) {
            return null;
        }
        Vector3d z = unit(normal);
        if (z == null) {
            return null;
        }
        return unit(projectOntoPlane(new Vector3d(hint), z));
    }

    /**
     * Local axis that should align with the surface normal when building a frame.
     */
    public enum LocalUpAxis {
        X,
        Y,
        Z
    }

    /**
     * Right-handed frame: selected local axis aligns with {@code +normal}; {@code tangent}
     * lies in the tangent plane. Axes are orthonormalized without flipping the up axis.
     */
    public static @Nullable FrameData fromNormalUpAxis(
            @Nullable Vector3d origin,
            @Nullable Vector3d unitNormal,
            @Nullable Vector3d unitTangent,
            @Nullable LocalUpAxis axis
    ) {
        if (!isFinite(origin) || !isUsableAxis(unitNormal) || !isUsableAxis(unitTangent)) {
            return null;
        }
        LocalUpAxis resolved = axis == null ? LocalUpAxis.Y : axis;
        Vector3d up = unit(unitNormal);
        Vector3d tangent = unit(unitTangent);
        if (up == null || tangent == null) {
            return null;
        }
        double alignment = VectorUtils.safeDot(up, tangent);
        if (!Double.isFinite(alignment) || Math.abs(alignment) > 1.0d - EPS) {
            return null;
        }

        Vector3d x;
        Vector3d y;
        Vector3d z;
        switch (resolved) {
            case X -> {
                x = new Vector3d(up);
                y = new Vector3d(tangent);
                z = VectorUtils.safeCross(up, tangent);
            }
            case Y -> {
                x = new Vector3d(tangent);
                y = new Vector3d(up);
                // Z = X × Y = tangent × up (not up × tangent) so orthonormal() keeps +Y
                z = VectorUtils.safeCross(tangent, up);
            }
            case Z -> {
                x = new Vector3d(tangent);
                y = VectorUtils.safeCross(up, tangent);
                z = new Vector3d(up);
            }
            default -> {
                return null;
            }
        }
        z = unit(z);
        y = unit(y);
        if (z == null || y == null) {
            return null;
        }
        return FrameData.orthonormal(origin, x, y, z);
    }

    private static @Nullable Vector3d unit(@Nullable Vector3d vector) {
        return VectorUtils.safeNormalize(vector);
    }

    private static @Nullable Vector3d resolveHint(@Nullable Vector3d xHint) {
        if (xHint != null && isUsableAxis(xHint)) {
            return new Vector3d(xHint);
        }
        return null;
    }

    private static Vector3d projectOntoPlane(@Nullable Vector3d vector, Vector3d normal) {
        if (vector == null || !isUsableAxis(vector)) {
            return new Vector3d();
        }
        double alongNormal = VectorUtils.safeDot(vector, normal);
        Vector3d scaled = VectorUtils.safeScale(normal, alongNormal);
        Vector3d projected = VectorUtils.safeSubtract(vector, scaled);
        return projected == null ? new Vector3d() : projected;
    }

    private static Vector3d leastAlignedCardinal(Vector3d axis) {
        double ax = Math.abs(axis.x);
        double ay = Math.abs(axis.y);
        double az = Math.abs(axis.z);
        if (ax <= ay && ax <= az) {
            return WORLD_X;
        }
        if (ay <= az) {
            return WORLD_Y;
        }
        return WORLD_Z;
    }
}
