package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.core.BaseNode;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Connection-aware SDF input resolution (Graph V93 / SDF Language v1).
 * <p>
 * Unconnected → property/default; connected valid → value; connected invalid → {@code null}
 * (caller fail-closed). Matches {@link PrimitiveInputUtils} / {@link CurveInputUtils}.
 */
public final class SdfInputUtils {

    private SdfInputUtils() {
    }

    public static boolean isConnected(BaseNode node, String portId) {
        return OptionalPortDrive.isConnected(node, portId);
    }

    public static @Nullable Vector3d resolveOptionalPoint(
            BaseNode node,
            String portId,
            @Nullable Vector3d propertyFallback
    ) {
        return OptionalPortDrive.resolveOptionalPoint(node, portId, propertyFallback);
    }

    public static @Nullable Vector3d resolveOptionalVector(
            BaseNode node,
            String portId,
            @Nullable Vector3d propertyFallback
    ) {
        return OptionalPortDrive.resolveOptionalVector(node, portId, propertyFallback);
    }

    public static @Nullable Double resolveOptionalFiniteDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        return CurveInputUtils.resolveOptionalFiniteDouble(node, portId, propertyFallback);
    }

    public static @Nullable Double resolveOptionalPositiveFiniteDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        return CurveInputUtils.resolveOptionalPositiveFiniteDouble(node, portId, propertyFallback);
    }

    public static @Nullable Double resolveOptionalNonNegativeFiniteDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        return CurveInputUtils.resolveOptionalNonNegativeFiniteDouble(node, portId, propertyFallback);
    }

    public static @Nullable Integer resolveOptionalInteger(
            BaseNode node,
            String portId,
            int propertyFallback
    ) {
        return OptionalPortDrive.resolveOptionalInteger(node, portId, propertyFallback);
    }
}
