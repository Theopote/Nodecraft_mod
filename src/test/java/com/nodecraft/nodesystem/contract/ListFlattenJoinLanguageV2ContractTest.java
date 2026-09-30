package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.math.ListFlattenOps;
import com.nodecraft.nodesystem.nodes.math.list_sequence.CreateListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.FlattenListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.JoinStringsNode;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * List Flatten Safety & Bounded String Join v2 (Graph V129).
 */
class ListFlattenJoinLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV129() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void flattenNestedList() {
        FlattenListNode node = new FlattenListNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of(List.of("A", "B"), List.of("C"))
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(List.of("A", "B", "C"), outputs.get("output_list"));
    }

    @Test
    void flattenPreservesNullLeaves() {
        List<Object> withNull = new ArrayList<>();
        withNull.add("A");
        withNull.add(null);
        withNull.add("B");
        FlattenListNode node = new FlattenListNode();
        Map<String, Object> outputs = node.compute(Map.of("input_list", withNull));
        assertTrue((Boolean) outputs.get("output_valid"));
        @SuppressWarnings("unchecked")
        List<Object> flattened = (List<Object>) outputs.get("output_list");
        assertEquals(3, flattened.size());
        assertEquals("A", flattened.get(0));
        assertNull(flattened.get(1));
        assertEquals("B", flattened.get(2));
    }

    @Test
    void flattenLargeFlatListSucceedsWithoutPerElementCopies() {
        List<Object> leaves = new ArrayList<>();
        for (int i = 0; i < 8_000; i++) {
            leaves.add(i);
        }
        Map<String, Object> outputs = new FlattenListNode().compute(Map.of("input_list", leaves));
        assertTrue((Boolean) outputs.get("output_valid"));
        @SuppressWarnings("unchecked")
        List<Object> flattened = (List<Object>) outputs.get("output_list");
        assertEquals(8_000, flattened.size());
        assertEquals(0, flattened.get(0));
        assertEquals(7_999, flattened.get(7_999));
    }

    @Test
    void createListWithDrivenNullSurvivesFlatten() {
        CreateListProbe create = new CreateListProbe();
        create.setInputCount(3);
        create.putInput("input_0", "A");
        create.putInput("input_1", null);
        create.putInput("input_2", "B");
        create.processNode(null);
        @SuppressWarnings("unchecked")
        List<Object> created = (List<Object>) create.getOutput("output_list");
        assertEquals(3, created.size());
        assertNull(created.get(1));

        Map<String, Object> flattened = new FlattenListNode().compute(Map.of("input_list", created));
        assertTrue((Boolean) flattened.get("output_valid"));
        assertEquals("", flattened.get("output_error"));
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) flattened.get("output_list");
        assertEquals(3, items.size());
        assertEquals("A", items.get(0));
        assertNull(items.get(1));
        assertEquals("B", items.get(2));
    }

    @Test
    void flattenDepthZeroPreservesNestedLists() {
        FlattenListNode node = new FlattenListNode();
        node.setMaxDepth(0);
        List<Object> nested = List.of("A", "B");
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of(nested, "C")
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(List.of(nested, "C"), outputs.get("output_list"));
    }

    @Test
    void flattenDepthOneFlattensSingleLevel() {
        FlattenListNode node = new FlattenListNode();
        node.setMaxDepth(1);
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of(List.of("A", "B"), List.of("C"))
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(List.of("A", "B", "C"), outputs.get("output_list"));
    }

    @Test
    void flattenCycleReferenceFailsClosed() {
        List<Object> cyclic = new ArrayList<>();
        cyclic.add("A");
        cyclic.add(cyclic);
        FlattenListNode node = new FlattenListNode();
        Map<String, Object> outputs = node.compute(Map.of("input_list", cyclic));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(ListFlattenOps.ERROR_CYCLE_REFERENCE, outputs.get("output_error"));
        assertEquals(List.of(), outputs.get("output_list"));
    }

    @Test
    void flattenDeepNestBeyondCapFails() {
        List<Object> nested = List.of("leaf");
        for (int i = 0; i < GenerationLimits.MAX_FORMAT_DEPTH + 2; i++) {
            nested = new ArrayList<>(List.of(nested));
        }
        FlattenListNode node = new FlattenListNode();
        Map<String, Object> outputs = node.compute(Map.of("input_list", nested));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(ListFlattenOps.ERROR_DEPTH_EXCEEDED, outputs.get("output_error"));
    }

    @Test
    void flattenNonListInputFails() {
        FlattenListNode node = new FlattenListNode();
        Map<String, Object> outputs = node.compute(Map.of("input_list", "STONE"));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(FlattenListNode.ERROR_INVALID_INPUT, outputs.get("output_error"));
    }

    @Test
    void flattenRejectsDoubleDepthWhenConnected() {
        FlattenListProbe probe = new FlattenListProbe();
        probe.putInput("input_list", List.of(List.of("A")));
        probe.putInput("input_depth", 2.9d);
        probe.connectInput("input_depth", NodeDataType.INTEGER);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals(FlattenListNode.ERROR_INVALID_DEPTH, probe.getOutput("output_error"));
    }

    @Test
    void flattenDepthPropertyRejectsValuesBelowMinusOne() {
        FlattenListNode node = new FlattenListNode();
        assertEquals(-1, node.getMaxDepth());
        node.setMaxDepth(-2);
        assertEquals(-1, node.getMaxDepth());

        node.setMaxDepth(2);
        assertEquals(2, node.getMaxDepth());
        node.setNodeState(Map.of("maxDepth", -5));
        assertEquals(2, node.getMaxDepth());
    }

    @Test
    void flattenRejectsConnectedDepthBelowMinusOne() {
        FlattenListProbe probe = new FlattenListProbe();
        probe.putInput("input_list", List.of(List.of("A"), "B"));
        probe.putInput("input_depth", -2);
        probe.connectInput("input_depth", NodeDataType.INTEGER);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals(FlattenListNode.ERROR_INVALID_DEPTH, probe.getOutput("output_error"));
    }

    @Test
    void flattenDoesNotUnwrapObjectArray() {
        FlattenListNode node = new FlattenListNode();
        Object[] array = new Object[] {"C", "D"};
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of(List.of("A", "B"), array)
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(List.of("A", "B", array), outputs.get("output_list"));
    }

    @Test
    void joinEmptyList() {
        JoinStringsNode node = new JoinStringsNode();
        Map<String, Object> outputs = node.compute(Map.of("input_list", List.of()));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals("", outputs.get("output_result"));
    }

    @Test
    void joinNonStringElementFails() {
        JoinStringsNode node = new JoinStringsNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of("A", 100)
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
    }

    @Test
    void joinOversizedOutputFailsPreflight() {
        JoinStringsNode node = new JoinStringsNode();
        String chunk = "x".repeat(GenerationLimits.MAX_JOIN_STRING_OUTPUT_CHARS);
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of(chunk, "y")
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals("Joined text exceeds output limit", outputs.get("output_error"));
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

    private static final class FlattenListProbe extends FlattenListNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ListFlattenJoinLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class CreateListProbe extends CreateListNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }
}
