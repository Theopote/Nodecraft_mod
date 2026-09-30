package com.nodecraft.nodesystem.nodes.math.data_tree;

import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataTreeNodeUtilsBudgetTest {

    @Test
    void combinedBudgetAccumulatesBranchesAndItemsWithLongSums() {
        DataTreeData treeA = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("A", "B"))
        ), ListElementKind.STRING);
        DataTreeData treeB = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("C", "D", "E"))
        ), ListElementKind.STRING);

        assertTrue(DataTreeNodeUtils.preflightCombinedTreeBudget(List.of(treeA, treeB), 2, 5).valid());
        assertFalse(DataTreeNodeUtils.preflightCombinedTreeBudget(List.of(treeA, treeB), 1, 10).valid());
        assertFalse(DataTreeNodeUtils.preflightCombinedTreeBudget(List.of(treeA, treeB), 10, 4).valid());
    }
}
