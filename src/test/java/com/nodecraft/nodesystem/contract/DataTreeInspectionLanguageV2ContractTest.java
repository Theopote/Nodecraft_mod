package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.TreePathData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.math.data_tree.DataTreeNodeUtils;
import com.nodecraft.nodesystem.nodes.math.data_tree.FlattenTreeNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.TreePathsNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.TreeStatisticsNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.TreeViewerNode;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data Tree Inspection & Flatten Safety v2 (Graph V132).
 */
class DataTreeInspectionLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV132() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void flattenEmptyValidTreeSucceeds() {
        FlattenTreeNode node = new FlattenTreeNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_tree", DataTreeData.empty(ListElementKind.STRING)
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals("", outputs.get("output_error"));
        assertEquals(List.of(), outputs.get("output_list"));
        assertEquals(0, outputs.get("output_item_count"));
    }

    @Test
    void flattenInvalidInputFailsClosed() {
        FlattenTreeNode node = new FlattenTreeNode();
        Map<String, Object> outputs = node.compute(Map.of("input_tree", "not-a-tree"));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_INVALID_INPUT, outputs.get("output_error"));
        assertEquals(List.of(), outputs.get("output_list"));
        assertEquals(0, outputs.get("output_item_count"));
    }

    @Test
    void treePathsLexicographicOrderUsesNonNegativeIndices() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(10), List.of("c")),
                new DataTreeData.Branch(List.of(1), List.of("a")),
                new DataTreeData.Branch(List.of(0), List.of("b")),
                new DataTreeData.Branch(List.of(0, 1), List.of("d"))
        ), ListElementKind.STRING);

        TreePathsNode pathsNode = new TreePathsNode();
        Map<String, Object> outputs = pathsNode.compute(Map.of("input_tree", tree));
        assertTrue((Boolean) outputs.get("output_valid"));
        @SuppressWarnings("unchecked")
        List<TreePathData> paths = (List<TreePathData>) outputs.get("output_paths");
        assertEquals(List.of(0), paths.get(0).indices());
        assertEquals(List.of(0, 1), paths.get(1).indices());
        assertEquals(List.of(1), paths.get(2).indices());
        assertEquals(List.of(10), paths.get(3).indices());
    }

    @Test
    void dataTreeRejectsNegativePathComponent() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                new DataTreeData.Branch(List.of(-1), List.of("a")));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                new DataTreeData(List.of(new DataTreeData.Branch(List.of(-1), List.of("a")))));
    }

    @Test
    void treeStatisticsInvalidTreeFailsClosed() {
        TreeStatisticsNode node = new TreeStatisticsNode();
        Map<String, Object> outputs = node.compute(Map.of("input_tree", 42));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_INVALID_INPUT, outputs.get("output_error"));
        assertEquals(0, outputs.get("output_branch_count"));
        assertEquals(0, outputs.get("output_item_count"));
        assertEquals(0, outputs.get("output_max_depth"));
        assertEquals(List.of(), outputs.get("output_branch_sizes"));
    }

    @Test
    void branchSizesOrderMatchesTreePaths() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(2), List.of("x", "y")),
                new DataTreeData.Branch(List.of(0), List.of("a")),
                new DataTreeData.Branch(List.of(1), List.of("b", "c", "d"))
        ), ListElementKind.STRING);

        TreePathsNode pathsNode = new TreePathsNode();
        TreeStatisticsNode statsNode = new TreeStatisticsNode();
        Map<String, Object> pathOut = pathsNode.compute(Map.of("input_tree", tree));
        Map<String, Object> statsOut = statsNode.compute(Map.of("input_tree", tree));

        @SuppressWarnings("unchecked")
        List<TreePathData> paths = (List<TreePathData>) pathOut.get("output_paths");
        @SuppressWarnings("unchecked")
        List<Integer> sizes = (List<Integer>) statsOut.get("output_branch_sizes");

        assertEquals(3, paths.size());
        assertEquals(3, sizes.size());
        for (int i = 0; i < paths.size(); i++) {
            assertEquals(tree.getBranch(paths.get(i)).items().size(), sizes.get(i));
        }
        assertEquals(List.of(0), paths.get(0).indices());
        assertEquals(1, sizes.get(0));
        assertEquals(List.of(1), paths.get(1).indices());
        assertEquals(3, sizes.get(1));
        assertEquals(List.of(2), paths.get(2).indices());
        assertEquals(2, sizes.get(2));
    }

    @Test
    void treeViewerHugeTreeTruncatesWithFooter() {
        List<DataTreeData.Branch> branches = new ArrayList<>();
        int branchCount = GenerationLimits.MAX_TREE_VIEWER_PREVIEW_BRANCHES + 10;
        for (int i = 0; i < branchCount; i++) {
            branches.add(new DataTreeData.Branch(List.of(i), List.of("x")));
        }
        DataTreeData tree = new DataTreeData(branches, ListElementKind.STRING);

        TreeViewerNode node = new TreeViewerNode();
        Map<String, Object> outputs = node.compute(Map.of("input_tree", tree));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertTrue((Boolean) outputs.get("output_truncated"));
        assertEquals("", outputs.get("output_error"));
        String summary = assertInstanceOf(String.class, outputs.get("output_summary"));
        assertTrue(summary.contains("Preview truncated"));
        assertTrue(summary.length() <= GenerationLimits.MAX_TREE_VIEWER_OUTPUT_CHARS);
    }

    @Test
    void treeViewerInvalidInputFailsClosed() {
        TreeViewerNode node = new TreeViewerNode();
        Map<String, Object> outputs = node.compute(Map.of("input_tree", "bad"));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertFalse((Boolean) outputs.get("output_truncated"));
        assertEquals(DataTreeNodeUtils.ERROR_INVALID_INPUT, outputs.get("output_error"));
        assertEquals("", outputs.get("output_summary"));
    }
}
