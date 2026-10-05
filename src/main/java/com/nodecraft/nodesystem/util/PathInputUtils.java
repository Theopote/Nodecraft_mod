package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.datatypes.PathData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Graph-facing PATH port ingress: canonical {@link PathData} only.
 * LINE / POLYLINE / CURVE wires may connect implicitly but are wrapped at assignment.
 */
public final class PathInputUtils {

    private PathInputUtils() {
    }

    /** Canonical PATH ingress from a port value. */
    public static @Nullable PathData resolvePath(@Nullable Object value) {
        return PathData.wrap(value);
    }

    /**
     * Wraps LINE / POLYLINE / CURVE into {@link PathData} when the port type is PATH or PATH_LIST.
     * Returns the original value when wrapping is not applicable or fails.
     */
    public static @Nullable Object canonicalizeForType(@Nullable NodeDataType type, @Nullable Object value) {
        if (value == null || type == null) {
            return value;
        }
        if (type == NodeDataType.PATH) {
            PathData wrapped = PathData.wrap(value);
            return wrapped != null ? wrapped : value;
        }
        if (type == NodeDataType.PATH_LIST && value instanceof Collection<?> collection) {
            List<PathData> out = new ArrayList<>(collection.size());
            for (Object item : collection) {
                if (item == null) {
                    continue;
                }
                PathData wrapped = PathData.wrap(item);
                if (wrapped == null) {
                    return value;
                }
                out.add(wrapped);
            }
            return List.copyOf(out);
        }
        return value;
    }
}
