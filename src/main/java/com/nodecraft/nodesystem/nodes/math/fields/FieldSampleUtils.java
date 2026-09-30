package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Field-node spatial boundary helpers. Locations use {@link SpatialValueResolver#resolvePoint};
 * directions use {@link SpatialValueResolver#resolveVector}.
 * <p>
 * Sampling helpers enforce Field v1 finite boundary: Valid=true only for finite outputs.
 * Batch sample points use {@link #resolvePointListStrict} (1:1, fail closed)—not the
 * filtering {@link SpatialValueResolver#resolvePointList}.
 * Single-point sample nodes use {@link #resolveFinitePoint} (finite x/y/z gate).
 */
public final class FieldSampleUtils {

    public static final String ERROR_INVALID_INPUT = "invalid_input";
    public static final String ERROR_INVALID_POINTS = "invalid_points";
    public static final String ERROR_INVALID_FIELD = "invalid_field";
    public static final String ERROR_OUTPUT_BUDGET_EXCEEDED = "output_budget_exceeded";

    record ScalarSample(double value, boolean valid) {
    }

    record VectorSample(@Nullable Vector3d vector, boolean valid) {
    }

    record PointListResult(@Nullable List<Vector3d> points, boolean valid, @Nullable String error) {
        static PointListResult ok(List<Vector3d> points) {
            return new PointListResult(points, true, null);
        }

        static PointListResult invalid(String error) {
            return new PointListResult(null, false, error);
        }
    }

    private FieldSampleUtils() {
    }

    static @Nullable Vector3d resolvePoint(Object value) {
        return SpatialValueResolver.resolvePoint(value);
    }

    /**
     * Resolves a query point and requires finite x/y/z. Does not alter
     * {@link SpatialValueResolver#resolvePoint}.
     */
    static @Nullable Vector3d resolveFinitePoint(Object value) {
        Vector3d resolved = SpatialValueResolver.resolvePoint(value);
        if (resolved == null
                || !Double.isFinite(resolved.x)
                || !Double.isFinite(resolved.y)
                || !Double.isFinite(resolved.z)) {
            return null;
        }
        return resolved;
    }

    static @Nullable Vector3d resolveVector(Object value) {
        return SpatialValueResolver.resolveVector(value);
    }

    /**
     * Central-difference SDF gradient direction (no {@code /2h}; same scale as historical path).
     * Writes a unit gradient, {@code (0,0,0)} for a true flat SDF, or NaN components on
     * numerical failure (non-finite inputs, unresolvable perturbation, overflow, etc.).
     */
    static void sampleSdfGradientDirection(SignedDistanceFieldData sdf, Vector3d point, double h,
                                           Vector3d dest) {
        if (point == null
                || !Double.isFinite(point.x)
                || !Double.isFinite(point.y)
                || !Double.isFinite(point.z)
                || !Double.isFinite(h)
                || h <= 0.0d) {
            dest.set(Double.NaN, Double.NaN, Double.NaN);
            return;
        }
        if ((point.x + h) == point.x || (point.x - h) == point.x
                || (point.y + h) == point.y || (point.y - h) == point.y
                || (point.z + h) == point.z || (point.z - h) == point.z) {
            dest.set(Double.NaN, Double.NaN, Double.NaN);
            return;
        }

        double dxPos = sdf.sampleDistance(new Vector3d(point.x + h, point.y, point.z));
        double dxNeg = sdf.sampleDistance(new Vector3d(point.x - h, point.y, point.z));
        double dyPos = sdf.sampleDistance(new Vector3d(point.x, point.y + h, point.z));
        double dyNeg = sdf.sampleDistance(new Vector3d(point.x, point.y - h, point.z));
        double dzPos = sdf.sampleDistance(new Vector3d(point.x, point.y, point.z + h));
        double dzNeg = sdf.sampleDistance(new Vector3d(point.x, point.y, point.z - h));
        if (!Double.isFinite(dxPos) || !Double.isFinite(dxNeg)
                || !Double.isFinite(dyPos) || !Double.isFinite(dyNeg)
                || !Double.isFinite(dzPos) || !Double.isFinite(dzNeg)) {
            dest.set(Double.NaN, Double.NaN, Double.NaN);
            return;
        }

        double gx = dxPos - dxNeg;
        double gy = dyPos - dyNeg;
        double gz = dzPos - dzNeg;
        if (!Double.isFinite(gx) || !Double.isFinite(gy) || !Double.isFinite(gz)) {
            dest.set(Double.NaN, Double.NaN, Double.NaN);
            return;
        }

        Vector3d temp = new Vector3d(gx, gy, gz);
        Vector3d normalized = VectorUtils.safeNormalize(temp);
        if (normalized != null) {
            dest.set(normalized);
            return;
        }
        double length = VectorUtils.safeLength(temp);
        if (VectorUtils.isFinite(temp) && Double.isFinite(length) && length <= VectorUtils.EPS) {
            dest.set(0.0d, 0.0d, 0.0d);
            return;
        }
        dest.set(Double.NaN, Double.NaN, Double.NaN);
    }

    /** Filtering resolve (legacy); prefer {@link #resolvePointListStrict} for batch sample nodes. */
    static List<Vector3d> resolvePointList(Object value) {
        return SpatialValueResolver.resolvePointList(value);
    }

    /**
     * Strict 1:1 POINT_LIST parse: every element must resolve to a finite point.
     * Empty collection is valid (Count=0 correspondence). No silent skips.
     */
    static PointListResult resolvePointListStrict(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return PointListResult.invalid(ERROR_INVALID_INPUT);
        }
        List<Vector3d> points = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            Vector3d resolved = SpatialValueResolver.resolvePoint(entry);
            if (resolved == null
                    || !Double.isFinite(resolved.x)
                    || !Double.isFinite(resolved.y)
                    || !Double.isFinite(resolved.z)) {
                return PointListResult.invalid(ERROR_INVALID_POINTS);
            }
            points.add(resolved);
        }
        return PointListResult.ok(List.copyOf(points));
    }

    static ScalarSample sampleScalar(ScalarFieldData field, Vector3d point) {
        double value = field.sampleScalar(point);
        boolean valid = Double.isFinite(value);
        return new ScalarSample(valid ? value : Double.NaN, valid);
    }

    static VectorSample sampleVector(VectorFieldData field, Vector3d point) {
        Vector3d out = new Vector3d();
        field.sampleVector(point, out);
        boolean valid = Double.isFinite(out.x) && Double.isFinite(out.y) && Double.isFinite(out.z);
        return new VectorSample(valid ? out : null, valid);
    }

    static boolean allFiniteScalars(List<Double> values) {
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    static boolean allFiniteVectors(List<Vector3d> vectors) {
        for (Vector3d vector : vectors) {
            if (vector == null
                    || !Double.isFinite(vector.x)
                    || !Double.isFinite(vector.y)
                    || !Double.isFinite(vector.z)) {
                return false;
            }
        }
        return true;
    }
}
