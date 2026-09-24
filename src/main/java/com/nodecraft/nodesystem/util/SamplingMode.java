package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

/**
 * Explicit path sampling mode. Avoids hidden Count-vs-Spacing precedence.
 */
public enum SamplingMode {
    /** Use the path's current vertices / internal samples. */
    ORIGINAL,
    /** Uniform samples along total arc length by count. */
    COUNT,
    /** Samples at fixed arc-length spacing. */
    SPACING;

    public static SamplingMode fromObject(@Nullable Object value, SamplingMode fallback) {
        if (value instanceof SamplingMode mode) {
            return mode;
        }
        if (value instanceof String text) {
            try {
                return valueOf(text.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
