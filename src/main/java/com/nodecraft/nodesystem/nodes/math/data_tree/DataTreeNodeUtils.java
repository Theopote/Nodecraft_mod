package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.datatypes.DataTreeData;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed boundary helpers for data-tree nodes.
 * <p>
 * LIST to DATA_TREE coercion is not performed here -- use Graft List / Flatten Tree
 * (and {@link com.nodecraft.nodesystem.api.TypeConversionRegistry} EXPLICIT policy).
 */
final class DataTreeNodeUtils {
    private DataTreeNodeUtils() {
    }

    static DataTreeData requireTree(Object value) {
        if (value instanceof DataTreeData tree) {
            return tree;
        }
        return DataTreeData.empty();
    }

    static List<Object> requireList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    static List<Integer> parsePath(Object value, List<Integer> fallback) {
        try {
            List<Integer> path = DataTreeData.parsePath(value);
            return path.isEmpty() && value == null ? fallback : path;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    static int normalizeIndex(int index, int size, boolean allowNegative, boolean wrap) {
        if (size <= 0) {
            return -1;
        }
        int resolved = index;
        if (resolved < 0 && allowNegative) {
            resolved = size + resolved;
        }
        if (wrap) {
            resolved = ((resolved % size) + size) % size;
        }
        return resolved >= 0 && resolved < size ? resolved : -1;
    }
}
