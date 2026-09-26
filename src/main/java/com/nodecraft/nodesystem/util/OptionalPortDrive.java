package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Connection-aware optional port resolution for property-backed drives.
 * <p>
 * Frozen rule:
 * <ul>
 *   <li>unconnected → property fallback</li>
 *   <li>connected + valid → input override</li>
 *   <li>connected + null/invalid → fail closed (no property fallback)</li>
 * </ul>
 */
public final class OptionalPortDrive {

    private OptionalPortDrive() {
    }

    public static boolean isConnected(@Nullable INode node, @Nullable String portId) {
        if (node == null || portId == null) {
            return false;
        }
        for (IPort port : node.getInputPorts()) {
            if (portId.equals(port.getId()) && port.isConnected()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Optional VECTOR drive. Returns {@code null} when connected but invalid (fail closed).
     * When unconnected, returns a copy of {@code propertyFallback} (may be null).
     */
    public static @Nullable Vector3d resolveOptionalVector(
            BaseNode node,
            String portId,
            @Nullable Vector3d propertyFallback
    ) {
        if (isConnected(node, portId)) {
            Vector3d resolved = SpatialValueResolver.resolveVector(node.getInput(portId));
            return VectorUtils.isFinite(resolved) ? resolved : null;
        }
        return propertyFallback == null ? null : new Vector3d(propertyFallback);
    }

    /**
     * Optional DOUBLE drive. Returns {@code null} when connected but invalid (fail closed).
     */
    public static @Nullable Double resolveOptionalDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        if (isConnected(node, portId)) {
            Object value = node.getInput(portId);
            if (!(value instanceof Number number)) {
                return null;
            }
            double resolved = number.doubleValue();
            return Double.isFinite(resolved) ? resolved : null;
        }
        return Double.isFinite(propertyFallback) ? propertyFallback : null;
    }

    /**
     * Optional POINT drive. Returns {@code null} when connected but invalid (fail closed).
     */
    public static @Nullable Vector3d resolveOptionalPoint(
            BaseNode node,
            String portId,
            @Nullable Vector3d propertyFallback
    ) {
        if (isConnected(node, portId)) {
            Vector3d resolved = SpatialValueResolver.resolvePoint(node.getInput(portId));
            return PointUtils.isFinite(resolved) ? resolved : null;
        }
        return propertyFallback == null ? null : new Vector3d(propertyFallback);
    }

    /**
     * Optional BLOCK_POS drive. Returns {@code null} when connected but invalid (fail closed).
     */
    public static @Nullable BlockPos resolveOptionalBlockPos(
            BaseNode node,
            String portId,
            @Nullable BlockPos propertyFallback
    ) {
        if (isConnected(node, portId)) {
            Object value = node.getInput(portId);
            return value instanceof BlockPos blockPos ? blockPos : null;
        }
        return propertyFallback;
    }

    /**
     * Optional PLANE drive. Returns {@code null} when connected but invalid (fail closed).
     * Canonicalizes via {@link PlaneData#normalized()} when connected.
     */
    public static @Nullable PlaneData resolveOptionalPlane(
            BaseNode node,
            String portId,
            @Nullable PlaneData propertyFallback
    ) {
        if (isConnected(node, portId)) {
            Object value = node.getInput(portId);
            if (!(value instanceof PlaneData plane)) {
                return null;
            }
            return plane.normalized();
        }
        return propertyFallback;
    }

    /**
     * Optional BOOLEAN drive. Returns {@code null} when connected but invalid (fail closed).
     */
    public static @Nullable Boolean resolveOptionalBoolean(
            BaseNode node,
            String portId,
            boolean propertyFallback
    ) {
        if (isConnected(node, portId)) {
            Object value = node.getInput(portId);
            return value instanceof Boolean bool ? bool : null;
        }
        return propertyFallback;
    }

    /**
     * Optional INTEGER drive. Returns {@code null} when connected but invalid (fail closed).
     */
    public static @Nullable Integer resolveOptionalInteger(
            BaseNode node,
            String portId,
            int propertyFallback
    ) {
        if (isConnected(node, portId)) {
            return StrictIntegerUtils.requireExactInteger(node.getInput(portId));
        }
        return propertyFallback;
    }

    /**
     * Optional STRING drive. Returns {@code null} when connected but invalid (fail closed).
     * When unconnected, returns trimmed {@code propertyFallback}; blank property → {@code null}.
     */
    public static @Nullable String resolveOptionalString(
            BaseNode node,
            String portId,
            @Nullable String propertyFallback
    ) {
        if (isConnected(node, portId)) {
            Object value = node.getInput(portId);
            if (!(value instanceof String text) || text.isBlank()) {
                return null;
            }
            return text.trim();
        }
        if (propertyFallback == null || propertyFallback.isBlank()) {
            return null;
        }
        return propertyFallback.trim();
    }
}
