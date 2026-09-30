package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.input.values.BooleanToggleNode;
import com.nodecraft.nodesystem.nodes.input.values.ColorPickerNode;
import com.nodecraft.nodesystem.nodes.input.values.DropdownSelectorNode;
import com.nodecraft.nodesystem.nodes.input.values.FilePathInputNode;
import com.nodecraft.nodesystem.nodes.input.values.GradientRampNode;
import com.nodecraft.nodesystem.nodes.input.values.TextInputNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Input Values v1 language fence: six value sources, typed ports, Valid gates, V34 migration.
 */
class InputValuesLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV34() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }

    @Test
    void exactlySixInputValuesNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("input.values."))
                .sorted()
                .toList();
        assertEquals(6, ids.size(), "Expected 6 input.values nodes: " + ids);
        assertTrue(ids.contains("input.values.text_input"));
        assertTrue(ids.contains("input.values.color_picker"));
        assertTrue(ids.contains("input.values.boolean_toggle"));
        assertTrue(ids.contains("input.values.gradient_ramp"));
        assertTrue(ids.contains("input.values.dropdown"));
        assertTrue(ids.contains("input.values.file_path"));
        assertFalse(registry.getAllNodeIds().stream()
                .anyMatch(id -> id.equalsIgnoreCase("input.basic.text_input")
                        || id.equalsIgnoreCase("input.basic.color_picker")
                        || id.equalsIgnoreCase("input.basic.boolean_toggle")));
    }

    @Test
    void allInputValuesNodesArePure() {
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(TextInputNode.class, "input.values.text_input"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(ColorPickerNode.class, "input.values.color_picker"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(BooleanToggleNode.class, "input.values.boolean_toggle"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(GradientRampNode.class, "input.values.gradient_ramp"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(DropdownSelectorNode.class, "input.values.dropdown"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(FilePathInputNode.class, "input.values.file_path"));
    }

    @Test
    void colorPickerEmitsColorDataAndDoubleChannels() {
        ColorPickerNode node = new ColorPickerNode();
        assertEquals(NodeDataType.COLOR, findPort(node.getOutputPorts(), "output_color").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(node.getOutputPorts(), "output_red").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(node.getOutputPorts(), "output_green").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(node.getOutputPorts(), "output_blue").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(node.getOutputPorts(), "output_alpha").getDataType());
        node.processNode(null);
        assertInstanceOf(ColorData.class, node.getOutput("output_color"));
        assertInstanceOf(Double.class, node.getOutput("output_red"));
    }

    @Test
    void textInputHasNoMultilineProperty() {
        for (Field field : TextInputNode.class.getDeclaredFields()) {
            NodeProperty property = field.getAnnotation(NodeProperty.class);
            if (property == null) {
                continue;
            }
            assertFalse("multiline".equalsIgnoreCase(field.getName()), "Text Input must not expose multiline");
            assertFalse(property.displayName().toLowerCase(Locale.ROOT).contains("multiline"));
        }
    }

    @Test
    void valueListUsesStringListAndIntegerOnlyIndex() throws Exception {
        DropdownSelectorNode node = new DropdownSelectorNode();
        assertEquals(NodeDataType.STRING_LIST, findPort(node.getInputPorts(), "input_options").getDataType());
        assertEquals(NodeDataType.STRING_LIST, findPort(node.getOutputPorts(), "output_options").getDataType());
        assertEquals(NodeDataType.INTEGER, findPort(node.getInputPorts(), "input_index").getDataType());

        node.setOptions("A, B, C");
        node.setSelectedIndex(1);
        // Unconnected Index ignores non-Integer slot values and uses selectedIndex.
        node.setInput("input_index", 2.0d);
        node.processNode(null);
        assertEquals(1, node.getOutput("output_index"));
        assertEquals("B", node.getOutput("output_value"));
        assertTrue((Boolean) node.getOutput("output_valid"));

        ValueListProbe connectedIndex = new ValueListProbe();
        connectedIndex.setOptions("A, B, C");
        connectedIndex.setSelectedIndex(1);
        connectedIndex.connectInput("input_index", NodeDataType.INTEGER);
        connectedIndex.putRawInput("input_index", 2);
        connectedIndex.processNode(null);
        assertEquals(2, connectedIndex.getOutput("output_index"));
        assertEquals("C", connectedIndex.getOutput("output_value"));

        // Connected Options with non-String elements fail closed (no CSV fallback).
        ValueListProbe badOptions = new ValueListProbe();
        badOptions.setOptions("A, B, C");
        badOptions.connectInput("input_options", NodeDataType.STRING_LIST);
        badOptions.putRawInput("input_options", List.of(1, 2, 3));
        badOptions.processNode(null);
        assertFalse((Boolean) badOptions.getOutput("output_valid"));
        assertEquals("", badOptions.getOutput("output_value"));
        assertEquals(List.of(), badOptions.getOutput("output_options"));

        ValueListProbe mixedOptions = new ValueListProbe();
        mixedOptions.setOptions("A, B, C");
        mixedOptions.connectInput("input_options", NodeDataType.STRING_LIST);
        mixedOptions.putRawInput("input_options", List.of("A", 1, "B"));
        mixedOptions.processNode(null);
        assertFalse((Boolean) mixedOptions.getOutput("output_valid"));
        assertEquals("", mixedOptions.getOutput("output_value"));
        assertEquals(List.of(), mixedOptions.getOutput("output_options"));

        ValueListProbe clamp = new ValueListProbe();
        clamp.connectInput("input_options", NodeDataType.STRING_LIST);
        clamp.connectInput("input_index", NodeDataType.INTEGER);
        clamp.putRawInput("input_options", List.of("X", "Y"));
        clamp.putRawInput("input_index", 99);
        clamp.processNode(null);
        assertTrue((Boolean) clamp.getOutput("output_valid"));
        assertEquals(1, clamp.getOutput("output_index"));
        assertEquals("Y", clamp.getOutput("output_value"));
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
            InputValuesLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class GradientProbe extends GradientRampNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }

        void connectInput(String portId, NodeDataType outputType) {
            InputValuesLanguageContractTest.connectInput(this, portId, outputType);
        }
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

    @Test
    void gradientFiniteValidContractAndNoRampPort() {
        GradientRampNode node = new GradientRampNode();
        assertFalse(hasPort(node.getOutputPorts(), "output_ramp"));
        assertEquals(NodeDataType.COLOR, findPort(node.getOutputPorts(), "output_color").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(node.getOutputPorts(), "output_red").getDataType());

        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertInstanceOf(ColorData.class, node.getOutput("output_color"));
        assertTrue(Double.isFinite((Double) node.getOutput("output_red")));

        GradientProbe nanT = new GradientProbe();
        nanT.connectInput("input_t", NodeDataType.DOUBLE);
        nanT.putRawInput("input_t", Double.NaN);
        nanT.processNode(null);
        assertFalse((Boolean) nanT.getOutput("output_valid"));
        assertNull(nanT.getOutput("output_color"));
        assertTrue(Double.isNaN((Double) nanT.getOutput("output_red")));
        assertTrue(Double.isNaN((Double) nanT.getOutput("output_t")));
        assertEquals("", nanT.getOutput("output_hex"));

        node.setNodeState(Map.of(
                "gradientMode", "RADIAL",
                "radius", 0.0d,
                "centerX", 0.5d,
                "centerY", 0.5d,
                "angleDegrees", 0.0d,
                "stops", List.of(
                        Map.of("position", 0.0d, "r", 0.0d, "g", 0.0d, "b", 0.0d, "a", 1.0d),
                        Map.of("position", 1.0d, "r", 1.0d, "g", 1.0d, "b", 1.0d, "a", 1.0d)
                )
        ));
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertNull(node.getOutput("output_color"));
    }

    @Test
    void filePathValidIsSyntaxOnly() {
        FilePathInputNode node = new FilePathInputNode();
        assertEquals(NodeDataType.BOOLEAN, findPort(node.getOutputPorts(), "output_valid").getDataType());

        node.setSelectedPath("");
        assertFalse((Boolean) node.getOutput("output_has_value"));
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertEquals("", node.getOutput("output_path"));

        node.setSelectedPath("relative/file.txt");
        assertTrue((Boolean) node.getOutput("output_has_value"));
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals("relative/file.txt", node.getOutput("output_path"));

        node.setSelectedPath("bad\0path");
        assertTrue((Boolean) node.getOutput("output_has_value"));
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertEquals("bad\0path", node.getOutput("output_path"));
    }


    private static SavedNode findNode(SavedGraph graph, String nodeId) {
        return graph.nodes.stream()
                .filter(n -> nodeId.equals(n.nodeId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing node " + nodeId));
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
        node.typeId = typeId;
        return node;
    }

    private static SavedConnection wire(String sourceNode, String sourcePort, String targetNode, String targetPort) {
        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = sourceNode;
        connection.sourcePortId = sourcePort;
        connection.targetNodeId = targetNode;
        connection.targetPortId = targetPort;
        return connection;
    }

    private static boolean hasWire(SavedGraph graph, String sourceNode, String sourcePort,
                                   String targetNode, String targetPort) {
        return graph.connections.stream().anyMatch(c ->
                sourceNode.equals(c.sourceNodeId)
                        && sourcePort.equals(c.sourcePortId)
                        && targetNode.equals(c.targetNodeId)
                        && targetPort.equals(c.targetPortId));
    }

    private static IPort findPort(Iterable<IPort> ports, String id) {
        for (IPort port : ports) {
            if (id.equals(port.getId())) {
                return port;
            }
        }
        throw new AssertionError("missing port " + id);
    }

    private static boolean hasPort(Iterable<IPort> ports, String id) {
        for (IPort port : ports) {
            if (id.equals(port.getId())) {
                return true;
            }
        }
        return false;
    }
}
