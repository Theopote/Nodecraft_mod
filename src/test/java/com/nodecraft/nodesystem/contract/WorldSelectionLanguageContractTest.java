package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.world.selection.MultiRegionSelectionNode;
import com.nodecraft.nodesystem.nodes.world.selection.PointToBlockIfGridNode;
import com.nodecraft.nodesystem.nodes.world.selection.SelectedBlockNode;
import com.nodecraft.nodesystem.nodes.world.selection.SelectedBlockSequenceNode;
import com.nodecraft.nodesystem.nodes.world.selection.SelectedEntityNode;
import com.nodecraft.nodesystem.nodes.world.selection.SelectedRegionNode;
import com.nodecraft.nodesystem.nodes.world.selection.SnapPointListToBlocksNode;
import com.nodecraft.nodesystem.nodes.world.selection.SnapPointToBlockNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockSpace;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * World Selection v1 language fence (Graph V62).
 */
class WorldSelectionLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "world.selection.selected_block",
            "world.selection.selected_region",
            "world.selection.snap_point_to_block",
            "world.selection.snap_points_to_blocks",
            "world.selection.point_to_block_if_grid",
            "world.selection.selected_block_sequence",
            "world.selection.multi_region",
            "world.selection.selected_entity"
    );

    private static final Map<String, NodeEffect> EXPECTED_EFFECTS = Map.ofEntries(
            Map.entry("world.selection.selected_block", NodeEffect.CONTEXT_READ),
            Map.entry("world.selection.selected_region", NodeEffect.CONTEXT_READ),
            Map.entry("world.selection.snap_point_to_block", NodeEffect.PURE),
            Map.entry("world.selection.snap_points_to_blocks", NodeEffect.PURE),
            Map.entry("world.selection.point_to_block_if_grid", NodeEffect.PURE),
            Map.entry("world.selection.selected_block_sequence", NodeEffect.CONTEXT_READ),
            Map.entry("world.selection.multi_region", NodeEffect.PURE),
            Map.entry("world.selection.selected_entity", NodeEffect.CONTEXT_READ)
    );

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV62() {
        assertEquals(62, GraphFormatVersion.V62);
        assertEquals(GraphFormatVersion.V62, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyEightWorldSelectionNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("world.selection."))
                .sorted()
                .toList();
        assertEquals(8, ids.size(), ids.toString());
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
        assertFalse(ids.contains("world.selection.snap_vector_to_block"));
    }

    @Test
    void worldSelectionNodesHaveUniqueOrderZeroThroughSeven() {
        int[] orders = CANONICAL_IDS.stream()
                .mapToInt(typeId -> {
                    INode created = registry.createNodeInstance(typeId);
                    assertNotNull(created, typeId);
                    NodeInfo info = created.getClass().getAnnotation(NodeInfo.class);
                    assertNotNull(info, typeId);
                    return info.order();
                })
                .sorted()
                .toArray();
        assertEquals(8, orders.length);
        for (int i = 0; i < orders.length; i++) {
            assertEquals(i, orders[i], "Expected unique order " + i);
        }
    }

    @Test
    void worldSelectionNodesDeclareExpectedEffects() {
        for (Map.Entry<String, NodeEffect> entry : EXPECTED_EFFECTS.entrySet()) {
            INode node = registry.createNodeInstance(entry.getKey());
            assertNotNull(node);
            NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
            assertNotNull(info);
            assertEquals(entry.getValue(), info.effect(), entry.getKey());
        }
    }

    @Test
    void snapPointUsesCellCenterLattice() {
        SnapPointToBlockNode node = new SnapPointToBlockNode();
        node.setSnapModeString("NEAREST_CENTER");
        node.setInput("input_point", new PointData(0.5, 0.5, 0.5));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(new BlockPos(0, 0, 0), node.getOutput("output_coordinate"));
        assertEquals(0.0d, (Double) node.getOutput("output_distance"), 1e-9);

        node.setSnapModeString("CONTAINING_CELL");
        node.setInput("input_point", new PointData(0.9, 0.1, 0.2));
        node.processNode(null);
        assertEquals(BlockSpace.pointToBlockFloor(new org.joml.Vector3d(0.9, 0.1, 0.2)),
                node.getOutput("output_coordinate"));
    }

    @Test
    void pointIfGridMatchesBlockSpaceCellCenter() {
        PointToBlockIfGridNode node = new PointToBlockIfGridNode();
        node.setInput("input_point", new PointData(0.5, 0.5, 0.5));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, node.getOutput("output_is_grid_point"));
        assertEquals(new BlockPos(0, 0, 0), node.getOutput("output_coordinate"));

        node.setInput("input_point", new PointData(0, 0, 0));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(Boolean.FALSE, node.getOutput("output_is_grid_point"));
        assertNull(node.getOutput("output_coordinate"));
    }

    @Test
    void snapPointListFailsClosedOnMalformedMember() {
        SnapPointListToBlocksNode node = new SnapPointListToBlocksNode();
        node.setInput("input_points", List.of(new PointData(0.5, 0.5, 0.5), "bad"));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertFalse(hasPort(node, "output_skipped_count"));
        assertFalse(hasPort(node, "output_valid_count"));
    }

    @Test
    void multiRegionUsesTypedListsAndNoVectorBounds() {
        MultiRegionSelectionNode node = new MultiRegionSelectionNode();
        assertPortType(node, "input_regions", NodeDataType.REGION_LIST);
        assertPortType(node, "input_min_blocks", NodeDataType.BLOCK_LIST);
        assertPortType(node, "input_max_blocks", NodeDataType.BLOCK_LIST);
        assertFalse(hasPort(node, "output_min"));
        assertFalse(hasPort(node, "output_max"));
        assertTrue(hasPort(node, "output_error"));
    }

    @Test
    void selectedBlockIsSlimSelectionSource() {
        SelectedBlockNode node = new SelectedBlockNode();
        assertPortType(node, "output_position", NodeDataType.BLOCK_POS);
        assertTrue(hasPort(node, "output_has_selection"));
        assertTrue(hasPort(node, "output_valid"));
        assertTrue(hasPort(node, "output_error"));
        assertFalse(hasPort(node, "output_block_id"));
        assertFalse(hasPort(node, "output_center"));
        assertFalse(hasPort(node, "output_block_state"));
    }

    @Test
    void selectedEntityPortsAreTyped() {
        SelectedEntityNode node = new SelectedEntityNode();
        assertPortType(node, "output_exact_position", NodeDataType.POINT);
        assertPortType(node, "output_entity_type", NodeDataType.ENTITY_TYPE);
        assertPortType(node, "output_entity", NodeDataType.MINECRAFT_ENTITY);
        assertFalse(hasPort(node, "output_entity_id"));
        assertTrue(hasPort(node, "output_entity_uuid"));
    }

    @Test
    void selectedRegionAndSequenceAreSlim() {
        SelectedRegionNode region = new SelectedRegionNode();
        assertPortType(region, "output_region", NodeDataType.REGION);
        assertPortType(region, "output_min_block", NodeDataType.BLOCK_POS);
        assertPortType(region, "output_max_block", NodeDataType.BLOCK_POS);
        assertFalse(hasPort(region, "output_pos1"));
        assertFalse(hasPort(region, "output_volume"));

        SelectedBlockSequenceNode sequence = new SelectedBlockSequenceNode();
        assertPortType(sequence, "output_path", NodeDataType.PATH);
        assertPortType(sequence, "output_centers", NodeDataType.POINT_LIST);
        assertFalse(hasPort(sequence, "output_point_list"));
        assertFalse(hasPort(sequence, "output_line"));
        assertFalse(hasPort(sequence, "output_polyline"));
    }

    @Test
    void migrateV61ToV62DeletesSnapVectorAndDropsPorts() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V61;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();
        graph.nodePositions = new HashMap<>();

        SavedNode snapVector = savedNode("v1", "world.selection.snap_vector_to_block");
        SavedNode snapPoint = savedNode("p1", "world.selection.snap_point_to_block");
        Map<String, Object> snapState = new HashMap<>();
        snapState.put("snapMode", "FLOOR");
        snapPoint.state = snapState;

        SavedNode selected = savedNode("b1", "world.selection.selected_block");
        Map<String, Object> selectedState = new HashMap<>();
        selectedState.put("sourceMode", "AUTO");
        selectedState.put("pickedBlock", Map.of("x", 1, "y", 2, "z", 3));
        selected.state = selectedState;

        SavedNode multi = savedNode("m1", "world.selection.multi_region");
        SavedNode listSnap = savedNode("l1", "world.selection.snap_points_to_blocks");
        graph.nodes.addAll(List.of(snapVector, snapPoint, selected, multi, listSnap));

        graph.connections.add(wire("v1", "output_coordinate", "t1", "input_stub"));
        graph.connections.add(wire("b1", "output_block_id", "t2", "input_stub"));
        graph.connections.add(wire("b1", "output_position", "t3", "input_stub"));
        graph.connections.add(wire("m1", "output_min", "t4", "input_stub"));
        graph.connections.add(wire("l1", "output_skipped_count", "t5", "input_stub"));
        graph.connections.add(wire("t6", "output_stub", "m1", "input_min_points"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.V62, migrated.formatVersion);
        assertTrue(migrated.nodes.stream().noneMatch(n -> "v1".equals(n.nodeId)));
        assertEquals("CONTAINING_CELL",
                ((Map<?, ?>) nodeOf(migrated, "p1").state).get("snapMode"));
        assertFalse(((Map<?, ?>) nodeOf(migrated, "b1").state).containsKey("pickedBlock"));

        List<String> kept = migrated.connections.stream()
                .map(c -> c.sourceNodeId + ":" + c.sourcePortId + "->" + c.targetNodeId + ":" + c.targetPortId)
                .sorted()
                .toList();
        assertEquals(List.of(
                "b1:output_position->t3:input_stub",
                "t6:output_stub->m1:input_min_blocks"
        ), kept);
    }

    private static void assertPortType(INode node, String portId, NodeDataType expected) {
        IPort port = findPort(node, portId);
        assertNotNull(port, portId);
        assertEquals(expected, port.getDataType(), portId);
    }

    private static IPort findPort(INode node, String portId) {
        for (IPort port : node.getInputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        for (IPort port : node.getOutputPorts()) {
            if (portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }

    private static boolean hasPort(INode node, String portId) {
        return findPort(node, portId) != null;
    }

    private static SavedNode savedNode(String nodeId, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = nodeId;
        node.typeId = typeId;
        return node;
    }

    private static SavedConnection wire(String src, String srcPort, String dst, String dstPort) {
        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = src;
        connection.sourcePortId = srcPort;
        connection.targetNodeId = dst;
        connection.targetPortId = dstPort;
        return connection;
    }

    private static SavedNode nodeOf(SavedGraph graph, String nodeId) {
        return graph.nodes.stream()
                .filter(node -> nodeId.equals(node.nodeId))
                .findFirst()
                .orElseThrow();
    }
}
