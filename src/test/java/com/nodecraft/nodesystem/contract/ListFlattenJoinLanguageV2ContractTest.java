package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.math.ListFlattenOps;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * List Flatten Safety & Bounded String Join v2 (Graph V129).
 */
class ListFlattenJoinLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV129() {
        assertEquals(129, GraphFormatVersion.V129);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V129);
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
}
