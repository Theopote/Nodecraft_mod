package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

/**
 * Strict DOUBLE port resolution: exact {@link Double} only, no {@code Number.doubleValue()} coercion.
 */
public final class StrictDoubleUtils {

    private StrictDoubleUtils() {
    }

    /** Required DOUBLE input: only exact finite {@link Double}, otherwise null. */
    public static @Nullable Double requireExactFiniteDouble(@Nullable Object value) {
        if (value instanceof Double d && Double.isFinite(d)) {
            return d;
        }
        return null;
    }
}
