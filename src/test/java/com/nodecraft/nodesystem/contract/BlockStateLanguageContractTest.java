package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.material.block_state.ApplyBlockStateNode;
import com.nodecraft.nodesystem.nodes.material.block_state.BuildBlockStateNode;
import com.nodecraft.nodesystem.nodes.material.block_state.OrientBlockStateNode;
import com.nodecraft.nodesystem.nodes.material.block_state.StairShapeNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockStateData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Block State v1 language fence: four PURE state-only nodes, merge semantics, Valid gates, V35 migration.
 */
class BlockStateLanguageContractTest {

    private static NodeRegistry registry;

    private static final Set<String> EXPECTED_BLOCK_STATE_IDS = Set.of(
            "material.block_state.build_block_state",
            "material.block_state.orient_block_state",
            "material.block_state.apply_block_state",
            "material.block_state.stair_shape"
    );

    private static final Set<String> REMOVED_BLOCK_STATE_IDS = Set.of(
            "material.block_state.auto_orient_blocks",
            "material.block_state.waterlogged",
            "material.block_state.facing_from_normal",
            "material.block_state.block_state_assign",
            "material.block_state.slab_autofill"
    );

    private static final Set<String> GEOMETRY_PORT_IDS = Set.of(
            "input_coordinates",
            "input_geometry",
            "input_box_geometry",
            "input_cylinder_geometry",
            "input_sphere_geometry",
            "input_torus_geometry"
    );

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV35() {
        assertEquals(34, GraphFormatVersion.V34);
        assertEquals(35, GraphFormatVersion.V35);
        assertEquals(36, GraphFormatVersion.V36);
        assertEquals(GraphFormatVersion.V45, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyFourBlockStateNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("material.block_state."))
                .sorted()
                .toList();
        assertEquals(4, ids.size(), "Expected 4 material.block_state nodes: " + ids);
        assertEquals(EXPECTED_BLOCK_STATE_IDS, Set.copyOf(ids));
    }

    @Test
    void removedBlockStateNodesAbsent() {
        for (String removed : REMOVED_BLOCK_STATE_IDS) {
            assertFalse(registry.getAllNodeIds().stream()
                    .anyMatch(id -> id.equalsIgnoreCase(removed)), "Removed node still registered: " + removed);
        }
        assertTrue(registry.getAllNodeIds().stream()
                .anyMatch(id -> id.equalsIgnoreCase("material.directional_mapping.slab_stair_autofill")));
    }

