package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
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

    static @Nullable Vector3d resolveVector(Object value) {
        return SpatialValueResolver.resolveVector(value);
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
