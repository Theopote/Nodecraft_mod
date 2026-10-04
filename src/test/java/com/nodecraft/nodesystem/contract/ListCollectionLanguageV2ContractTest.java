package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.DataTreeData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.math.data_tree.DataTreeNodeUtils;
import com.nodecraft.nodesystem.nodes.math.list_sequence.DeduplicateListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.FilterListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.GroupListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.ReverseListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.ShuffleListNode;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * List Collection Strict Grouping & Seed Contract v2 (Graph V127).
 */
class ListCollectionLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV127() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void groupListLengthMismatchFailsClosed() {
        GroupListNode node = new GroupListNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of("A", "B", "C", "D"),
                "input_keys", List.of(1, 2)
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(GroupListNode.ERROR_LENGTH_MISMATCH, outputs.get("output_error"));
        DataTreeData tree = assertInstanceOf(DataTreeData.class, outputs.get("output_tree"));
        assertTrue(tree.getBranches().isEmpty());
        assertEquals(List.of(), outputs.get("output_unique_keys"));
        assertEquals(0, outputs.get("output_group_count"));
    }

    @Test
    void groupListNullItemFailsClosed() {
        GroupListNode node = new GroupListNode();
        List<Object> list = new ArrayList<>(List.of("A", "B"));
        list.add(1, null);
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", list,
                "input_keys", List.of("x", "y", "z")
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(GroupListNode.ERROR_NULL_ITEM, outputs.get("output_error"));
    }

    @Test
    void groupListNullKeyFailsWhenSkipInvalidKeysFalse() {
        GroupListNode node = new GroupListNode();
        node.setSkipInvalidKeys(false);
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("input_list", List.of("A", "B"));
        inputs.put("input_keys", listWithNullAt(1, "k1"));
        Map<String, Object> outputs = node.compute(inputs);
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(GroupListNode.ERROR_NULL_KEY, outputs.get("output_error"));
    }

    @Test
    void groupListNullKeySkippedWhenSkipInvalidKeysTrue() {
        GroupListNode node = new GroupListNode();
        node.setSkipInvalidKeys(true);
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("input_list", List.of("A", "B", "C"));
        inputs.put("input_keys", listWithNullAt(1, "k1", "k1"));
        Map<String, Object> outputs = node.compute(inputs);
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals("", outputs.get("output_error"));
        assertEquals(List.of("k1"), outputs.get("output_unique_keys"));
        assertEquals(1, outputs.get("output_group_count"));
        DataTreeData tree = assertInstanceOf(DataTreeData.class, outputs.get("output_tree"));
        assertEquals(1, tree.getBranches().size());
        assertEquals(List.of("A", "C"), tree.getBranches().getFirst().items());
    }

    @Test
    void groupListHappyPathUnchanged() {
        GroupListNode node = new GroupListNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of("A", "B", "C", "D"),
                "input_keys", List.of("x", "y", "x", "z")
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals("", outputs.get("output_error"));
        assertEquals(List.of("x", "y", "z"), outputs.get("output_unique_keys"));
        assertEquals(3, outputs.get("output_group_count"));
        DataTreeData tree = assertInstanceOf(DataTreeData.class, outputs.get("output_tree"));
        assertEquals(3, tree.getBranches().size());
    }

    @Test
    void groupListConstructionBudgetHelperRejectsOversize() {
        DataTreeNodeUtils.ParseResult<Void> over =
                DataTreeNodeUtils.preflightTreeConstruction(10, 10, 1, 5, 5, 8);
        assertFalse(over.valid());
        assertEquals(DataTreeNodeUtils.ERROR_OUTPUT_BUDGET_EXCEEDED, over.error());
    }

    @Test
    void deduplicateCollapsesEqualPointData() {
        PointData a = new PointData(1, 2, 3);
        PointData b = new PointData(1, 2, 3);
        DeduplicateListNode node = new DeduplicateListNode();
        Map<String, Object> outputs = node.compute(Map.of("input_list", List.of(a, b)));
        assertTrue((Boolean) outputs.get("output_valid"));
        @SuppressWarnings("unchecked")
        List<Object> unique = (List<Object>) outputs.get("output_unique");
        assertEquals(1, unique.size());
        assertEquals(a, unique.getFirst());
        assertEquals(1, outputs.get("output_removed_count"));
    }

    @Test
    void deduplicateCollapsesEqualBlockPlacementData() {
        BlockPlacementData first = new BlockPlacementData(new BlockPos(1, 2, 3), "minecraft:stone");
        BlockPlacementData second = new BlockPlacementData(new BlockPos(1, 2, 3), "minecraft:stone");
        DeduplicateListNode node = new DeduplicateListNode();
        Map<String, Object> outputs = node.compute(Map.of("input_list", List.of(first, second)));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(1, outputs.get("output_unique_count"));
        assertEquals(1, outputs.get("output_removed_count"));
    }

    @Test
    void shuffleRejectsDoubleSeedWhenConnected() {
        ShuffleListProbe probe = new ShuffleListProbe();
        probe.putInput("input_list", List.of(1, 2, 3, 4));
        probe.putInput("input_seed", 1.9d);
        probe.connectInput("input_seed", NodeDataType.INTEGER);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals(ShuffleListNode.ERROR_INVALID_INPUT, probe.getOutput("output_error"));
        assertEquals(List.of(), probe.getOutput("output_list"));
    }

    @Test
    void shuffleUndrivenPropertySeedIsDeterministic() {
        List<Integer> source = List.of(1, 2, 3, 4, 5, 6, 7, 8);
        ShuffleListNode a = new ShuffleListNode();
        ShuffleListNode b = new ShuffleListNode();
        a.setSeed(42);
        b.setSeed(42);
        Map<String, Object> outA = a.compute(Map.of("input_list", new ArrayList<>(source)));
        Map<String, Object> outB = b.compute(Map.of("input_list", new ArrayList<>(source)));
        assertTrue((Boolean) outA.get("output_valid"));
        assertTrue((Boolean) outB.get("output_valid"));
        assertEquals(outA.get("output_list"), outB.get("output_list"));
    }

    @Test
    void shuffleDoesNotMutateInputList() {
        List<Integer> input = new ArrayList<>(List.of(1, 2, 3, 4));
        ShuffleListNode node = new ShuffleListNode();
        node.setSeed(7);
        node.compute(Map.of("input_list", input));
        assertEquals(List.of(1, 2, 3, 4), input);
    }

    @Test
    void reverseDoesNotMutateInputList() {
        List<String> input = new ArrayList<>(List.of("A", "B", "C"));
        ReverseListNode node = new ReverseListNode();
        node.compute(Map.of("input_list", input));
        assertEquals(List.of("A", "B", "C"), input);
    }

    @Test
    void filterInvalidBooleanAtEndOfMaskFails() {
        FilterListNode node = new FilterListNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of("A", "B", "C"),
                "input_condition", List.of(true, false, "not-boolean")
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(List.of(), outputs.get("output_list"));
    }

    @Test
    void filterNullElementWithValidMaskStillValid() {
        FilterListNode node = new FilterListNode();
        List<Object> list = new ArrayList<>(List.of("A", "B"));
        list.add(1, null);
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("input_list", list);
        inputs.put("input_condition", List.of(true, false, true));
        Map<String, Object> outputs = node.compute(inputs);
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(List.of("A", "B"), outputs.get("output_list"));
        @SuppressWarnings("unchecked")
        List<Object> removed = (List<Object>) outputs.get("output_removed");
        assertEquals(1, removed.size());
        assertEquals(null, removed.getFirst());
    }

    private static List<Object> listWithNullAt(int index, Object... values) {
        List<Object> list = new ArrayList<>(List.of(values));
        list.add(index, null);
        return list;
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

    private static final class ShuffleListProbe extends ShuffleListNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ListCollectionLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }
}
