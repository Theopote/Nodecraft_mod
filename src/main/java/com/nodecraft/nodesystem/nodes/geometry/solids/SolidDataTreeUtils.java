package com.nodecraft.nodesystem.nodes.geometry.solids;

import com.nodecraft.nodesystem.datatypes.DataTreeData;

import java.util.ArrayList;
import java.util.List;

final class SolidDataTreeUtils {
    private SolidDataTreeUtils() {
    }

    static DataTreeData indexedValueTree(List<?> values) {
        List<DataTreeData.Branch> branches = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            branches.add(new DataTreeData.Branch(List.of(i), value == null ? List.of() : List.of(value)));
        }
        return new DataTreeData(branches);
    }

    static DataTreeData indexedGroupTree(List<? extends List<?>> groups) {
        List<DataTreeData.Branch> branches = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            branches.add(new DataTreeData.Branch(List.of(i), new ArrayList<>(groups.get(i))));
        }
        return new DataTreeData(branches);
    }
}
