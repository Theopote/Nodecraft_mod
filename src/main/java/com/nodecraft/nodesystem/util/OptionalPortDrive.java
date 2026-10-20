package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional port resolution for property-backed drives.
 * <p>
 * Unified drive rule (Phase 5G):
 * <ul>
 *   <li>undriven (no wire, no injected key) → property fallback</li>
 *   <li>driven + valid → input override</li>
 *   <li>driven + null/invalid → fail closed ({@code null}, no property fallback)</li>
 * </ul>
 * Driven means {@link #isConnected wired} or {@link BaseNode#isInputPresent injected}
 * (including rejected-null markers from {@link BaseNode#setInput} / compute ingress).
 * Callers must treat a {@code null} resolve result as invalid when {@link #isDriven} is true.
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
     * Wired or explicitly injected (including rejected-null markers).
     * Prefer this over {@link #isConnected} when deciding fail-closed vs property fallback.
     */
    public static boolean isDriven(BaseNode node, String portId) {
        return isConnected(node, portId) || node.isInputPresent(portId);
    }

    /**
     * Optional VECTOR drive. Returns {@code null} when driven but invalid (fail closed).
     * When undriven, returns a copy of {@code propertyFallback} (may be null).
     */
    public static @Nullable Vector3d resolveOptionalVector(
            BaseNode node,
            String portId,
            @Nullable Vector3d propertyFallback
    ) {
        if (isDriven(node, portId)) {
            Vector3d resolved = SpatialValueResolver.resolveVector(node.getInput(portId));
            return VectorUtils.isFinite(resolved) ? resolved : null;
        }
        return propertyFallback == null ? null : new Vector3d(propertyFallback);
    }

    /**
     * Optional strict DOUBLE drive. Connected ports require exact finite {@link Double}.
     * Returns {@code null} when connected but invalid (fail closed).
     */
    /** @deprecated use {@link #resolveOptionalDouble} — graph DOUBLE ports are always strict. */
    @Deprecated
    public static @Nullable Double resolveOptionalStrictDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        return resolveOptionalDouble(node, portId, propertyFallback);
    }

    /**
     * Optional DOUBLE drive. Driven ports require exact finite {@link Double}.
     * Returns {@code null} when driven but invalid (fail closed).
     */
    public static @Nullable Double resolveOptionalDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        if (isDriven(node, portId)) {
            return StrictDoubleUtils.requireExactFiniteDouble(node.getInput(portId));
        }
        return Double.isFinite(propertyFallback) ? propertyFallback : null;
    }

    /**
     * Internal legacy numeric coercion for non-graph algorithms. Do not use for graph DOUBLE ports.
     */
    public static @Nullable Double resolveLegacyNumberAsDouble(@Nullable Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double resolved = number.doubleValue();
        return Double.isFinite(resolved) ? resolved : null;
    }

    /**
     * Optional POINT drive. Returns {@code null} when driven but invalid (fail closed).
     */
    public static @Nullable Vector3d resolveOptionalPoint(
            BaseNode node,
            String portId,
            @Nullable Vector3d propertyFallback
    ) {
        if (isDriven(node, portId)) {
            Object value = node.getInput(portId);
            if (!(value instanceof PointData point)) {
                return null;
            }
            return point.position();
        }
        return propertyFallback == null ? null : new Vector3d(propertyFallback);
    }

    /**
     * Optional BLOCK_POS drive. Returns {@code null} when driven but invalid (fail closed).
     */
    public static @Nullable BlockPos resolveOptionalBlockPos(
            BaseNode node,
            String portId,
            @Nullable BlockPos propertyFallback
    ) {
        if (isDriven(node, portId)) {
            Object value = node.getInput(portId);
            return value instanceof BlockPos blockPos ? blockPos : null;
        }
        return propertyFallback;
    }

    /**
     * Optional PLANE drive. Returns {@code null} when driven but invalid (fail closed).
     * Canonicalizes via {@link PlaneData#normalized()} when driven.
     */
    public static @Nullable PlaneData resolveOptionalPlane(
            BaseNode node,
            String portId,
            @Nullable PlaneData propertyFallback
    ) {
        if (isDriven(node, portId)) {
            Object value = node.getInput(portId);
            if (!(value instanceof PlaneData plane)) {
                return null;
            }
            return plane.normalized();
        }
        return propertyFallback;
    }

    /**
     * Optional BOOLEAN drive. Returns {@code null} when driven but invalid (fail closed).
     */
    public static @Nullable Boolean resolveOptionalBoolean(
            BaseNode node,
            String portId,
            boolean propertyFallback
    ) {
        if (isDriven(node, portId)) {
            Object value = node.getInput(portId);
            return value instanceof Boolean bool ? bool : null;
        }
        return propertyFallback;
    }

    /**
     * Optional INTEGER drive. Returns {@code null} when driven but invalid (fail closed).
     */
    public static @Nullable Integer resolveOptionalInteger(
            BaseNode node,
            String portId,
            int propertyFallback
    ) {
        if (isDriven(node, portId)) {
            return StrictIntegerUtils.requireExactInteger(node.getInput(portId));
        }
        return propertyFallback;
    }

    /**
     * Optional STRING drive. Returns {@code null} when driven but invalid (fail closed).
     * When undriven, returns trimmed {@code propertyFallback}; blank property → {@code null}.
     */
    public static @Nullable String resolveOptionalString(
            BaseNode node,
            String portId,
            @Nullable String propertyFallback
    ) {
        if (isDriven(node, portId)) {
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

    /**
     * Optional STRING drive that preserves explicit blank strings when driven.
     * Returns {@code null} when driven but value is not a {@link String} (fail closed).
     */
    public static @Nullable String resolveOptionalStringAllowBlank(
            BaseNode node,
            String portId,
            @Nullable String propertyFallback
    ) {
        if (isDriven(node, portId)) {
            Object value = node.getInput(portId);
            return value instanceof String text ? text : null;
        }
        return propertyFallback;
    }

    /**
     * Optional STRING_LIST drive. Returns {@code null} when driven but invalid (fail closed).
     * Driven lists must be homogeneous {@link String} elements (no coercion). Blank entries
     * are trimmed and skipped. When undriven, returns {@code propertyFallback} as-is
     * (may be null or empty).
     */
    public static @Nullable List<String> resolveOptionalStringList(
            BaseNode node,
            String portId,
            @Nullable List<String> propertyFallback
    ) {
        if (isDriven(node, portId)) {
            Object value = node.getInput(portId);
            if (!(value instanceof List<?> list)) {
                return null;
            }
            List<String> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (!(item instanceof String text)) {
                    return null;
                }
                String trimmed = text.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
            return List.copyOf(out);
        }
        return propertyFallback;
    }
}
