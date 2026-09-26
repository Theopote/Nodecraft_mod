package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    /**
     * Strict INTEGER_LIST resolution: null / not Collection / empty → null;
     * any non-Integer element → null (fail closed); otherwise full list (no filtering).
     */
    public static @Nullable List<Integer> resolveStrictIntegerList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return null;
        }
        List<Integer> indices = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            if (!(entry instanceof Integer integer)) {
                return null;
            }
            indices.add(integer);
        }
        return List.copyOf(indices);
    }
}
