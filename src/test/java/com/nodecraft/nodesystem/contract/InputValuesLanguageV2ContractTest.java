package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.nodes.input.values.BooleanToggleNode;
import com.nodecraft.nodesystem.nodes.input.values.DropdownSelectorNode;
import com.nodecraft.nodesystem.nodes.input.values.GradientRampNode;
import com.nodecraft.nodesystem.nodes.input.values.TextInputNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Input Values Optional Drive Strictness v2 (Graph V110).
 */
class InputValuesLanguageV2ContractTest {

    @Test
    void currentGraphFormatIsAtLeastV110() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void valueListIndexConnectedNullFailsClosed() {
        ValueListProbe probe = new ValueListProbe();
        probe.setOptions("A, B, C");
        probe.setSelectedIndex(1);
        probe.connectInput("input_index", NodeDataType.INTEGER);
        probe.putRawInput("input_index", null);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals("", probe.getOutput("output_value"));
        assertEquals(List.of(), probe.getOutput("output_options"));
    }

    @Test
    void valueListOptionsConnectedNullFailsClosedWithoutCsvFallback() {
        ValueListProbe probe = new ValueListProbe();
        probe.setOptions("A, B, C");
        probe.connectInput("input_options", NodeDataType.STRING_LIST);
        probe.putRawInput("input_options", null);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals("", probe.getOutput("output_value"));
        assertEquals(List.of(), probe.getOutput("output_options"));
    }

    @Test
    void valueListIndexUnconnectedUsesSelectedIndex() {
        DropdownSelectorNode node = new DropdownSelectorNode();
        node.setOptions("A, B, C");
        node.setSelectedIndex(2);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals(2, node.getOutput("output_index"));
        assertEquals("C", node.getOutput("output_value"));
        assertEquals("", node.getOutput("output_error"));
    }

    @Test
    void valueListConnectedIndexOutOfRangeFailsClosedWithError() {
        ValueListProbe probe = new ValueListProbe();
        probe.setOptions("A, B, C");
        probe.connectInput("input_index", NodeDataType.INTEGER);
        probe.putRawInput("input_index", 99);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals("", probe.getOutput("output_value"));
        assertTrue(((String) probe.getOutput("output_error")).contains("out of range"));
    }

    @Test
    void valueListUnconnectedIndexOutOfRangeClamps() {
        DropdownSelectorNode node = new DropdownSelectorNode();
        node.setOptions("A, B, C");
        node.setSelectedIndex(99);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals(2, node.getOutput("output_index"));
        assertEquals("C", node.getOutput("output_value"));
    }

    @Test
    void textMaxLengthHardCapIgnoresAbove32767() {
        TextInputNode node = new TextInputNode();
        node.setMaxLength(100);
        assertEquals(100, node.getMaxLength());
        node.setMaxLength(40000);
        assertEquals(100, node.getMaxLength());
        node.setMaxLength(0);
        assertEquals(32767, node.getMaxLength());
    }

    @Test
    void booleanRestoreAcceptsBooleanOnly() {
        BooleanToggleNode node = new BooleanToggleNode();
        node.setNodeState(Map.of("value", true));
        node.processNode(null);
        assertEquals(true, node.getOutput("output_value"));

        node.setNodeState(Map.of("value", false));
        node.processNode(null);
        assertEquals(false, node.getOutput("output_value"));

        // String/Number coercion dropped: keep previous value.
        node.setNodeState(Map.of("value", "true"));
        node.processNode(null);
        assertEquals(false, node.getOutput("output_value"));
        node.setNodeState(1);
        node.processNode(null);
        assertEquals(false, node.getOutput("output_value"));
        node.setNodeState(Boolean.TRUE);
        node.processNode(null);
        assertEquals(true, node.getOutput("output_value"));
    }

    @Test
    void valueListOptionsUnconnectedUsesCsv() {
        DropdownSelectorNode node = new DropdownSelectorNode();
        node.setOptions("Red, Green, Blue");
        node.setSelectedIndex(0);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals(List.of("Red", "Green", "Blue"), node.getOutput("output_options"));
        assertEquals("Red", node.getOutput("output_value"));
    }

    @Test
    void gradientTConnectedNullFailsClosed() {
        GradientProbe probe = new GradientProbe();
        probe.connectInput("input_t", NodeDataType.DOUBLE);
        probe.putRawInput("input_t", null);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals(null, probe.getOutput("output_color"));
    }

    @Test
    void gradientTUnconnectedDefaultsToHalfAndRemainsValid() {
        GradientRampNode node = new GradientRampNode();
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertInstanceOf(ColorData.class, node.getOutput("output_color"));
        assertEquals(0.5d, (Double) node.getOutput("output_t"), 1.0e-9d);
    }

    @Test
    void gradientTConnectedIntegerFailsClosed() {
        GradientProbe probe = new GradientProbe();
        probe.connectInput("input_t", NodeDataType.DOUBLE);
        probe.putRawInput("input_t", 1);
        probe.processNode(null);
        assertFalse((Boolean) probe.getOutput("output_valid"));
        assertEquals(null, probe.getOutput("output_color"));
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
            .filter(port -> inputPortId.equals(port.getId()))
            .findFirst()
            .orElseThrow();
        assertTrue(output.connectTo(input));
    }

    private static final class ValueListProbe extends DropdownSelectorNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            InputValuesLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class GradientProbe extends GradientRampNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            InputValuesLanguageV2ContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }
}
