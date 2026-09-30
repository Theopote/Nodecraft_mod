package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.TreePathData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.math.data_tree.DataTreeNodeUtils;
import com.nodecraft.nodesystem.nodes.math.data_tree.GraftListNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.PartitionListToTreeNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.TreeBranchNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.TreeItemNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data Tree Construction & Strict Lookup Contract v2 (Graph V130).
 */
class DataTreeConstructionLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV130() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void graftNullElementFailsClosed() {
        GraftListNode node = new GraftListNode();
        List<Object> list = new ArrayList<>(List.of("A", "B"));
        list.add(1, null);
        Map<String, Object> outputs = node.compute(Map.of("input_list", list));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_NULL_ITEM, outputs.get("output_error"));
        assertEquals(0, outputs.get("output_branch_count"));
        DataTreeData tree = assertInstanceOf(DataTreeData.class, outputs.get("output_tree"));
        assertTrue(tree.getBranches().isEmpty());
    }

    @Test
    void partitionNullElementFailsClosed() {
        PartitionListToTreeNode node = new PartitionListToTreeNode();
        List<Object> list = new ArrayList<>(List.of("A", "B", "C"));
        list.set(1, null);
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", list,
                "input_size", 2
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_NULL_ITEM, outputs.get("output_error"));
        assertEquals(0, outputs.get("output_branch_count"));
    }

    @Test
    void partitionRejectsDoubleSizeWhenConnected() {
        PartitionProbe probe = new PartitionProbe();
        probe.putInput("input_list", List.of("A", "B", "C", "D", "E"));
        probe.putInput("input_size", 2.9d);
        probe.connectInput("input_size", NodeDataType.INTEGER);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals(PartitionListToTreeNode.ERROR_INVALID_SIZE, probe.getOutput("output_error"));
    }

    @Test
    void partitionRejectsZeroSize() {
        PartitionListToTreeNode node = new PartitionListToTreeNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of("A", "B"),
                "input_size", 0
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(PartitionListToTreeNode.ERROR_INVALID_SIZE, outputs.get("output_error"));
    }

    @Test
    void partitionFiveItemsSizeTwoCreatesThreeBranches() {
        PartitionListToTreeNode node = new PartitionListToTreeNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of("A", "B", "C", "D", "E"),
                "input_size", 2
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals("", outputs.get("output_error"));
        assertEquals(3, outputs.get("output_branch_count"));
        DataTreeData tree = assertInstanceOf(DataTreeData.class, outputs.get("output_tree"));
        assertEquals(List.of("A", "B"), tree.getBranch(List.of(0)).items());
        assertEquals(List.of("C", "D"), tree.getBranch(List.of(1)).items());
        assertEquals(List.of("E"), tree.getBranch(List.of(2)).items());
    }

    @Test
    void treeBranchInvalidTreeTypeFailsClosed() {
        TreeBranchNode node = new TreeBranchNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_tree", "not-a-tree",
                "input_path", TreePathData.of(0)
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertFalse((Boolean) outputs.get("output_found"));
        assertEquals(DataTreeNodeUtils.ERROR_INVALID_INPUT, outputs.get("output_error"));
        assertEquals(List.of(), outputs.get("output_branch"));
    }

    @Test
    void treeBranchValidTreeMissingPathIsNotFound() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("a"))
        ), ListElementKind.STRING);
        TreeBranchNode node = new TreeBranchNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_tree", tree,
                "input_path", TreePathData.of(99)
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertFalse((Boolean) outputs.get("output_found"));
        assertEquals("", outputs.get("output_error"));
        assertEquals(List.of(), outputs.get("output_branch"));
    }

    @Test
    void treeItemRejectsDoubleIndexWhenConnected() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("a", "b"))
        ), ListElementKind.STRING);
        TreeItemProbe probe = new TreeItemProbe();
        probe.putInput("input_tree", tree);
        probe.putInput("input_path", TreePathData.of(0));
        probe.putInput("input_index", 1.9d);
        probe.connectInput("input_index", NodeDataType.INTEGER);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertFalse((Boolean) probe.getOutput("output_found"));
        assertEquals(TreeItemNode.ERROR_INVALID_INDEX, probe.getOutput("output_error"));
    }

    @Test
    void treeItemNegativeIndexReadsLast() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("a", "b"))
        ), ListElementKind.STRING);
        TreeItemNode node = new TreeItemNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_tree", tree,
                "input_path", TreePathData.of(0),
                "input_index", -1
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertTrue((Boolean) outputs.get("output_found"));
        assertEquals("b", outputs.get("output_item"));
    }

    @Test
    void graftKindMismatchFailsClosedWhenPointListBound() {
        GraftProbe probe = new GraftProbe();
        probe.putInput("input_list", List.of("not-a-point", new PointData(0, 0, 0)));
        probe.connectInput("input_list", NodeDataType.POINT_LIST);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_ELEMENT_KIND_MISMATCH, probe.getOutput("output_error"));
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
                .filter(port -> inputPortId.equals(port.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
        target.getInput(inputPortId);
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(com.nodecraft.nodesystem.execution.ExecutionContext context) {
        }
    }

    private static final class PartitionProbe extends PartitionListToTreeNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            DataTreeConstructionLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class TreeItemProbe extends TreeItemNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            DataTreeConstructionLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class GraftProbe extends GraftListNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            DataTreeConstructionLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }
}
