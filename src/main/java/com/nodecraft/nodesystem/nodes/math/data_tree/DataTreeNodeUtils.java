package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.datatypes.DataTreeData;

import java.util.ArrayList;
import java.util.List;

final class DataTreeNodeUtils {
    private DataTreeNodeUtils() {
    }

    static DataTreeData resolveTree(Object value) {
        if (value instanceof DataTreeData tree) {
            return tree;
        }
        if (value instanceof List<?> list) {
            return new DataTreeData(List.of(new DataTreeData.Branch(List.of(0), new ArrayList<>(list))));
        }
        if (value == null) {
            return DataTreeData.empty();
        }
        return new DataTreeData(List.of(new DataTreeData.Branch(List.of(0), List.of(value))));
    }

    static List<Object> resolveList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof DataTreeData tree) {
            return new ArrayList<>(tree.flatten());
        }
        if (value == null) {
            return List.of();
        }
        return List.of(value);
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
