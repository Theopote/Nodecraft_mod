package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.math.compare.EqualsNode;
import com.nodecraft.nodesystem.nodes.math.compare.LessThanNode;
import com.nodecraft.nodesystem.nodes.math.compare.NotEqualsNode;
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
 * Compare v1 language fence: exact comparison, strict equality, no composite Compare node.
 */
class CompareLanguageContractTest {

    private static NodeRegistry registry;

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void exactlySixCompareNodesRegistered() {
        List<String> compareIds = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("math.compare."))
                .sorted()
                .collect(Collectors.toList());
        assertEquals(6, compareIds.size(), "Expected 6 compare nodes: " + compareIds);
    }

    @Test
    void compositeCompareNodeIsDeleted() {
        assertFalse(registry.getAllNodeIds().contains("math.compare.compare"));
    }

    @Test
    void equalityNodesUseAnyOrderingNodesUseDouble() {
        assertPortTypes(new EqualsNode(), NodeDataType.ANY, NodeDataType.ANY);
        assertPortTypes(new NotEqualsNode(), NodeDataType.ANY, NodeDataType.ANY);
        assertPortTypes(new LessThanNode(), NodeDataType.DOUBLE, NodeDataType.DOUBLE);
    }

    @Test
    void equalsUsesStrictGenericEquality() {
        EqualsNode node = new EqualsNode();
        assertTrue((Boolean) node.compute(Map.of("input_a", 1, "input_b", 1.0d)).get("output_result"));
        assertFalse((Boolean) node.compute(Map.of("input_a", "1", "input_b", 1)).get("output_result"));
        assertFalse((Boolean) node.compute(Map.of("input_a", "true", "input_b", true)).get("output_result"));
        assertTrue((Boolean) node.compute(Map.of("input_a", "abc", "input_b", "abc")).get("output_result"));
        assertTrue((Boolean) node.compute(Map.of("input_a", +0.0d, "input_b", -0.0d)).get("output_result"));
        assertFalse((Boolean) node.compute(Map.of("input_a", Double.NaN, "input_b", Double.NaN)).get("output_result"));
    }

    @Test
    void lessThanUsesExactNumericComparison() {
        LessThanNode node = new LessThanNode();
        assertTrue((Boolean) node.compute(Map.of("input_a", 0.0d, "input_b", 5.0e-11d)).get("output_result"));
        assertFalse((Boolean) node.compute(Map.of("input_a", 0.0d, "input_b", 0.0d)).get("output_result"));
        assertFalse((Boolean) node.compute(Map.of("input_a", Double.NaN, "input_b", 1.0d)).get("output_result"));
    }

    @Test
    void currentGraphFormatIsV28() {
        assertTrue(GraphFormatVersion.isCurrent(GraphFormatVersion.CURRENT));
    }


    private static void assertPortTypes(INode node, NodeDataType expectedA, NodeDataType expectedB) {
        IPort portA = node.getInputPorts().stream()
                .filter(p -> "input_a".equals(p.getId()))
                .findFirst()
                .orElseThrow();
        IPort portB = node.getInputPorts().stream()
                .filter(p -> "input_b".equals(p.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(expectedA, portA.getDataType(), node.getTypeId() + "#input_a");
        assertEquals(expectedB, portB.getDataType(), node.getTypeId() + "#input_b");
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