    @Test
    void allBlockStateNodesArePure() {
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(BuildBlockStateNode.class, "material.block_state.build_block_state"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(OrientBlockStateNode.class, "material.block_state.orient_block_state"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(ApplyBlockStateNode.class, "material.block_state.apply_block_state"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(StairShapeNode.class, "material.block_state.stair_shape"));
    }

    @Test
    void blockStateNodesHaveNoGeometryInputs() {
        for (String typeId : EXPECTED_BLOCK_STATE_IDS) {
            INode node = registry.createNodeInstance(typeId);
            for (IPort port : node.getInputPorts()) {
                assertFalse(GEOMETRY_PORT_IDS.contains(port.getId()),
                        typeId + " must not expose geometry port " + port.getId());
                assertFalse(port.getDataType() == NodeDataType.GEOMETRY,
                        typeId + " must not expose GEOMETRY input " + port.getId());
            }
        }
    }

    @Test
    void buildBlockStateHasNoBlockInfoOutput() {
        BuildBlockStateNode node = new BuildBlockStateNode();
        for (IPort port : node.getOutputPorts()) {
            assertFalse("output_block_info".equals(port.getId()));
            assertFalse(port.getDataType() == NodeDataType.BLOCK_INFO);
        }
    }

    @Test
    void buildBlockStateRequiresBlockTypeForValidation() {
        BuildBlockStateNode node = new BuildBlockStateNode();
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertEquals("Block type required for validation", node.getOutput("output_error"));
    }

    @Test
    void buildBlockStateRejectsUnknownBlock() {
        BuildBlockStateNode node = new BuildBlockStateNode();
        node.setInput("input_block_type", "minecraft:definitely_not_a_real_block_id_v35");
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertTrue(((String) node.getOutput("output_error")).contains("Unknown block"));
    }

    @Test
    void buildBlockStateRejectsMalformedPropertiesText() {
        BuildBlockStateNode node = new BuildBlockStateNode();
        node.setPropertiesText("facing=north,badpair,half=top");
        node.setInput("input_block_type", "minecraft:stone");
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertTrue(((String) node.getOutput("output_error")).contains("Malformed property entry"));
    }

    @Test
    void buildBlockStateRejectsEmptyPropertyKeyOrValue() {
        BuildBlockStateNode emptyValue = new BuildBlockStateNode();
        emptyValue.setPropertiesText("facing=");
        emptyValue.setInput("input_block_type", "minecraft:stone");
        emptyValue.processNode(null);
        assertFalse((Boolean) emptyValue.getOutput("output_valid"));
        assertTrue(((String) emptyValue.getOutput("output_error")).contains("Malformed property entry"));

        BuildBlockStateNode emptyKey = new BuildBlockStateNode();
        emptyKey.setPropertiesText("=north");
        emptyKey.setInput("input_block_type", "minecraft:stone");
        emptyKey.processNode(null);
        assertFalse((Boolean) emptyKey.getOutput("output_valid"));
        assertTrue(((String) emptyKey.getOutput("output_error")).contains("Malformed property entry"));
    }

    @Test
    void buildBlockStateOutputHasNoBlockIdKey() {
        BuildBlockStateNode node = new BuildBlockStateNode();
        node.setInput("input_block_type", "minecraft:oak_log");
        node.setInput("input_axis", "y");
        node.processNode(null);

        BlockStateData state = assertInstanceOf(BlockStateData.class, node.getOutput("output_block_state"));
        assertNull(state.get("blockId"));
        assertNull(state.get("id"));
    }

    @Test
    void orientBlockStateRejectsPointOnVectorPort() {
        OrientBlockStateNode node = new OrientBlockStateNode();
        node.setInput("input_vector", new BlockPos(1, 2, 3));
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertTrue(((String) node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("vector"));
    }

    @Test
    void orientBlockStateRejectsNonFiniteVector() {
        OrientBlockStateNode nan = new OrientBlockStateNode();
        nan.setInput("input_vector", new Vector3d(Double.NaN, 0.0d, 0.0d));
        nan.processNode(null);
        assertFalse((Boolean) nan.getOutput("output_valid"));
        assertTrue(((String) nan.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("vector"));

        OrientBlockStateNode infinity = new OrientBlockStateNode();
        infinity.setInput("input_vector", new Vector3d(Double.POSITIVE_INFINITY, 0.0d, 0.0d));
        infinity.processNode(null);
        assertFalse((Boolean) infinity.getOutput("output_valid"));
        assertTrue(((String) infinity.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("vector"));
    }

    @Test
    void orientBlockStateRejectsUnknownMode() {
        OrientBlockStateNode node = new OrientBlockStateNode();
        node.setInput("input_vector", new Vector3d(0.0d, 0.0d, -1.0d));
        node.setInput("input_mode", "banana");
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertEquals("Unknown orientation mode", node.getOutput("output_error"));
    }

    @Test
    void applyBlockStateMergesOverridesWithoutReplacingBase() {
        ApplyBlockStateNode node = new ApplyBlockStateNode();

        BlockStateData baseState = new BlockStateData();
        baseState.setProperty("facing", "north");
        baseState.setProperty("half", "bottom");

        BlockStateData override = new BlockStateData();
        override.setProperty("facing", "east");

        BlockPlacementData placement = new BlockPlacementData(
                new BlockPos(0, 64, 0),
                "minecraft:oak_stairs",
                baseState
        );

        node.setInput("input_placements", List.of(placement));
        node.setInput("input_block_state", override);
        node.processNode(null);

        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals(1, out.size());
        BlockPlacementData merged = out.getFirst();
        assertEquals("minecraft:oak_stairs", merged.blockId());
        assertNotNull(merged.stateData());
        assertEquals("east", merged.stateData().get("facing"));
        assertEquals("bottom", merged.stateData().get("half"));
    }

    @Test
    void applyBlockStateHasPlacementsOnlyIo() {
        ApplyBlockStateNode node = new ApplyBlockStateNode();
        assertEquals(NodeDataType.BLOCK_PLACEMENT_LIST, findPort(node.getInputPorts(), "input_placements").getDataType());
        assertEquals(NodeDataType.BLOCK_STATE_DATA, findPort(node.getInputPorts(), "input_block_state").getDataType());
        assertEquals(NodeDataType.BLOCK_PLACEMENT_LIST, findPort(node.getOutputPorts(), "output_placements").getDataType());
        assertFalse(hasPort(node.getInputPorts(), "input_geometry"));
        assertFalse(hasPort(node.getOutputPorts(), "output_positions"));
        assertFalse(hasPort(node.getOutputPorts(), "output_block_ids"));
    }

    @Test
    void stairShapePreservesBlockId() {
        StairShapeNode node = new StairShapeNode();
        BlockStateData state = new BlockStateData();
        state.setProperty("facing", "north");
        state.setProperty("half", "bottom");

        BlockPlacementData placement = new BlockPlacementData(
                new BlockPos(5, 64, 5),
                "minecraft:oak_stairs",
                state
        );

        node.setInput("input_placements", List.of(placement));
        node.processNode(null);

        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals(1, out.size());
        assertEquals("minecraft:oak_stairs", out.getFirst().blockId());
    }

    @Test
    void stairShapeLeavesNonStairPlacementsUnchanged() {
        StairShapeNode node = new StairShapeNode();

        BlockPlacementData stone = new BlockPlacementData(
                new BlockPos(1, 64, 1),
                "minecraft:stone",
                null
        );

        node.setInput("input_placements", List.of(stone));
        node.processNode(null);

        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals(1, out.size());
        BlockPlacementData unchanged = out.getFirst();
        assertEquals("minecraft:stone", unchanged.blockId());
        assertTrue(unchanged.stateData() == null || unchanged.stateData().isEmpty());
    }

    @Test
    void stairShapeHasPlacementsOnlyIo() {
        StairShapeNode node = new StairShapeNode();
        assertTrue(hasPort(node.getInputPorts(), "input_placements"));
        assertFalse(hasPort(node.getInputPorts(), "input_geometry"));
        assertFalse(hasPort(node.getInputPorts(), "input_coordinates"));
        assertFalse(hasPort(node.getOutputPorts(), "output_positions"));
    }

    @Test
    void v34ToV35RemapsTypesDropsWiresAndStripsBlockIdFromState() {
        SavedGraph v34 = new SavedGraph();
        v34.formatVersion = GraphFormatVersion.V34;

        SavedNode assign = savedNode("assign", "material.block_state.block_state_assign");
        SavedNode slab = savedNode("slab", "material.block_state.slab_autofill");
        SavedNode autoOrient = savedNode("auto", "material.block_state.auto_orient_blocks");
        SavedNode build = savedNode("build", "material.block_state.build_block_state");
        SavedNode stair = savedNode("stair", "material.block_state.stair_shape");
        SavedNode box = savedNode("box", "geometry.primitives.box");
        SavedNode list = savedNode("list", "math.list.create_list");
        SavedNode preview = savedNode("preview", "output.preview.preview_blocks");

        Map<String, Object> buildState = new HashMap<>();
        buildState.put("propertiesText", "facing=north");
        buildState.put("blockId", "minecraft:stone");
        build.state = buildState;

        v34.nodes = new ArrayList<>(List.of(assign, slab, autoOrient, build, stair, box, list, preview));
        v34.connections = new ArrayList<>(List.of(
                wire("build", "output_block_info", "preview", "input_block_placements"),
                wire("build", "output_block_state", "assign", "input_block_state"),
                wire("box", "output_geometry", "assign", "input_geometry"),
                wire("assign", "output_positions", "preview", "input_block_placements"),
                wire("assign", "output_placements", "stair", "input_placements"),
                wire("stair", "output_placements", "preview", "input_block_placements"),
                wire("box", "output_geometry", "stair", "input_geometry"),
                wire("list", "output_list", "slab", "input_normals")
        ));
        v34.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v34);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);

        assertFalse(migrated.nodes.stream().anyMatch(n -> "auto".equals(n.nodeId)));
        assertEquals("material.block_state.apply_block_state", findNode(migrated, "assign").typeId);
        assertEquals("material.directional_mapping.slab_stair_autofill", findNode(migrated, "slab").typeId);

        assertFalse(hasWire(migrated, "build", "output_block_info", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "assign", "output_positions", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "box", "output_geometry", "stair", "input_geometry"));
        assertFalse(hasWire(migrated, "box", "output_geometry", "assign", "input_geometry"));
        assertFalse(hasWire(migrated, "list", "output_list", "slab", "input_normals"));
        assertTrue(hasWire(migrated, "assign", "output_placements", "stair", "input_placements"));

        @SuppressWarnings("unchecked")
        Map<String, Object> migratedBuildState = (Map<String, Object>) findNode(migrated, "build").state;
        assertNotNull(migratedBuildState);
        assertFalse(migratedBuildState.containsKey("blockId"));
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
