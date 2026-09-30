package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

/**
 * Strict list index resolution for List Core v2 nodes.
 * <p>
 * Requires exact {@link Integer} when driven — no {@code Number.intValue()} coercion.
 */
public final class ListIndexResolver {

    public record IndexResolveResult(boolean valid, int index) {
        public static IndexResolveResult ok(int index) {
            return new IndexResolveResult(true, index);
        }

        public static IndexResolveResult invalid() {
            return new IndexResolveResult(false, 0);
        }
    }

    private ListIndexResolver() {
    }

    /** Driven port must supply exact {@link Integer}; undriven → invalid. */
    public static IndexResolveResult resolveRequiredIndex(@Nullable Object value, boolean driven) {
        if (!driven) {
            return IndexResolveResult.invalid();
        }
        Integer index = StrictIntegerUtils.requireExactInteger(value);
        return index != null ? IndexResolveResult.ok(index) : IndexResolveResult.invalid();
    }

    /** Undriven → default; driven → exact {@link Integer} or invalid. */
    public static IndexResolveResult resolveOptionalIndex(
            @Nullable Object value,
            int defaultWhenUndriven,
            boolean driven
    ) {
        if (!driven) {
            return IndexResolveResult.ok(defaultWhenUndriven);
        }
        Integer index = StrictIntegerUtils.requireExactInteger(value);
        return index != null ? IndexResolveResult.ok(index) : IndexResolveResult.invalid();
    }

    /** Negative index counts from end: {@code -1} → last element. */
    public static int normalizeNegativeFromEnd(int index, int size) {
        if (index < 0) {
            return size + index;
        }
        return index;
    }

    /** Wrap index into {@code [0, size)} when size &gt; 0. */
    public static int applyWrap(int index, int size) {
        if (size <= 0) {
            return index;
        }
        return ((index % size) + size) % size;
    }
}
