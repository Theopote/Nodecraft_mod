package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.VectorData;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Shared vector validation helpers used across reference vector nodes.
 */
public final class VectorUtils {

    public static final double EPS = SpatialTolerance.EPS;
    public static final double EPS_SQ = SpatialTolerance.EPS_SQ;

    private VectorUtils() {
    }

    /** Strict VECTOR port: accepts {@link VectorData}, {@link Vector3d}, and legacy {@link Vec3d}. */
    public static @Nullable Vector3d toVector(@Nullable Object value) {
        if (value instanceof VectorData vectorData) {
            return vectorData.components();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vec3d) {
            return new Vector3d(vec3d.x, vec3d.y, vec3d.z);
        }
        return null;
    }

    /** Canonical typed output for VECTOR ports. */
    public static @Nullable VectorData toVectorPort(@Nullable Vector3d vector) {
        return VectorData.canonical(vector);
    }

    /** Canonical typed output for VECTOR_LIST ports. Any non-finite entry → {@code null}. */
    public static @Nullable List<VectorData> toVectorPortList(@Nullable Collection<Vector3d> vectors) {
        if (vectors == null) {
            return null;
        }
        List<VectorData> result = new ArrayList<>(vectors.size());
        for (Vector3d vector : vectors) {
            VectorData data = toVectorPort(vector);
            if (data == null) {
                return null;
            }
            result.add(data);
        }
        return List.copyOf(result);
    }

    public static boolean isFinite(@Nullable Vector3d vector) {
        return FrameUtils.isFinite(vector);
    }

    public static boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    /**
     * True when {@code distance >= 0} is finite and {@code distance²} is also finite.
     */
    public static boolean isFiniteSquaredDistance(double distance) {
        return isFinite(distance) && distance >= 0.0d && isFinite(distance * distance);
    }

    public static boolean isNonZero(@Nullable Vector3d vector) {
        double length = safeLength(vector);
        return isFinite(length) && length > EPS;
    }

    /**
     * Overflow-safe length via chained hypot. Returns NaN when input or result is non-finite.
     */
    public static double safeLength(@Nullable Vector3d vector) {
        if (!isFinite(vector)) {
            return Double.NaN;
        }
        double length = Math.hypot(Math.hypot(vector.x, vector.y), vector.z);
        return isFinite(length) ? length : Double.NaN;
    }

    /**
     * Normalizes via {@link #safeLength}. Returns null when length is non-finite or below EPS.
     */
    public static @Nullable Vector3d safeNormalize(@Nullable Vector3d vector) {
        double length = safeLength(vector);
        if (!isFinite(length) || length <= EPS) {
            return null;
        }
        Vector3d result = new Vector3d(vector).div(length);
        return isFinite(result) ? result : null;
    }

    /**
     * Divides a finite vector by a known finite length. Returns null when result is non-finite.
     */
    public static @Nullable Vector3d normalizeByLength(@Nullable Vector3d vector, double length) {
        if (!isFinite(vector) || !isFinite(length) || length <= EPS) {
            return null;
        }
        Vector3d result = new Vector3d(vector).div(length);
        return isFinite(result) ? result : null;
    }

    public static @Nullable Vector3d safeAdd(@Nullable Vector3d a, @Nullable Vector3d b) {
        if (!isFinite(a) || !isFinite(b)) {
            return null;
        }
        Vector3d result = new Vector3d(a).add(b);
        return isFinite(result) ? result : null;
    }

    public static @Nullable Vector3d safeSubtract(@Nullable Vector3d a, @Nullable Vector3d b) {
        if (!isFinite(a) || !isFinite(b)) {
            return null;
        }
        Vector3d result = new Vector3d(a).sub(b);
        return isFinite(result) ? result : null;
    }

    public static @Nullable Vector3d safeScale(@Nullable Vector3d vector, double scalar) {
        if (!isFinite(vector) || !isFinite(scalar)) {
            return null;
        }
        Vector3d result = new Vector3d(vector).mul(scalar);
        return isFinite(result) ? result : null;
    }

    public static double safeDot(@Nullable Vector3d a, @Nullable Vector3d b) {
        if (!isFinite(a) || !isFinite(b)) {
            return Double.NaN;
        }
        double dot = a.dot(b);
        return isFinite(dot) ? dot : Double.NaN;
    }

    public static @Nullable Vector3d safeCross(@Nullable Vector3d a, @Nullable Vector3d b) {
        if (!isFinite(a) || !isFinite(b)) {
            return null;
        }
        Vector3d result = new Vector3d(a).cross(b);
        return isFinite(result) ? result : null;
    }

    /**
     * Linear combination {@code a + t(b-a)} with finite-result fence.
     * Uses fused multiply-add for extrapolation stability. Does not clamp T.
     */
    public static @Nullable Vector3d safeLerp(@Nullable Vector3d a, @Nullable Vector3d b, double t) {
        if (!isFinite(a) || !isFinite(b) || !isFinite(t)) {
            return null;
        }
        double x = Math.fma(t, b.x, Math.fma(-t, a.x, a.x));
        double y = Math.fma(t, b.y, Math.fma(-t, a.y, a.y));
        double z = Math.fma(t, b.z, Math.fma(-t, a.z, a.z));
        Vector3d result = new Vector3d(x, y, z);
        return isFinite(result) ? result : null;
    }

    /**
     * Scalar linear interpolation with finite-result fence. Does not clamp T.
     * Uses fused multiply-add to avoid avoidable intermediate overflow on extrapolation.
     */
    public static double safeScalarLerp(double a, double b, double t) {
        if (!isFinite(a) || !isFinite(b) || !isFinite(t)) {
            return Double.NaN;
        }
        double result = Math.fma(t, b, Math.fma(-t, a, a));
        return isFinite(result) ? result : Double.NaN;
    }

    /**
     * Strict VECTOR_LIST resolution: null/empty/non-Collection → null;
     * any non-VECTOR or non-finite entry → null; otherwise full list (no filtering).
     */
    public static @Nullable List<Vector3d> resolveStrictVectorList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return null;
        }
        List<Vector3d> vectors = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            Vector3d vector = toVector(entry);
            if (!isFinite(vector)) {
                return null;
            }
            vectors.add(vector);
        }
        return List.copyOf(vectors);
    }

    /**
     * Strict VECTOR_LIST with a hard size budget. Size is checked before allocating the result.
     */
    public static @Nullable List<Vector3d> resolveStrictVectorListBounded(
            @Nullable Object value,
            int maxElements
    ) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return null;
        }
        if (maxElements < 1 || collection.size() > maxElements) {
            return null;
        }
        return resolveStrictVectorList(value);
    }
}
