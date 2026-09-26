package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.input.numeric.AngleSliderNode;
import com.nodecraft.nodesystem.nodes.input.numeric.CircularAngleNode;
import com.nodecraft.nodesystem.nodes.input.numeric.ENode;
import com.nodecraft.nodesystem.nodes.input.numeric.FloatInputNode;
import com.nodecraft.nodesystem.nodes.input.numeric.FloatSliderNode;
import com.nodecraft.nodesystem.nodes.input.numeric.PiNode;
import com.nodecraft.nodesystem.nodes.input.numeric.RangeInputNode;
import com.nodecraft.nodesystem.nodes.input.numeric.XYSliderNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Input Numeric v1 language fence: finite graph values, UI-only precision, typed ports.
 */
class InputNumericLanguageContractTest {

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
        assertEquals(33, GraphFormatVersion.V33);
        assertEquals(34, GraphFormatVersion.V34);
        assertEquals(35, GraphFormatVersion.V35);
        assertEquals(36, GraphFormatVersion.V36);
        assertEquals(GraphFormatVersion.V56, GraphFormatVersion.CURRENT);
    }

    @Test
    void floatInputRejectsNonFiniteAndDoesNotQuantize() {
        FloatInputNode node = new FloatInputNode();
        node.setValue(1.23456789d);
        node.setPrecision(2);
        assertEquals(1.23456789d, node.getValue(), 0.0d);

        node.setValue(Double.NaN);
        assertEquals(1.23456789d, node.getValue(), 0.0d);

        node.setValue(Double.POSITIVE_INFINITY);
        assertEquals(1.23456789d, node.getValue(), 0.0d);
    }

    @Test
    void floatInputAllowsInfinityBounds() {
        FloatInputNode node = new FloatInputNode();
        node.setMinValue(Double.NEGATIVE_INFINITY);
        node.setMaxValue(Double.POSITIVE_INFINITY);
        node.setValue(1.0e200d);
        assertEquals(1.0e200d, node.getValue(), 0.0d);
    }

    @Test
    void floatSliderDoesNotQuantizeGraphValue() {
        FloatSliderNode node = new FloatSliderNode();
        node.setMinValue(0.0d);
        node.setMaxValue(100.0d);
        node.setDecimalPlaces(0);
        node.setCurrentValue(33.333333d);
        assertEquals(33.333333d, node.getCurrentValue(), 0.0d);
    }

    @Test
    void floatSliderRejectsNonFiniteValue() {
        FloatSliderNode node = new FloatSliderNode();
        node.setCurrentValue(42.0d);
        node.setCurrentValue(Double.NaN);
        assertEquals(42.0d, node.getCurrentValue(), 0.0d);
    }

    @Test
    void floatInputStateNaNRemainsFinite() {
        FloatInputNode node = new FloatInputNode();
        node.setValue(3.0d);
        node.setNodeState(Map.of("value", Double.NaN));
        assertTrue(Double.isFinite(node.getValue()));
        assertEquals(3.0d, node.getValue(), 0.0d);
    }

    @Test
    void floatSliderStateInfinityRemainsFinite() {
        FloatSliderNode node = new FloatSliderNode();
        node.setCurrentValue(12.0d);
        node.setNodeState(Map.of("currentValue", Double.POSITIVE_INFINITY));
        assertTrue(Double.isFinite(node.getCurrentValue()));
        assertEquals(12.0d, node.getCurrentValue(), 0.0d);
    }

    @Test
    void floatSliderRejectsOverflowSpan() {
        FloatSliderNode node = new FloatSliderNode();
        node.setMinValue(-Double.MAX_VALUE);
        node.setMaxValue(Double.MAX_VALUE);
        assertEquals(0.0d, node.getMinValue(), 0.0d);
        assertEquals(100.0d, node.getMaxValue(), 0.0d);
    }

    @Test
    void circularAnglePickerRejectsNaNBeforeWrap() {
        CircularAngleNode node = new CircularAngleNode();
        node.setAngle(90.0d);
        node.setAngle(Double.NaN);
        assertEquals(90.0d, node.getAngle(), 0.0d);
    }

    @Test
    void circularAnglePickerWrapsToZeroInclusiveRange() {
        CircularAngleNode node = new CircularAngleNode();
        node.setAngle(370.0d);
        assertEquals(10.0d, node.getAngle(), 1.0e-12);
        node.setAngle(-10.0d);
        assertEquals(350.0d, node.getAngle(), 1.0e-12);
    }

    @Test
    void circularPickerStateNaNRemainsFinite() {
        CircularAngleNode node = new CircularAngleNode();
        node.setAngle(90.0d);
        node.setNodeState(Map.of("angle", Double.NaN));
        assertTrue(Double.isFinite(node.getAngle()));
        assertEquals(90.0d, node.getAngle(), 0.0d);
    }

    @Test
    void angleSliderRejectsNonFiniteAngle() {
        AngleSliderNode node = new AngleSliderNode();
        node.setCurrentAngle(45.0d);
        node.setCurrentAngle(Double.NaN);
        assertEquals(45.0d, node.getCurrentAngle(), 0.0d);
    }

    @Test
    void angleSliderStateNaNRemainsFinite() {
        AngleSliderNode node = new AngleSliderNode();
        node.setCurrentAngle(45.0d);
        node.setNodeState(Map.of("angle", Double.NaN));
        assertTrue(Double.isFinite(node.getCurrentAngle()));
        assertEquals(45.0d, node.getCurrentAngle(), 0.0d);
    }

    @Test
    void xySliderHasDoubleListUvAndNoVectorPort() {
        XYSliderNode node = new XYSliderNode();
        assertFalse(hasPort(node.getOutputPorts(), "output_vector"));
        IPort uv = findPort(node.getOutputPorts(), "output_uv");
        assertEquals(NodeDataType.DOUBLE_LIST, uv.getDataType());

        node.processNode(null);
        List<?> uvValues = assertInstanceOf(List.class, node.getOutput("output_uv"));
        assertEquals(2, uvValues.size());
    }

    @Test
    void xySliderZeroWidthRangeNormalizesToZero() {
        XYSliderNode node = new XYSliderNode();
        node.setMaxX(5.0d);
        node.setMinX(5.0d);
        node.setX(5.0d);
        node.processNode(null);
        List<?> uv = assertInstanceOf(List.class, node.getOutput("output_uv"));
        assertEquals(0.0d, (Double) uv.get(0), 0.0d);
    }

    @Test
    void xySliderStateNonFiniteRemainsFinite() {
        XYSliderNode node = new XYSliderNode();
        node.setX(0.25d);
        node.setY(0.75d);
        node.setNodeState(Map.of(
                "x", Double.NaN,
                "y", Double.POSITIVE_INFINITY,
                "minX", Double.POSITIVE_INFINITY,
                "step", Double.NaN
        ));
        node.processNode(null);
        assertTrue(Double.isFinite(node.getX()));
        assertTrue(Double.isFinite(node.getY()));
        List<?> uv = assertInstanceOf(List.class, node.getOutput("output_uv"));
        assertTrue(Double.isFinite((Double) uv.get(0)));
        assertTrue(Double.isFinite((Double) uv.get(1)));
    }

    @Test
    void domainInputRejectsNonFiniteEndpoints() {
        RangeInputNode node = new RangeInputNode();
        node.setStart(1.0d);
        node.setEnd(2.0d);
        node.setStart(Double.NaN);
        node.setEnd(Double.POSITIVE_INFINITY);
        assertEquals(1.0d, node.getStart(), 0.0d);
        assertEquals(2.0d, node.getEnd(), 0.0d);
    }

    @Test
    void domainInputSpanIsNaNOnOverflow() {
        RangeInputNode node = new RangeInputNode();
        node.setStart(Double.MAX_VALUE);
        node.setEnd(-Double.MAX_VALUE);
        node.processNode(null);
        assertTrue(Double.isNaN((Double) node.getOutput("output_span")));
    }

    @Test
    void piAndEExposeOutputValuePort() {
        PiNode pi = new PiNode();
        ENode e = new ENode();
        assertEquals(NodeDataType.DOUBLE, findPort(pi.getOutputPorts(), "output_value").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(e.getOutputPorts(), "output_value").getDataType());
        assertFalse(hasPort(pi.getOutputPorts(), "output_pi"));
        assertFalse(hasPort(e.getOutputPorts(), "output_e"));
        assertEquals(Math.PI, (Double) pi.getOutput("output_value"), 0.0d);
        assertEquals(Math.E, (Double) e.getOutput("output_value"), 0.0d);
    }

    @Test
    void v30ToV31RemapsPiPortAndDropsXyVectorWire() {
        SavedGraph v30 = new SavedGraph();
        v30.formatVersion = GraphFormatVersion.V30;

        SavedNode pi = savedNode("pi", "input.numeric.pi");
        SavedNode xy = savedNode("xy", "input.numeric.xy_slider");
        SavedNode sin = savedNode("sin", "math.trigonometry.sin");
        SavedNode construct = savedNode("construct", "reference.vectors.construct_vector");

        v30.nodes = new ArrayList<>(List.of(pi, xy, sin, construct));
        v30.connections = new ArrayList<>(List.of(
                wire("pi", "output_pi", "sin", "input_angle"),
                wire("xy", "output_vector", "construct", "input_x"),
                wire("xy", "output_x", "construct", "input_y")
        ));
        v30.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v30);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertTrue(hasWire(migrated, "pi", "output_value", "sin", "input_angle"));
        assertFalse(hasWire(migrated, "pi", "output_pi", "sin", "input_angle"));
        assertFalse(hasWire(migrated, "xy", "output_vector", "construct", "input_x"));
        assertTrue(hasWire(migrated, "xy", "output_x", "construct", "input_y"));
    }

    @Test
    void exactlyTenInputNumericNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("input.numeric."))
                .sorted()
                .toList();
        assertEquals(10, ids.size(), "Expected 10 input.numeric nodes: " + ids);
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
