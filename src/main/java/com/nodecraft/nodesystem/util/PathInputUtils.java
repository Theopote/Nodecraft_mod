package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PathData;
import org.jetbrains.annotations.Nullable;

/**
 * Graph-facing PATH consumer helper. Wire wrapping lives in {@link InputValueNormalizer}.
 */
public final class PathInputUtils {

    private PathInputUtils() {
    }

    /** Canonical PATH ingress from a port value. */
    public static @Nullable PathData resolvePath(@Nullable Object value) {
        return PathData.wrap(value);
    }
}
