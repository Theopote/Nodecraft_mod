package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

/**
 * Strict INTEGER port resolution: exact {@link Integer} only, no {@code Number.intValue()} truncation.
 */
public final class StrictIntegerUtils {

    private StrictIntegerUtils() {
    }

    /**
     * Unconnected ({@code null}) returns {@code fallbackWhenUnconnected};
     * connected non-Integer returns {@code null} (fail closed).
     */
    public static @Nullable Integer resolveExactInteger(
            @Nullable Object value,
            @Nullable Integer fallbackWhenUnconnected
    ) {
        if (value == null) {
            return fallbackWhenUnconnected;
        }
        return value instanceof Integer i ? i : null;
    }

    /** Required INTEGER input: only exact {@link Integer}, otherwise null. */
    public static @Nullable Integer requireExactInteger(@Nullable Object value) {
        return value instanceof Integer i ? i : null;
    }
}
