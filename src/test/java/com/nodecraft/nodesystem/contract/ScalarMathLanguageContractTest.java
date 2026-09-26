package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.NumericRangeData;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.DivisionNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.ExpressionNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.FracNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.GraphMapperNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.LerpNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.MultiplicationNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.RemapNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.RoundNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.SubtractionNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scalar Math v1 language fence: finite-result contract, exact zero, Round=rint, advanced ports.
 */
class ScalarMathLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void continuousScalarNodesNeverEmitInfinityOnValidPorts() {
        for (String nodeId : registry.getAllNodeIds()) {
            if (!nodeId.toLowerCase(Locale.ROOT).startsWith("math.scalar_math.")) {
                continue;
            }
            if (nodeId.endsWith(".int_divide")) {
                continue;
            }
            INode created = registry.createNodeInstance(nodeId);
            if (!(created instanceof BaseNode instance)) {
                continue;
            }
            // Smoke: after empty process, Valid=false  -> numeric outs are NaN (not Inf).
            instance.processNode(null);
            Object validObj = instance.getOutput("output_valid");
            if (!(validObj instanceof Boolean valid) || valid) {
                continue;
            }
            for (IPort port : instance.getOutputPorts()) {
                if (port.getDataType() != NodeDataType.DOUBLE) {
                    continue;
                }
                Object value = instance.getOutput(port.getId());
                if (value instanceof Double d) {
                    assertFalse(Double.isInfinite(d),
                        nodeId + "#" + port.getId() + " must not be Infinity when Valid=false");
                }
            }
        }
    }

    @Test
    void subtractionOverflowIsNanNotInfinity() {
        SubtractionNode node = new SubtractionNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", Double.MAX_VALUE,
            "input_b", -Double.MAX_VALUE
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_difference")));
    }

    @Test
    void multiplicationOverflowIsNanNotInfinity() {
        MultiplicationNode node = new MultiplicationNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", 1.0e308d,
            "input_b", 1.0e308d
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_product")));
    }

    @Test
    void divisionAcceptsTinyNonZeroDivisor() {
        DivisionNode node = new DivisionNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", 1.0d,
            "input_b", 1.0e-11d
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(1.0e11d, (Double) outputs.get("output_quotient"), 1.0e-3);
    }

    @Test
    void divisionRejectsExactZeroOnly() {
        DivisionNode node = new DivisionNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", 1.0d,
            "input_b", 0.0d
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_quotient")));
    }

    @Test
    void expressionDivisionMatchesDivisionNodeTinyDivisor() {
        ExpressionNode node = new ExpressionNode();
        node.setExpression("A / B");
        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", 1.0d,
            "input_b", 1.0e-11d
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(1.0e11d, (Double) outputs.get("output_result"), 1.0e-3);
    }

    @Test
    void lerpOverflowIsInvalid() {
        LerpNode node = new LerpNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_a", 1.0e308d,
            "input_b", -1.0e308d,
            "input_t", 1.0d
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_result")));
    }

    @Test
    void remapUnclampedOverflowIsInvalid() {
        RemapNode node = new RemapNode();
        Map<String, Object> outputs = node.compute(Map.of(
            "input_value", 1.0e20d,
            "input_source", new NumericRangeData(0.0d, 1.0d),
            "input_target", new NumericRangeData(0.0d, 1.0e308d),
            "input_clamp", false
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_result")));
    }

    @Test
    void roundPreservesHugeFiniteDoubles() {
        RoundNode node = new RoundNode();
        Map<String, Object> outputs = node.compute(Map.of("input_value", 1.0e100d));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(1.0e100d, (Double) outputs.get("output_rounded"), 0.0d);
    }

    @Test
    void roundUsesTiesToEven() {
        RoundNode node = new RoundNode();
        Map<String, Object> even = node.compute(Map.of("input_value", 2.5d));
        Map<String, Object> odd = node.compute(Map.of("input_value", 1.5d));
        assertEquals(2.0d, (Double) even.get("output_rounded"), 0.0d);
        assertEquals(2.0d, (Double) odd.get("output_rounded"), 0.0d);
    }

    @Test
    void fractionHasNoFloorOutputPort() {
        FracNode node = new FracNode();
        assertFalse(node.getOutputPorts().stream().anyMatch(p -> "output_floor".equals(p.getId())));
        assertTrue(node.getOutputPorts().stream().anyMatch(p -> "output_result".equals(p.getId())));
    }

    @Test
    void graphMapperHasNoCurveParameterInputPorts() {
        GraphMapperNode node = new GraphMapperNode();
        assertFalse(node.getInputPorts().stream().anyMatch(p -> p.getId().contains("exponent")));
        assertFalse(node.getInputPorts().stream().anyMatch(p -> p.getId().contains("gaussian")));
        assertEquals(3, node.getInputPorts().size());
    }

    @Test
    void graphMapperResultOverflowIsInvalid() {
        GraphMapperNode node = new GraphMapperNode();
        node.setCurveType(GraphMapperNode.CurveType.LINEAR);
        node.setClampInput(false);
        Map<String, Object> outputs = node.compute(Map.of(
            "input_value", 2.0d,
            "input_source", new NumericRangeData(0.0d, 1.0d),
            "input_target", new NumericRangeData(0.0d, 1.0e308d)
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_result")));
        assertTrue(Double.isNaN((Double) outputs.get("output_t")));
        assertTrue(Double.isNaN((Double) outputs.get("output_mapped")));
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
        assertEquals(GraphFormatVersion.V45, GraphFormatVersion.CURRENT);
    }

    @Test
    void v24ToV25DropsDeletedScalarMathPortsAndPreservesUnrelatedWires() {
        SavedGraph v24 = new SavedGraph();
        v24.formatVersion = GraphFormatVersion.V24;

        SavedNode numberA = savedNode("num_a", "input.values.float");
        SavedNode numberB = savedNode("num_b", "input.values.float");
        SavedNode numberC = savedNode("num_c", "input.values.float");
        SavedNode numberD = savedNode("num_d", "input.values.float");
        SavedNode mapper = savedNode("mapper", "math.scalar_math.graph_mapper");
        SavedNode frac = savedNode("frac", "math.scalar_math.frac");
        SavedNode sink = savedNode("sink", "math.list.create_list");
        SavedNode add = savedNode("add", "math.scalar_math.addition");

        v24.nodes = new ArrayList<>(List.of(numberA, numberB, numberC, numberD, mapper, frac, sink, add));
        v24.connections = new ArrayList<>(List.of(
                wire("num_a", "output_value", "mapper", "input_exponent"),
                wire("num_b", "output_value", "mapper", "input_gaussian_center"),
                wire("num_c", "output_value", "mapper", "input_gaussian_width"),
                wire("frac", "output_floor", "sink", "input_0"),
                wire("num_d", "output_value", "mapper", "input_value"),
                wire("num_a", "output_value", "add", "input_a"),
                wire("num_b", "output_value", "add", "input_b")
        ));
        v24.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v24);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);

        assertEquals(8, migrated.nodes.size());
        assertEquals(3, migrated.connections.size());
        assertTrue(hasWire(migrated, "num_d", "output_value", "mapper", "input_value"));
        assertTrue(hasWire(migrated, "num_a", "output_value", "add", "input_a"));
        assertTrue(hasWire(migrated, "num_b", "output_value", "add", "input_b"));
        assertFalse(hasWire(migrated, "num_a", "output_value", "mapper", "input_exponent"));
        assertFalse(hasWire(migrated, "num_b", "output_value", "mapper", "input_gaussian_center"));
        assertFalse(hasWire(migrated, "num_c", "output_value", "mapper", "input_gaussian_width"));
        assertFalse(hasWire(migrated, "frac", "output_floor", "sink", "input_0"));
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
