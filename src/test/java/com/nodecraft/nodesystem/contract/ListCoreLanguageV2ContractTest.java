package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.math.list_sequence.CreateListNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.GetItemNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.InsertItemNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.RemoveItemNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.SetItemNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.SubListNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * List Core Strict Index & Null Contract v2 (Graph V126).
 */
class ListCoreLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV126() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void getItemRejectsDoubleIndexWhenConnected() {
        GetItemProbe probe = new GetItemProbe();
        probe.putInput("input_list", List.of("A", "B", "C"));
        probe.putInput("input_index", 1.9d);
        probe.connectInput("input_index", NodeDataType.INTEGER);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_found"));
    }

    @Test
    void getItemNegativeIndexReadsLast() {
        GetItemNode node = new GetItemNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of("A", "B", "C"),
                "input_index", -1
        ));
        assertTrue((Boolean) outputs.get("output_found"));
        assertEquals("C", outputs.get("output_item"));
    }

    @Test
    void setItemRejectsDoubleIndexWhenConnected() {
        SetItemProbe probe = new SetItemProbe();
        probe.putInput("input_list", List.of("A", "B"));
        probe.putInput("input_index", 1.9d);
        probe.putInput("input_value", "X");
        probe.connectInput("input_index", NodeDataType.INTEGER);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals(List.of(), probe.getOutput("output_list"));
    }

    @Test
    void setItemOutOfRangeFailsClosedEmpty() {
        SetItemNode node = new SetItemNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of("A", "B"),
                "input_index", 99,
                "input_value", "X"
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(SetItemNode.ERROR_INVALID_INDEX, outputs.get("output_error"));
        assertEquals(List.of(), outputs.get("output_list"));
    }

    @Test
    void insertItemNegativeIndexInsertsBeforeLast() {
        InsertItemNode node = new InsertItemNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", new ArrayList<>(List.of("A", "B", "C")),
                "input_index", -1,
                "input_value", "X"
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(List.of("A", "B", "X", "C"), outputs.get("output_list"));
    }

    @Test
    void removeItemRejectsDoubleIndexWhenConnected() {
        RemoveItemProbe probe = new RemoveItemProbe();
        probe.putInput("input_list", List.of("A", "B"));
        probe.putInput("input_index", 1.9d);
        probe.connectInput("input_index", NodeDataType.INTEGER);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
    }

    @Test
    void subListRejectsDoubleStartWhenConnected() {
        SubListProbe probe = new SubListProbe();
        probe.putInput("input_list", List.of("A", "B", "C"));
        probe.putInput("input_start", 1.9d);
        probe.connectInput("input_start", NodeDataType.INTEGER);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
    }

    @Test
    void subListUndrivenEndDefaultsToSize() {
        SubListNode node = new SubListNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", List.of("A", "B", "C"),
                "input_start", 1
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(List.of("B", "C"), outputs.get("output_sublist"));
    }

    @Test
    void createListPreservesConnectedNull() {
        CreateListProbe probe = new CreateListProbe();
        probe.setInputCount(3);
        probe.putInput("input_0", "A");
        probe.putInput("input_1", null);
        probe.putInput("input_2", "B");
        probe.processNode(null);
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) probe.getOutput("output_list");
        assertEquals(3, result.size());
        assertEquals("A", result.get(0));
        assertEquals(null, result.get(1));
        assertEquals("B", result.get(2));
    }

    @Test
    void createListSkipsUndrivenPort() {
        CreateListNode node = new CreateListNode();
        node.setInputCount(3);
        Map<String, Object> outputs = node.compute(Map.of(
                "input_0", "A",
                "input_2", "B"
        ));
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) outputs.get("output_list");
        assertEquals(List.of("A", "B"), result);
    }

    @Test
    void setItemDoesNotMutateInputList() {
        List<String> input = new ArrayList<>(List.of("A", "B", "C"));
        SetItemNode node = new SetItemNode();
        node.compute(Map.of(
                "input_list", input,
                "input_index", 1,
                "input_value", "X"
        ));
        assertEquals(List.of("A", "B", "C"), input);
    }

    @Test
    void insertItemDoesNotMutateInputList() {
        List<String> input = new ArrayList<>(List.of("A", "B"));
        InsertItemNode node = new InsertItemNode();
        node.compute(Map.of(
                "input_list", input,
                "input_index", 1,
                "input_value", "X"
        ));
        assertEquals(List.of("A", "B"), input);
    }

    @Test
    void removeItemDoesNotMutateInputList() {
        List<String> input = new ArrayList<>(List.of("A", "B", "C"));
        RemoveItemNode node = new RemoveItemNode();
        node.compute(Map.of(
                "input_list", input,
                "input_index", 1
        ));
        assertEquals(List.of("A", "B", "C"), input);
    }

    @Test
    void setItemOutputIsNewListInstance() {
        List<String> input = new ArrayList<>(List.of("A", "B"));
        SetItemNode node = new SetItemNode();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_list", input,
                "input_index", 0,
                "input_value", "X"
        ));
        assertNotSame(input, outputs.get("output_list"));
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

    private static final class GetItemProbe extends GetItemNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ListCoreLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class SetItemProbe extends SetItemNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ListCoreLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class RemoveItemProbe extends RemoveItemNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ListCoreLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class SubListProbe extends SubListNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            ListCoreLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class CreateListProbe extends CreateListNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }
}
