package com.nodecraft.nodesystem.datatypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ordered integer branch address for {@link DataTreeData} (e.g. {@code {0;1;2}}).
 * Graph wires use this type; string formatting is display/debug only.
 */
public record TreePathData(List<Integer> indices) {

    public TreePathData {
        Objects.requireNonNull(indices, "Tree path indices cannot be null");
        List<Integer> copied = new ArrayList<>(indices.size());
        for (Integer index : indices) {
            Objects.requireNonNull(index, "Tree path index cannot be null");
            copied.add(index);
        }
        indices = List.copyOf(copied);
    }

    public static TreePathData of(int... indices) {
        List<Integer> list = new ArrayList<>(indices.length);
        for (int index : indices) {
            list.add(index);
        }
        return new TreePathData(list);
    }

    public static TreePathData empty() {
        return new TreePathData(List.of());
    }

    public int size() {
        return indices.size();
    }

    public boolean isEmpty() {
        return indices.isEmpty();
    }

    /** Debug / Tree Viewer formatting only — not a graph wire type. */
    public String format() {
        return DataTreeData.formatPath(indices);
    }
}
