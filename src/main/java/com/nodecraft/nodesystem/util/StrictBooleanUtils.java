package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

/**
 * Strict BOOLEAN port resolution: exact {@link Boolean} only, no truthiness coercion.
 */
public final class StrictBooleanUtils {

    private StrictBooleanUtils() {
    }

    /** Required BOOLEAN input: only exact {@link Boolean}, otherwise null. */
    public static @Nullable Boolean requireExactBoolean(@Nullable Object value) {
        return value instanceof Boolean bool ? bool : null;
    }
}
