package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeProperty;
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
        assertEquals(34, GraphFormatVersion.V34);
        assertEquals(35, GraphFormatVersion.V35);
        assertEquals(36, GraphFormatVersion.V36);
        assertEquals(GraphFormatVersion.V40, GraphFormatVersion.CURRENT);
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
        node.setInput("input_index", 2.0d); // Number but not Integer → ignored
        node.processNode(null);
        assertEquals(1, node.getOutput("output_index"));
        assertEquals("B", node.getOutput("output_value"));
        assertTrue((Boolean) node.getOutput("output_valid"));

        node.setInput("input_index", 2);
        node.processNode(null);
        assertEquals(2, node.getOutput("output_index"));
        assertEquals("C", node.getOutput("output_value"));

        // Bypass port compatibility so runtime can observe non-String list elements without coercion.
        putInput(node, "input_options", List.of(1, 2, 3));
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertEquals("", node.getOutput("output_value"));
        assertEquals(List.of(), node.getOutput("output_options"));

        // Mixed list must fail-closed (no silent String filtering).
        putInput(node, "input_options", List.of("A", 1, "B"));
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertEquals("", node.getOutput("output_value"));
        assertEquals(List.of(), node.getOutput("output_options"));

        node.setInput("input_options", List.of("X", "Y"));
        node.setInput("input_index", 99);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        assertEquals(1, node.getOutput("output_index"));
        assertEquals("Y", node.getOutput("output_value"));
    }

    @SuppressWarnings("unchecked")
    private static void putInput(DropdownSelectorNode node, String portId, Object value) throws Exception {
        Class<?> type = node.getClass();
        Field field = null;
        while (type != null) {
            try {
                field = type.getDeclaredField("inputValues");
                break;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (field == null) {
            throw new AssertionError("inputValues field not found");
        }
        field.setAccessible(true);
        ((Map<String, Object>) field.get(node)).put(portId, value);
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

        node.setInput("input_t", Double.NaN);
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertNull(node.getOutput("output_color"));
        assertTrue(Double.isNaN((Double) node.getOutput("output_red")));
        assertTrue(Double.isNaN((Double) node.getOutput("output_t")));
        assertEquals("", node.getOutput("output_hex"));

        node.setInput("input_t", 0.5d);
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

    @Test
    void v33ToV34RemapsTypesAndDropsIncompatibleWires() {
        SavedGraph v33 = new SavedGraph();
        v33.formatVersion = GraphFormatVersion.V33;

        SavedNode text = savedNode("text", "input.basic.text_input");
        SavedNode color = savedNode("color", "input.basic.color_picker");
        SavedNode toggle = savedNode("toggle", "input.basic.boolean_toggle");
        SavedNode dropdown = savedNode("dropdown", "input.values.dropdown");
        SavedNode createList = savedNode("list", "math.list.create_list");
        SavedNode ramp = savedNode("ramp", "input.values.gradient_ramp");
        SavedNode viewer = savedNode("viewer", "output.preview.geometry_viewer");

        v33.nodes = new ArrayList<>(List.of(text, color, toggle, dropdown, createList, ramp, viewer));
        v33.connections = new ArrayList<>(List.of(
                // Former FLOAT channel → FLOAT target stays (DOUBLE↔FLOAT numeric)
                wire("color", "output_red", "viewer", "input_transparency"),
                // Channel → STRING_LIST options: incompatible after DOUBLE tighten
                wire("color", "output_green", "dropdown", "input_options"),
                // ANY/LIST options → STRING_LIST: drop
                wire("list", "output_list", "dropdown", "input_options"),
                // Gradient ramp port removed
                wire("ramp", "output_ramp", "list", "input_0")
        ));
        v33.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v33);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);

        assertEquals("input.values.text_input", findNode(migrated, "text").typeId);
        assertEquals("input.values.color_picker", findNode(migrated, "color").typeId);
        assertEquals("input.values.boolean_toggle", findNode(migrated, "toggle").typeId);

        assertTrue(hasWire(migrated, "color", "output_red", "viewer", "input_transparency"));
        assertFalse(hasWire(migrated, "color", "output_green", "dropdown", "input_options"));
        assertFalse(hasWire(migrated, "list", "output_list", "dropdown", "input_options"));
        assertFalse(hasWire(migrated, "ramp", "output_ramp", "list", "input_0"));
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
