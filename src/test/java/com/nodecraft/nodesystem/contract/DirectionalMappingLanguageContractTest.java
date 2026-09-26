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
import com.nodecraft.nodesystem.nodes.material.directional_mapping.SlabStairAutofillNode;
import com.nodecraft.nodesystem.nodes.material.directional_mapping.SlopeMapNode;
import com.nodecraft.nodesystem.nodes.material.directional_mapping.TopSideBottomMapNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockStateData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
 * Directional Mapping v1 language fence: three PURE blockId-only nodes, Valid gates, V36 migration.
 */
class DirectionalMappingLanguageContractTest {

    private static NodeRegistry registry;

    private static final Set<String> EXPECTED_IDS = Set.of(
            "material.directional_mapping.top_side_bottom_map",
            "material.directional_mapping.slope_map",
            "material.directional_mapping.slab_stair_autofill"
    );

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV36() {
        assertEquals(35, GraphFormatVersion.V35);
        assertEquals(36, GraphFormatVersion.V36);
        assertEquals(GraphFormatVersion.V50, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyThreeDirectionalMappingNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("material.directional_mapping."))
                .sorted()
                .toList();
        assertEquals(3, ids.size(), "Expected 3 material.directional_mapping nodes: " + ids);
        assertEquals(EXPECTED_IDS, Set.copyOf(ids));
    }

    @Test
    void allDirectionalMappingNodesArePure() {
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(TopSideBottomMapNode.class,
                "material.directional_mapping.top_side_bottom_map"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(SlopeMapNode.class,
                "material.directional_mapping.slope_map"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(SlabStairAutofillNode.class,
                "material.directional_mapping.slab_stair_autofill"));
    }

    @Test
    void directionalMappingNodesHaveNoDeconstructOutputs() {
        for (String typeId : EXPECTED_IDS) {
            INode node = registry.createNodeInstance(typeId);
            assertFalse(hasPort(node.getOutputPorts(), "output_positions"), typeId);
            assertFalse(hasPort(node.getOutputPorts(), "output_block_ids"), typeId);
            assertTrue(hasPort(node.getOutputPorts(), "output_placements"), typeId);
            assertTrue(hasPort(node.getOutputPorts(), "output_valid"), typeId);
            assertTrue(hasPort(node.getOutputPorts(), "output_error"), typeId);
        }
    }

    @Test
    void columnLayerMapPreservesStateAndPartialOverrides() {
        TopSideBottomMapNode node = new TopSideBottomMapNode();

        BlockStateData state = new BlockStateData();
        state.setProperty("facing", "north");

        BlockPlacementData top = new BlockPlacementData(new BlockPos(0, 2, 0), "minecraft:oak_planks", state);
        BlockPlacementData mid = new BlockPlacementData(new BlockPos(0, 1, 0), "minecraft:oak_planks", state);
        BlockPlacementData bottom = new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", state);

        node.setInput("input_placements", List.of(top, mid, bottom));
        node.setInput("input_top", "minecraft:grass_block");
        node.processNode(null);

        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals(3, out.size());

        BlockPlacementData remappedTop = findAt(out, 0, 2, 0);
        assertEquals("minecraft:grass_block", remappedTop.blockId());
        assertEquals("north", remappedTop.stateData().get("facing"));

        BlockPlacementData remappedMid = findAt(out, 0, 1, 0);
        assertEquals("minecraft:oak_planks", remappedMid.blockId());
        assertEquals("north", remappedMid.stateData().get("facing"));

        BlockPlacementData remappedBottom = findAt(out, 0, 0, 0);
        assertEquals("minecraft:oak_planks", remappedBottom.blockId());
    }

    @Test
    void columnLayerMapSingleCellUsesTop() {
        TopSideBottomMapNode node = new TopSideBottomMapNode();
        BlockPlacementData cell = new BlockPlacementData(new BlockPos(1, 5, 1), "minecraft:dirt", null);
        node.setInput("input_placements", List.of(cell));
        node.setInput("input_top", "minecraft:grass_block");
        node.setInput("input_bottom", "minecraft:stone");
        node.processNode(null);

        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals(1, out.size());
        assertEquals("minecraft:grass_block", out.getFirst().blockId());
    }

    @Test
    void surfaceSlopeMapRemapsOnlyColumnTop() {
        SlopeMapNode node = new SlopeMapNode();

        BlockStateData state = new BlockStateData();
        state.setProperty("axis", "y");

        BlockPlacementData surface = new BlockPlacementData(new BlockPos(0, 5, 0), "minecraft:oak_log", state);
        BlockPlacementData interior = new BlockPlacementData(new BlockPos(0, 4, 0), "minecraft:oak_log", state);
        BlockPlacementData neighbor = new BlockPlacementData(new BlockPos(-1, 7, 0), "minecraft:oak_log", null);

        node.setInput("input_placements", List.of(surface, interior, neighbor));
        node.setInput("input_flat", "minecraft:grass_block");
        node.setInput("input_slope", "minecraft:dirt");
        node.setInput("input_steep", "minecraft:stone");
        node.processNode(null);

        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));

