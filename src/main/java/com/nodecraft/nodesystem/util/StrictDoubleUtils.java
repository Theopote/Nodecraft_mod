package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    /**
     * Strict DOUBLE_LIST: every entry must be an exact finite {@link Double}.
     * Empty collections are valid.
     */
    public static @Nullable List<Double> resolveStrictDoubleList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return null;
        }
        List<Double> out = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            Double resolved = requireExactFiniteDouble(entry);
            if (resolved == null) {
                return null;
            }
            out.add(resolved);
        }
        return List.copyOf(out);
    }

    /**
     * Strict bounded DOUBLE_LIST: same rules as {@link #resolveStrictDoubleList(Object)}
     * with an element-count ceiling.
     */
    public static @Nullable List<Double> resolveStrictDoubleListBounded(
            @Nullable Object value,
            int maxElements
    ) {
        if (maxElements < 0) {
            return null;
        }
        if (!(value instanceof Collection<?> collection)) {
            return null;
        }
        if (collection.size() > maxElements) {
            return null;
        }
        return resolveStrictDoubleList(value);
    }
}
