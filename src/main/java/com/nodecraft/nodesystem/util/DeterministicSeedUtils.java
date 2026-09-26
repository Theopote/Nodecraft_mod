package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

/**
 * Resolves deterministic RNG seeds for scatter/sampling nodes.
 */
public final class DeterministicSeedUtils {

    public static final int DEFAULT_SEED = 12_345;

    private DeterministicSeedUtils() {
    }

    public static int resolveSeed(@Nullable Object portValue, int propertyDefault) {
        return portValue instanceof Integer i ? i : propertyDefault;
    }

    public static int resolveStrictInteger(@Nullable Object portValue, int propertyFallback) {
        return portValue instanceof Integer i ? i : propertyFallback;
    }
}
