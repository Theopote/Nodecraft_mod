package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.datatypes.DataTreeData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DataTreeNodeTest {

    @Test
    void graftListCreatesOneBranchPerItem() {
        GraftListNode graft = new GraftListNode();
        Map<String, Object> outputs = graft.compute(Map.of(
            "input_list", List.of("a", "b", "c")
        ));

        assertEquals(3, outputs.get("output_branch_count"));
        DataTreeData tree = assertInstanceOf(DataTreeData.class, outputs.get("output_tree"));
        assertEquals(3, tree.getBranchCount());
        assertEquals(List.of("a"), tree.getBranch(List.of(0)).items());
        assertEquals(List.of("b"), tree.getBranch(List.of(1)).items());
        assertEquals(List.of("c"), tree.getBranch(List.of(2)).items());
    }

    @Test
    void flattenTreeCollectsAllBranchItems() {
        DataTreeData tree = DataTreeData.fromBranches(
            List.of(List.of(0), List.of(1), List.of(2)),
            List.of(List.of("a"), List.of("b"), List.of("c"))
        );

        FlattenTreeNode flatten = new FlattenTreeNode();
        Map<String, Object> outputs = flatten.compute(Map.of("input_tree", tree));

        assertEquals(3, outputs.get("output_item_count"));
        assertEquals(List.of("a", "b", "c"), outputs.get("output_list"));
    }

    @Test
    void graftThenFlattenRoundTripsListValues() {
        GraftListNode graft = new GraftListNode();
        Map<String, Object> grafted = graft.compute(Map.of(
            "input_list", List.of(10, 20, 30)
        ));

        FlattenTreeNode flatten = new FlattenTreeNode();
        Map<String, Object> flattened = flatten.compute(Map.of(
            "input_tree", grafted.get("output_tree")
        ));

        assertEquals(List.of(10, 20, 30), flattened.get("output_list"));
    }

    @Test
    void graftEmptyListProducesEmptyTree() {
        GraftListNode graft = new GraftListNode();
        Map<String, Object> outputs = graft.compute(Map.of("input_list", List.of()));

        assertEquals(0, outputs.get("output_branch_count"));
        DataTreeData tree = assertInstanceOf(DataTreeData.class, outputs.get("output_tree"));
        assertEquals(0, tree.getBranchCount());
        assertEquals(List.of(), tree.flatten());
    }
}
