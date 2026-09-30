package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

/**
 * Strict resolution for Random v2 Seed, Count, and coordinates from resolved port values.
 */
public final class RandomInputResolver {

    public record IntegerResolveResult(boolean valid, int value) {
        public static IntegerResolveResult ok(int value) {
            return new IntegerResolveResult(true, value);
        }

        public static IntegerResolveResult invalid() {
            return new IntegerResolveResult(false, 0);
        }
    }

    private RandomInputResolver() {
    }

    /** Undriven Seed ≡ {@code 0}. Driven ports require exact {@link Integer}. */
    public static IntegerResolveResult resolveSeed(@Nullable Object value, boolean driven) {
        if (!driven) {
            return IntegerResolveResult.ok(0);
        }
        Integer seed = StrictIntegerUtils.requireExactInteger(value);
        return seed != null ? IntegerResolveResult.ok(seed) : IntegerResolveResult.invalid();
    }

    /** Undriven Count uses property default. Driven ports require exact {@link Integer}. */
    public static IntegerResolveResult resolveCount(@Nullable Object value, int defaultCount, boolean driven) {
        if (!driven) {
            return IntegerResolveResult.ok(GenerationLimits.clampNonNegativeCount(defaultCount));
        }
        Integer count = StrictIntegerUtils.requireExactInteger(value);
        if (count == null) {
            return IntegerResolveResult.invalid();
        }
        return IntegerResolveResult.ok(GenerationLimits.clampNonNegativeCount(count));
    }

    /** Undriven coordinate → default. Driven → exact finite {@link Double}. */
    public static @Nullable Double resolveDouble(@Nullable Object value, double defaultValue, boolean driven) {
        if (!driven) {
            return defaultValue;
        }
        return StrictDoubleUtils.requireExactFiniteDouble(value);
    }

    /** Undriven → default. Driven → exact {@link Boolean}. */
    public static @Nullable Boolean resolveBoolean(@Nullable Object value, boolean defaultValue, boolean driven) {
        if (!driven) {
            return defaultValue;
        }
        return StrictBooleanUtils.requireExactBoolean(value);
    }
}
