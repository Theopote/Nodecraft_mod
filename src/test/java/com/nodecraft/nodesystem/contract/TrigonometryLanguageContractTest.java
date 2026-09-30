package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.math.scalar_math.ExpressionNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.Atan2Node;
import com.nodecraft.nodesystem.nodes.math.trigonometry.SineNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.SinhNode;
import com.nodecraft.nodesystem.nodes.math.trigonometry.TangentNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trigonometry v1 language fence: degrees-only, finite-result contract, shared TrigMathOps.
 */
class TrigonometryLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void exactlyTenTrigonometryNodesRegistered() {
        List<String> trigIds = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("math.trigonometry."))
                .sorted()
                .collect(Collectors.toList());
        assertEquals(10, trigIds.size(), "Expected 10 trig nodes: " + trigIds);
    }

    @Test
    void piAndEAreUnderInputNumeric() {
        assertTrue(registry.getAllNodeIds().contains("input.numeric.pi"));
        assertTrue(registry.getAllNodeIds().contains("input.numeric.e"));
        assertFalse(registry.getAllNodeIds().contains("math.trigonometry.pi"));
        assertFalse(registry.getAllNodeIds().contains("math.trigonometry.e"));
    }

    @Test
    void deletedDegRadConvertersAreGone() {
        assertFalse(registry.getAllNodeIds().contains("math.trigonometry.deg_to_rad"));
        assertFalse(registry.getAllNodeIds().contains("math.trigonometry.rad_to_deg"));
    }

    @Test
    void trigNodesNeverEmitInfinityWhenValid() {
        for (String nodeId : registry.getAllNodeIds()) {
            if (!nodeId.toLowerCase(Locale.ROOT).startsWith("math.trigonometry.")) {
                continue;
            }
            INode created = registry.createNodeInstance(nodeId);
            if (!(created instanceof BaseNode instance)) {
                continue;
            }
            instance.processNode(null);
            Object validObj = instance.getOutput("output_valid");
            if (!(validObj instanceof Boolean valid) || !valid) {
                continue;
            }
            for (IPort port : instance.getOutputPorts()) {
                if (port.getDataType() != NodeDataType.DOUBLE) {
                    continue;
                }
                Object value = instance.getOutput(port.getId());
                if (value instanceof Double d) {
                    assertTrue(Double.isFinite(d),
                            nodeId + "#" + port.getId() + " must be finite when Valid=true");
                    assertFalse(Double.isInfinite(d),
                            nodeId + "#" + port.getId() + " must not be Infinity when Valid=true");
                }
            }
        }
    }

    @Test
    void sineUsesDegrees() {
        SineNode node = new SineNode();
        Map<String, Object> outputs = node.compute(Map.of("input_angle", 90.0d));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(1.0d, (Double) outputs.get("output_sine"), 1.0e-12);
    }

    @Test
    void atan2RejectsNonFiniteInputs() {
        Atan2Node node = new Atan2Node();
        Map<String, Object> outputs = node.compute(Map.of(
                "input_y", Double.NaN,
                "input_x", 1.0d
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_angle")));
    }

    @Test
    void sinhOverflowIsInvalid() {
        SinhNode node = new SinhNode();
        Map<String, Object> outputs = node.compute(Map.of("input_value", 1000.0d));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isNaN((Double) outputs.get("output_result")));
    }

    @Test
    void tanNearSingularityRemainsValid() {
        TangentNode node = new TangentNode();
        Map<String, Object> outputs = node.compute(Map.of("input_angle", 89.999999d));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertTrue(Double.isFinite((Double) outputs.get("output_tangent")));
    }

    @Test
    void expressionTrigUsesDegrees() {
        ExpressionNode node = new ExpressionNode();
        node.setExpression("sin(90)");
        Map<String, Object> outputs = node.compute(Map.of());
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(1.0d, (Double) outputs.get("output_result"), 1.0e-12);

        node.setExpression("asin(1)");
        outputs = node.compute(Map.of());
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(90.0d, (Double) outputs.get("output_result"), 1.0e-12);
    }

    @Test
    void expressionRejectsRemovedDegRadFunctions() {
        ExpressionNode node = new ExpressionNode();
        node.setExpression("deg(90)");
        Map<String, Object> outputs = node.compute(Map.of());
        assertFalse((Boolean) outputs.get("output_valid"));

        node.setExpression("rad(1)");
        outputs = node.compute(Map.of());
        assertFalse((Boolean) outputs.get("output_valid"));
    }

    @Test
    void currentGraphFormatIsV28() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
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
