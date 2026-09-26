package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.DataSeriesNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.MathRangeNode;
import com.nodecraft.nodesystem.nodes.math.list_sequence.RepeatNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sequence v1 language fence: bounded generation, exact step, finite lists, strict INTEGER Count.
 */
class SequenceLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void exactlyThreeSequenceNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("math.sequence."))
                .sorted()
                .collect(Collectors.toList());
        assertEquals(3, ids.size(), "Expected 3 sequence nodes: " + ids);
    }

    @Test
    void seriesHasNoSumPort() {
        DataSeriesNode node = new DataSeriesNode();
        assertNull(findPortOrNull(node, "output_sum"));
        assertNotNull(findPort(node, "output_series"));
    }

    @Test
    void rangeEmitsFiniteBoundedLists() {
        MathRangeNode node = new MathRangeNode();
        @SuppressWarnings("unchecked")
        List<Double> values = (List<Double>) node.compute(Map.of(
                "input_start", 0.0d,
                "input_end", 1.0d,
                "input_step", 0.25d
        )).get("output_numbers");
        assertEquals(List.of(0.0d, 0.25d, 0.5d, 0.75d, 1.0d), values);

        @SuppressWarnings("unchecked")
        List<Double> empty = (List<Double>) node.compute(Map.of(
                "input_start", 0.0d,
                "input_end", 10.0d,
                "input_step", 0.0d
        )).get("output_numbers");
        assertTrue(empty.isEmpty());

        @SuppressWarnings("unchecked")
        List<Double> huge = (List<Double>) node.compute(Map.of(
                "input_start", 1.0e308d,
                "input_end", Double.MAX_VALUE,
                "input_step", 1.0d
        )).get("output_numbers");
        assertTrue(huge.size() <= GenerationLimits.MAX_LIST_ELEMENTS);
        for (Double value : huge) {
            assertTrue(Double.isFinite(value));
        }
    }

    @Test
    void seriesRejectsNonIntegerCountAndStopsBeforeInfinity() {
        DataSeriesNode node = new DataSeriesNode();
        node.setDefaultCount(0);

        @SuppressWarnings("unchecked")
        List<Double> fromDoubleCount = (List<Double>) node.compute(Map.of(
                "input_start", 0.0d,
                "input_step", 1.0d,
                "input_count", 1.9d
        )).get("output_series");
        assertTrue(fromDoubleCount.isEmpty());

        @SuppressWarnings("unchecked")
        List<Double> overflow = (List<Double>) node.compute(Map.of(
                "input_start", 1.0e308d,
                "input_step", 1.0e308d,
                "input_count", 10
        )).get("output_series");
        assertFalse(overflow.isEmpty());
        for (Double value : overflow) {
            assertTrue(Double.isFinite(value));
        }
    }

    @Test
    void repeatBindsListTypeVariableAndKeepsLength() {
        RepeatNode node = new RepeatNode();
        assertEquals("T", findPort(node, "input_data").getListTypeVariable());
        assertEquals("T", findPort(node, "output_result").getListTypeVariable());
        assertTrue(findPort(node, "input_data").isListElementBinding());

        Map<String, Object> outputs = node.compute(Map.of(
                "input_data", "A",
                "input_count", 3
        ));
        assertEquals(List.of("A", "A", "A"), outputs.get("output_result"));
        assertEquals(3, outputs.get("output_length"));
    }

    @Test
    void currentGraphFormatIsV28() {
        assertEquals(28, GraphFormatVersion.V28);
        assertEquals(29, GraphFormatVersion.V29);
        assertEquals(30, GraphFormatVersion.V30);
        assertEquals(33, GraphFormatVersion.V33);
        assertEquals(34, GraphFormatVersion.V34);
        assertEquals(35, GraphFormatVersion.V35);
        assertEquals(36, GraphFormatVersion.V36);
        assertEquals(GraphFormatVersion.V54, GraphFormatVersion.CURRENT);
    }

    @Test
    void v27ToV28DropsSeriesSumWiresAndPreservesSeriesOutput() {
        SavedGraph v27 = new SavedGraph();
        v27.formatVersion = GraphFormatVersion.V27;

        SavedNode series = savedNode("series", "math.sequence.series");
        SavedNode sink = savedNode("sink", "math.list.create_list");
        SavedNode add = savedNode("add", "math.scalar_math.addition");
        SavedNode floatA = savedNode("float_a", "input.numeric.float");

        v27.nodes = new ArrayList<>(List.of(series, sink, add, floatA));
        v27.connections = new ArrayList<>(List.of(
                wire("series", "output_sum", "add", "input_a"),
                wire("series", "output_series", "sink", "input_0"),
                wire("float_a", "output_value", "add", "input_b")
        ));
        v27.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v27);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);

        assertEquals(4, migrated.nodes.size());
        assertEquals(2, migrated.connections.size());
        assertTrue(hasWire(migrated, "series", "output_series", "sink", "input_0"));
        assertTrue(hasWire(migrated, "float_a", "output_value", "add", "input_b"));
        assertFalse(hasWire(migrated, "series", "output_sum", "add", "input_a"));
    }

    private static IPort findPort(Object node, String portId) {
        IPort port = findPortOrNull(node, portId);
        if (port == null) {
            throw new AssertionError("missing port " + portId);
        }
        return port;
    }

    private static IPort findPortOrNull(Object node, String portId) {
        if (node instanceof MathRangeNode range) {
            return findAmong(range.getInputPorts(), range.getOutputPorts(), portId);
        }
        if (node instanceof DataSeriesNode series) {
            return findAmong(series.getInputPorts(), series.getOutputPorts(), portId);
        }
        if (node instanceof RepeatNode repeat) {
            return findAmong(repeat.getInputPorts(), repeat.getOutputPorts(), portId);
        }
        return null;
    }

    private static IPort findAmong(Iterable<IPort> inputs, Iterable<IPort> outputs, String portId) {
        for (IPort port : inputs) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        for (IPort port : outputs) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
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
                        && sourcePort.equalsIgnoreCase(c.sourcePortId)
                        && targetNode.equals(c.targetNodeId)
                        && targetPort.equalsIgnoreCase(c.targetPortId));
    }
}