        BlockPlacementData remappedSurface = findAt(out, 0, 5, 0);
        assertEquals("minecraft:stone", remappedSurface.blockId());
        assertEquals("y", remappedSurface.stateData().get("axis"));

        BlockPlacementData remappedInterior = findAt(out, 0, 4, 0);
        assertEquals("minecraft:oak_log", remappedInterior.blockId());
        assertEquals("y", remappedInterior.stateData().get("axis"));
    }

    @Test
    void slabStairAdaptIsBlockIdOnlyAndRequiresNormals() {
        SlabStairAutofillNode missingNormals = new SlabStairAutofillNode();
        BlockPlacementData placement = new BlockPlacementData(
                new BlockPos(0, 64, 0),
                "minecraft:oak_planks",
                null
        );
        missingNormals.setInput("input_placements", List.of(placement));
        missingNormals.processNode(null);
        assertFalse((Boolean) missingNormals.getOutput("output_valid"));
        assertTrue(((String) missingNormals.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("normal"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> empty = assertInstanceOf(List.class, missingNormals.getOutput("output_placements"));
        assertTrue(empty.isEmpty());

        SlabStairAutofillNode node = new SlabStairAutofillNode();
        BlockStateData state = new BlockStateData();
        state.setProperty("facing", "east");
        BlockPlacementData stairPlacement = new BlockPlacementData(
                new BlockPos(1, 64, 1),
                "minecraft:oak_planks",
                state
        );
        node.setInput("input_placements", List.of(stairPlacement));
        node.setInput("input_normals", List.of(new Vector3d(1.0d, 0.0d, 0.0d)));
        node.setInput("input_stair_block", "minecraft:oak_stairs");
        node.processNode(null);

        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals(1, out.size());
        BlockPlacementData adapted = out.getFirst();
        assertEquals("minecraft:oak_stairs", adapted.blockId());
        assertNotNull(adapted.stateData());
        assertEquals("east", adapted.stateData().get("facing"));
        assertNull(adapted.stateData().get("half"));
        assertNull(adapted.stateData().get("shape"));
        assertNull(adapted.stateData().get("type"));
    }

    @Test
    void slabStairAdaptRequiresNormalsForCoordinatesSource() {
        SlabStairAutofillNode node = new SlabStairAutofillNode();
        BlockPosList coordinates = new BlockPosList();
        coordinates.add(new BlockPos(0, 64, 0));
        coordinates.add(new BlockPos(1, 64, 0));

        node.setInput("input_coordinates", coordinates);
        node.setInput("input_default_block", "minecraft:stone");
        // No normals  -> must fail closed even when source is coordinates, not placements
        node.processNode(null);

        assertFalse((Boolean) node.getOutput("output_valid"));
        assertTrue(((String) node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("normal"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertTrue(out.isEmpty());
    }

    @Test
    void slabStairAdaptRejectsNormalsCountMismatch() {
        SlabStairAutofillNode node = new SlabStairAutofillNode();
        node.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone", null),
                new BlockPlacementData(new BlockPos(1, 0, 0), "minecraft:stone", null)
        ));
        node.setInput("input_normals", List.of(new Vector3d(0.0d, 1.0d, 0.0d)));
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        assertTrue(((String) node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("match"));
    }

    @Test
    void v35ToV36DropsDirectionalDeconstructWires() {
        SavedGraph v35 = new SavedGraph();
        v35.formatVersion = GraphFormatVersion.V35;

        SavedNode slope = savedNode("slope", "material.directional_mapping.slope_map");
        SavedNode column = savedNode("column", "material.directional_mapping.top_side_bottom_map");
        SavedNode slab = savedNode("slab", "material.directional_mapping.slab_stair_autofill");
        SavedNode preview = savedNode("preview", "output.preview.preview_blocks");

        v35.nodes = new ArrayList<>(List.of(slope, column, slab, preview));
        v35.connections = new ArrayList<>(List.of(
                wire("slope", "output_placements", "preview", "input_block_placements"),
                wire("slope", "output_positions", "preview", "input_block_placements"),
                wire("column", "output_block_ids", "preview", "input_block_placements"),
                wire("slab", "output_positions", "preview", "input_block_placements")
        ));
        v35.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v35);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);

        assertTrue(hasWire(migrated, "slope", "output_placements", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "slope", "output_positions", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "column", "output_block_ids", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "slab", "output_positions", "preview", "input_block_placements"));
    }

    private static BlockPlacementData findAt(List<BlockPlacementData> placements, int x, int y, int z) {
        return placements.stream()
                .filter(p -> p.pos() != null && p.pos().getX() == x && p.pos().getY() == y && p.pos().getZ() == z)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing placement at " + x + "," + y + "," + z));
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

    private static boolean hasPort(Iterable<IPort> ports, String id) {
        for (IPort port : ports) {
            if (id.equals(port.getId())) {
                return true;
            }
        }
        return false;
    }
}
