package com.nodecraft.nodesystem.nodes.math.scalar_math;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.StrictDoubleUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Scalar-math port helpers. DOUBLE = exact finite {@link Double}; optional drives
 * use {@code isConnected || isInputPresent} (fail closed, no property fallback).
 */
final class ScalarMathPorts {

    static final String ERROR_INVALID_INPUT = "invalid_input";
    static final String ERROR_NON_FINITE_RESULT = "non_finite_result";
    static final String ERROR_INVALID_DOMAIN = "invalid_domain";
    static final String ERROR_DEGENERATE_DOMAIN = "degenerate_domain";

    private ScalarMathPorts() {
    }

    static boolean isPortDriven(BaseNode node, String portId) {
        return OptionalPortDrive.isConnected(node, portId) || node.isInputPresent(portId);
    }

    static @Nullable Double requireExactFinite(@Nullable Object value) {
        return StrictDoubleUtils.requireExactFiniteDouble(value);
    }

    /**
     * Unconnected → {@code propertyFallback}. Driven exact {@link Boolean} → value.
     * Driven invalid → {@code null}.
     */
    static @Nullable Boolean resolveOptionalBoolean(BaseNode node, String portId, boolean propertyFallback) {
        if (isPortDriven(node, portId)) {
            Object value = node.getInput(portId);
            return value instanceof Boolean bool ? bool : null;
        }
        return propertyFallback;
    }
}
