package com.nodecraft.nodesystem.nodes.math.fields;

import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

/**
 * Field-node spatial boundary helpers. Locations use {@link SpatialValueResolver#resolvePoint};
 * directions use {@link SpatialValueResolver#resolveVector}.
 */
final class FieldSampleUtils {
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
}
