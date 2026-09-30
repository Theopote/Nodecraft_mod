package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.ListElementKind;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.TreePathData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.nodes.math.data_tree.CullEmptyBranchesNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.DataTreeNodeUtils;
import com.nodecraft.nodesystem.nodes.math.data_tree.EntwineNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.MergeTreesNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.ShiftPathNode;
import com.nodecraft.nodesystem.nodes.math.data_tree.SimplifyTreeNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data Tree Structural Operations Safety v2 (Graph V131).
 */
class DataTreeStructuralOperationsLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV131() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void shiftIntegerMinValueFailsClosedWithoutException() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("A"))
        ), ListElementKind.STRING);
        ShiftPathNode node = new ShiftPathNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_tree", tree,
                "input_shift", Integer.MIN_VALUE
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_PATH_DEPTH_EXCEEDED, outputs.get("output_error"));
        assertEquals(0, outputs.get("output_branch_count"));
    }

    @Test
    void shiftLargeNegativeTriggersDepthBudget() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("A"))
        ), ListElementKind.STRING);
        ShiftPathNode node = new ShiftPathNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_tree", tree,
                "input_shift", -100_000
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_PATH_DEPTH_EXCEEDED, outputs.get("output_error"));
    }

    @Test
    void shiftGreaterThanPathLengthCollapsesToEmptyPath() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("A"))
        ), ListElementKind.STRING);
        ShiftPathNode node = new ShiftPathNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_tree", tree,
                "input_shift", 2
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        DataTreeData shifted = assertInstanceOf(DataTreeData.class, outputs.get("output_tree"));
        assertEquals(1, shifted.getBranchCount());
        assertEquals(List.of("A"), shifted.getBranch(List.of()).items());
    }

    @Test
    void shiftMultipleBranchesCollapseToEmptyPathInEncounterOrder() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("A")),
                new DataTreeData.Branch(List.of(1), List.of("B"))
        ), ListElementKind.STRING);
        ShiftPathNode node = new ShiftPathNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_tree", tree,
                "input_shift", 1
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        DataTreeData shifted = assertInstanceOf(DataTreeData.class, outputs.get("output_tree"));
        assertEquals(1, shifted.getBranchCount());
        assertEquals(List.of("A", "B"), shifted.getBranch(List.of()).items());
    }

    @Test
    void mergeCombinedItemBudgetFailsBeforeOutputConstruction() {
        int halfCap = GenerationLimits.MAX_TREE_ITEMS / 2 + 1;
        DataTreeData treeA = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), java.util.Collections.nCopies(halfCap, "A"))
        ), ListElementKind.STRING);
        DataTreeData treeB = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(1), java.util.Collections.nCopies(halfCap, "B"))
        ), ListElementKind.STRING);

        MergeTreesNode node = new MergeTreesNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_a", treeA,
                "input_b", treeB
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_OUTPUT_BUDGET_EXCEEDED, outputs.get("output_error"));
        assertEquals(0, outputs.get("output_item_count"));
    }

    @Test
    void entwineCombinedItemBudgetFailsBeforeOutputConstruction() {
        int halfCap = GenerationLimits.MAX_TREE_ITEMS / 2 + 1;
        DataTreeData treeA = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), java.util.Collections.nCopies(halfCap, "A"))
        ), ListElementKind.STRING);
        DataTreeData treeB = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), java.util.Collections.nCopies(halfCap, "B"))
        ), ListElementKind.STRING);

        EntwineNode node = new EntwineNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_a", treeA,
                "input_b", treeB
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_OUTPUT_BUDGET_EXCEEDED, outputs.get("output_error"));
        assertEquals(0, outputs.get("output_item_count"));
    }

    @Test
    void mergePointAndVectorAtRuntimeFailsClosed() {
        DataTreeData pointTree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of(new PointData(0, 0, 0)))
        ), ListElementKind.POINT);
        DataTreeData vectorTree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of(new VectorData(1, 0, 0)))
        ), ListElementKind.VECTOR);

        MergeTreesNode node = new MergeTreesNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_a", pointTree,
                "input_b", vectorTree
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_ELEMENT_KIND_CONFLICT, outputs.get("output_error"));
    }

    @Test
    void entwinePointAndVectorAtRuntimeFailsClosed() {
        DataTreeData pointTree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of(new PointData(0, 0, 0)))
        ), ListElementKind.POINT);
        DataTreeData vectorTree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of(new VectorData(1, 0, 0)))
        ), ListElementKind.VECTOR);

        EntwineNode node = new EntwineNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_a", pointTree,
                "input_b", vectorTree
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_ELEMENT_KIND_CONFLICT, outputs.get("output_error"));
    }

    @Test
    void mergeConnectedNullInputFailsClosed() {
        DataTreeData treeA = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("A"))
        ), ListElementKind.STRING);
        MergeProbe probe = new MergeProbe();
        probe.putInput("input_a", treeA);
        probe.putInput("input_b", null);
        probe.connectInput("input_b", NodeDataType.DATA_TREE);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_INVALID_INPUT, probe.getOutput("output_error"));
    }

    @Test
    void entwineSkipsUndrivenOptionalPorts() {
        DataTreeData a = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("A"))
        ), ListElementKind.STRING);
        DataTreeData b = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("B"))
        ), ListElementKind.STRING);

        EntwineNode node = new EntwineNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_a", a,
                "input_b", b
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals("", outputs.get("output_error"));
        assertEquals(2, outputs.get("output_branch_count"));
    }

    @Test
    void entwineConnectedInvalidTypeFailsClosed() {
        EntwineProbe probe = new EntwineProbe();
        probe.putInput("input_a", new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("A"))
        ), ListElementKind.STRING));
        probe.putInput("input_b", "not-a-tree");
        probe.connectInput("input_b", NodeDataType.DATA_TREE);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_INVALID_INPUT, probe.getOutput("output_error"));
    }

    @Test
    void simplifySingleBranchRemovesFullPrefix() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0, 1, 2), List.of("A"))
        ), ListElementKind.STRING);
        SimplifyTreeNode node = new SimplifyTreeNode();
        Map<String, Object> outputs = node.compute(Map.of("input_tree", tree));
        assertTrue((Boolean) outputs.get("output_valid"));
        DataTreeData simplified = assertInstanceOf(DataTreeData.class, outputs.get("output_tree"));
        assertEquals(1, simplified.getBranchCount());
        assertEquals(List.of(), simplified.getBranch(List.of()).path());
        assertEquals(List.of("A"), simplified.getBranch(List.of()).items());
        TreePathData removed = assertInstanceOf(TreePathData.class, outputs.get("output_removed_prefix"));
        assertEquals(List.of(0, 1, 2), removed.indices());
    }

    @Test
    void simplifyWithEmptyPathDoesNotRemoveSharedPrefix() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(), List.of("A")),
                new DataTreeData.Branch(List.of(0, 1), List.of("B"))
        ), ListElementKind.STRING);
        SimplifyTreeNode node = new SimplifyTreeNode();
        Map<String, Object> outputs = node.compute(Map.of("input_tree", tree));
        assertTrue((Boolean) outputs.get("output_valid"));
        DataTreeData simplified = assertInstanceOf(DataTreeData.class, outputs.get("output_tree"));
        assertEquals(2, simplified.getBranchCount());
        TreePathData removed = assertInstanceOf(TreePathData.class, outputs.get("output_removed_prefix"));
        assertEquals(List.of(), removed.indices());
    }

    @Test
    void cullEmptyRemovesEmptyBranchesButPreservesPaths() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("A")),
                new DataTreeData.Branch(List.of(1), List.of()),
                new DataTreeData.Branch(List.of(2), List.of("B"))
        ), ListElementKind.STRING);
        CullEmptyBranchesNode node = new CullEmptyBranchesNode();
        Map<String, Object> outputs = node.compute(Map.of("input_tree", tree));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(1, outputs.get("output_removed_count"));
        DataTreeData culled = assertInstanceOf(DataTreeData.class, outputs.get("output_tree"));
        assertEquals(2, culled.getBranchCount());
        assertEquals(List.of("A"), culled.getBranch(List.of(0)).items());
        assertEquals(List.of("B"), culled.getBranch(List.of(2)).items());
    }

    @Test
    void shiftRejectsDoubleWhenConnected() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of("A"))
        ), ListElementKind.STRING);
        ShiftProbe probe = new ShiftProbe();
        probe.putInput("input_tree", tree);
        probe.putInput("input_shift", 1.9d);
        probe.connectInput("input_shift", NodeDataType.INTEGER);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals(DataTreeNodeUtils.ERROR_INVALID_SHIFT, probe.getOutput("output_error"));
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

    private static final class MergeProbe extends MergeTreesNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            DataTreeStructuralOperationsLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class EntwineProbe extends EntwineNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            DataTreeStructuralOperationsLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class ShiftProbe extends ShiftPathNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            DataTreeStructuralOperationsLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }
}
