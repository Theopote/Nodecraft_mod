package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.nodes.input.context.CurrentTimeNode;
import com.nodecraft.nodesystem.nodes.input.context.DimensionInfoNode;
import com.nodecraft.nodesystem.nodes.input.context.PlayerPositionNode;
import com.nodecraft.nodesystem.nodes.input.context.PlayerRaycastNode;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Input Context v1 language fence: fail-closed WORLD_READ semantics, typed ports, V32 migration.
 */
class InputContextLanguageContractTest {

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
        assertEquals(GraphFormatVersion.V53, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyFourInputContextNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("input.context."))
                .sorted()
                .toList();
        assertEquals(4, ids.size(), "Expected 4 input.context nodes: " + ids);
        assertTrue(ids.contains("input.context.player_position"));
        assertTrue(ids.contains("input.context.player_raycast"));
        assertTrue(ids.contains("input.context.dimension_info"));
        assertTrue(ids.contains("input.context.current_time"));
    }

    @Test
    void allInputContextNodesAreWorldRead() {
        assertEquals(NodeEffect.WORLD_READ, NodeEffectResolver.resolve(PlayerPositionNode.class, "input.context.player_position"));
        assertEquals(NodeEffect.WORLD_READ, NodeEffectResolver.resolve(PlayerRaycastNode.class, "input.context.player_raycast"));
        assertEquals(NodeEffect.WORLD_READ, NodeEffectResolver.resolve(DimensionInfoNode.class, "input.context.dimension_info"));
        assertEquals(NodeEffect.WORLD_READ, NodeEffectResolver.resolve(CurrentTimeNode.class, "input.context.current_time"));
    }

    @Test
    void playerRaycastPortsAreTypedWithValidGate() {
        PlayerRaycastNode node = new PlayerRaycastNode();
        assertEquals(NodeDataType.POINT, findPort(node.getOutputPorts(), "output_hit_position").getDataType());
        assertEquals(NodeDataType.DOUBLE, findPort(node.getOutputPorts(), "output_hit_distance").getDataType());
        assertEquals(NodeDataType.BOOLEAN, findPort(node.getOutputPorts(), "output_valid").getDataType());
    }

    @Test
    void currentTimeTicksPortIsDouble() {
        CurrentTimeNode node = new CurrentTimeNode();
        assertEquals(NodeDataType.DOUBLE, findPort(node.getOutputPorts(), "output_time_ticks").getDataType());
    }

    @Test
    void allContextNodesExposeOutputValid() {
        assertTrue(hasPort(new PlayerRaycastNode().getOutputPorts(), "output_valid"));
        assertTrue(hasPort(new PlayerPositionNode().getOutputPorts(), "output_valid"));
        assertTrue(hasPort(new DimensionInfoNode().getOutputPorts(), "output_valid"));
        assertTrue(hasPort(new CurrentTimeNode().getOutputPorts(), "output_valid"));
    }

    @Test
    void processNodeWithNullContextIsFailClosed() {
        PlayerRaycastNode raycast = new PlayerRaycastNode();
        raycast.processNode(null);
        assertFalse((Boolean) raycast.getOutput("output_valid"));
        assertFalse((Boolean) raycast.getOutput("output_has_hit"));
        assertNull(raycast.getOutput("output_hit_position"));

        DimensionInfoNode dimension = new DimensionInfoNode();
        dimension.processNode(null);
        assertFalse((Boolean) dimension.getOutput("output_valid"));
        assertEquals("", dimension.getOutput("output_dimension_id"));
        assertFalse((Boolean) dimension.getOutput("output_is_overworld"));

        CurrentTimeNode time = new CurrentTimeNode();
        time.processNode(null);
        assertFalse((Boolean) time.getOutput("output_valid"));
        assertInstanceOf(Double.class, time.getOutput("output_time_ticks"));
        assertEquals(0.0d, (Double) time.getOutput("output_time_ticks"), 0.0d);
        assertFalse((Boolean) time.getOutput("output_is_day"));

        PlayerPositionNode position = new PlayerPositionNode();
        position.processNode(null);
        assertFalse((Boolean) position.getOutput("output_valid"));
    }

    @Test
    void playerPositionRestoreWithoutSnapshotIsInvalid() {
        PlayerPositionNode node = new PlayerPositionNode();
        node.setNodeState(Map.of(
                "hasCachedPosition", false,
                "cachedX", 10.0d,
                "cachedY", 64.0d,
                "cachedZ", 20.0d
        ));
        assertFalse((Boolean) node.getOutput("output_valid"));
    }

    @Test
    void playerPositionRestoreNonFiniteSnapshotIsInvalid() {
        PlayerPositionNode nanNode = new PlayerPositionNode();
        nanNode.setNodeState(Map.of(
                "hasCachedPosition", true,
                "cachedX", Double.NaN,
                "cachedY", 64.0d,
                "cachedZ", 20.0d
        ));
        assertFalse((Boolean) nanNode.getOutput("output_valid"));
        assertNull(nanNode.getOutput("output_position"));

        PlayerPositionNode infNode = new PlayerPositionNode();
        infNode.setNodeState(Map.of(
                "hasCachedPosition", true,
                "cachedX", 10.0d,
                "cachedY", 64.0d,
                "cachedZ", Double.POSITIVE_INFINITY
        ));
        assertFalse((Boolean) infNode.getOutput("output_valid"));
        assertNull(infNode.getOutput("output_position"));
    }

    @Test
    void v31ToV32RemapsRaycastTypeAndDropsIncompatibleHitWire() {
        SavedGraph v31 = new SavedGraph();
        v31.formatVersion = GraphFormatVersion.V31;

        SavedNode look = savedNode("look", "input.context.player_look_direction");
        SavedNode construct = savedNode("construct", "reference.vectors.construct_vector");
        SavedNode compare = savedNode("compare", "math.compare.greater_than");

        v31.nodes = new ArrayList<>(List.of(look, construct, compare));
        v31.connections = new ArrayList<>(List.of(
                wire("look", "output_hit_position", "construct", "input_x"),
                wire("look", "output_hit_distance", "compare", "input_a")
        ));
        v31.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v31);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);
        assertEquals("input.context.player_raycast",
                migrated.nodes.stream().filter(n -> "look".equals(n.nodeId)).findFirst().orElseThrow().typeId);
        assertFalse(hasWire(migrated, "look", "output_hit_position", "construct", "input_x"));
        assertTrue(hasWire(migrated, "look", "output_hit_distance", "compare", "input_a"));
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
