package com.nodecraft.nodesystem.nodes.math.logic;

import org.jetbrains.annotations.Nullable;

/**
 * Shared helpers for Logic v1 nodes.
 * <p>
 * Port types are the sole semantics source: no Number/String/Object coercion.
 */
final class LogicUtils {

    private LogicUtils() {
    }

    /**
     * {@link Boolean#TRUE} → true; {@link Boolean#FALSE} → false;
     * null / non-Boolean → false.
     */
    static boolean booleanValue(@Nullable Object value) {
        return value instanceof Boolean bool && bool;
    }

    /**
     * {@link Integer} → that index; null / non-Integer → {@code -1}
     * (Switch treats as Default branch).
     */
    static int switchIndex(@Nullable Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        return -1;
    }
}
