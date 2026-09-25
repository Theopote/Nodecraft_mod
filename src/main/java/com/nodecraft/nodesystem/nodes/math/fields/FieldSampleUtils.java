package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

/**
 * Field-node spatial boundary helpers. Locations use {@link SpatialValueResolver#resolvePoint};
 * directions use {@link SpatialValueResolver#resolveVector}.
 * <p>
 * Sampling helpers enforce Field v1 finite boundary: Valid=true only for finite outputs.
 */
final class FieldSampleUtils {

    record ScalarSample(double value, boolean valid) {
    }

    record VectorSample(@Nullable Vector3d vector, boolean valid) {
    }

    private FieldSampleUtils() {
    }

    static @Nullable Vector3d resolvePoint(Object value) {
        return SpatialValueResolver.resolvePoint(value);
    }

    static @Nullable Vector3d resolveVector(Object value) {
        return SpatialValueResolver.resolveVector(value);
    }

    static List<Vector3d> resolvePointList(Object value) {
        return SpatialValueResolver.resolvePointList(value);
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
