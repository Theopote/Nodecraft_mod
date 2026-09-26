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
import com.nodecraft.nodesystem.nodes.material.pattern_mapping.BrickPatternMapNode;
import com.nodecraft.nodesystem.nodes.material.pattern_mapping.CheckerPatternMapNode;
import com.nodecraft.nodesystem.nodes.material.pattern_mapping.GridPatternMapNode;
import com.nodecraft.nodesystem.nodes.material.pattern_mapping.StripePatternMapNode;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pattern Mapping v1 language fence: four PURE blockId-only nodes, Valid gates, V38 migration.
 */
class PatternMappingLanguageContractTest {

    private static NodeRegistry registry;

    private static final Set<String> EXPECTED_IDS = Set.of(
            "material.pattern_mapping.checker_pattern_map",
            "material.pattern_mapping.stripe_pattern_map",
            "material.pattern_mapping.brick_pattern_map",
            "material.pattern_mapping.grid_pattern_map"
    );

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV38() {
        assertEquals(37, GraphFormatVersion.V37);
        assertEquals(38, GraphFormatVersion.V38);
        assertEquals(GraphFormatVersion.V53, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyFourPatternMappingNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("material.pattern_mapping."))
                .sorted()
                .toList();
        assertEquals(4, ids.size(), "Expected 4 material.pattern_mapping nodes: " + ids);
        assertEquals(EXPECTED_IDS, Set.copyOf(ids));
    }

    @Test
    void allPatternMappingNodesArePure() {
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(CheckerPatternMapNode.class,
                "material.pattern_mapping.checker_pattern_map"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(StripePatternMapNode.class,
                "material.pattern_mapping.stripe_pattern_map"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(BrickPatternMapNode.class,
                "material.pattern_mapping.brick_pattern_map"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(GridPatternMapNode.class,
                "material.pattern_mapping.grid_pattern_map"));
    }

    @Test
    void patternMappingNodesHaveNoDeconstructOutputsAndHaveValidErrorAndOrigin() {
        for (String typeId : EXPECTED_IDS) {
            INode node = registry.createNodeInstance(typeId);
            assertFalse(hasPort(node.getOutputPorts(), "output_positions"), typeId);
            assertFalse(hasPort(node.getOutputPorts(), "output_block_ids"), typeId);
            assertTrue(hasPort(node.getOutputPorts(), "output_placements"), typeId);
            assertTrue(hasPort(node.getOutputPorts(), "output_valid"), typeId);
            assertTrue(hasPort(node.getOutputPorts(), "output_error"), typeId);
            assertEquals(NodeDataType.BLOCK_POS, portType(node.getInputPorts(), "input_pattern_origin"), typeId);
        }
    }

    @Test
    void checkerPreservesSecondaryWhenOnlyPrimaryConnected() {
        CheckerPatternMapNode node = new CheckerPatternMapNode();
        // (0,0,0) even  -> primary; (1,0,0) odd  -> secondary (preserve)
        node.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(new BlockPos(1, 0, 0), "minecraft:oak_planks", null)
        ));
        node.setInput("input_primary", "minecraft:dirt");
        node.processNode(null);

        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals("minecraft:dirt", findAt(out, 0, 0, 0).blockId());
        assertEquals("minecraft:oak_planks", findAt(out, 1, 0, 0).blockId());
        assertFalse(findAt(out, 1, 0, 0).blockId().contains("stone"));
    }

    @Test
    void geometryWithoutMaterialsIsInvalid() {
        CheckerPatternMapNode node = new CheckerPatternMapNode();
        BlockPosList coords = new BlockPosList();
        coords.add(new BlockPos(0, 0, 0));
        node.setInput("input_coordinates", coords);
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertTrue(out.isEmpty());
    }

    @Test
    void stripeWidthZeroIsInvalid() {
        StripePatternMapNode node = new StripePatternMapNode();
        node.setStripeWidth(0);
        node.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:dirt", null)
        ));
        node.setInput("input_primary", "minecraft:stone");
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
    }

    @Test
    void brickLengthZeroIsInvalid() {
        BrickPatternMapNode node = new BrickPatternMapNode();
        node.setBrickLength(0);
        node.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:dirt", null)
        ));
        node.setInput("input_primary", "minecraft:bricks");
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
    }

    @Test
    void gridLineWidthExceedingSizeIsInvalid() {
        GridPatternMapNode node = new GridPatternMapNode();
        node.setGridSize(4);
        node.setLineWidth(5);
        node.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:dirt", null)
        ));
        node.setInput("input_frame", "minecraft:stone_bricks");
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
    }

    @Test
    void patternOriginShiftsPhaseWithWorldOffset() {
        CheckerPatternMapNode atOrigin = new CheckerPatternMapNode();
        atOrigin.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        atOrigin.setInput("input_primary", "minecraft:dirt");
        atOrigin.setInput("input_secondary", "minecraft:cobblestone");
        atOrigin.processNode(null);
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> originOut = assertInstanceOf(List.class, atOrigin.getOutput("output_placements"));
        String idAtOrigin = originOut.getFirst().blockId();

        CheckerPatternMapNode shifted = new CheckerPatternMapNode();
        shifted.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(10, 0, 0), "minecraft:oak_planks", null)
        ));
        shifted.setInput("input_primary", "minecraft:dirt");
        shifted.setInput("input_secondary", "minecraft:cobblestone");
        shifted.setInput("input_pattern_origin", new BlockPos(10, 0, 0));
        shifted.processNode(null);
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> shiftedOut = assertInstanceOf(List.class, shifted.getOutput("output_placements"));
        assertEquals(idAtOrigin, shiftedOut.getFirst().blockId());
    }

    @Test
    void patternOriginMissingDefaultsToWorldOriginWhileWrongTypesFailClosed() {
        List<BlockPlacementData> placements = List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        );

        CheckerPatternMapNode missing = new CheckerPatternMapNode();
        missing.compute(Map.of(
                "input_placements", placements,
                "input_primary", "minecraft:dirt",
                "input_secondary", "minecraft:cobblestone"
        ));
        assertTrue((Boolean) missing.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> missingOut = assertInstanceOf(List.class, missing.getOutput("output_placements"));
        assertEquals("minecraft:dirt", missingOut.getFirst().blockId());

        CheckerProbe pointOrigin = new CheckerProbe();
        pointOrigin.putInput("input_placements", placements);
        pointOrigin.putInput("input_primary", "minecraft:dirt");
        pointOrigin.putInput("input_secondary", "minecraft:cobblestone");
        pointOrigin.putInput("input_pattern_origin", new PointData(0, 0, 0));
        pointOrigin.processNode(null);
        assertFalse((Boolean) pointOrigin.getOutput("output_valid"));
        assertTrue(((String) pointOrigin.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("block_pos"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> pointOut = assertInstanceOf(List.class, pointOrigin.getOutput("output_placements"));
        assertTrue(pointOut.isEmpty());

        CheckerProbe vectorOrigin = new CheckerProbe();
        vectorOrigin.putInput("input_placements", placements);
        vectorOrigin.putInput("input_primary", "minecraft:dirt");
        vectorOrigin.putInput("input_secondary", "minecraft:cobblestone");
        vectorOrigin.putInput("input_pattern_origin", new Vector3d(0, 0, 0));
        vectorOrigin.processNode(null);
        assertFalse((Boolean) vectorOrigin.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> vectorOut = assertInstanceOf(List.class, vectorOrigin.getOutput("output_placements"));
        assertTrue(vectorOut.isEmpty());
    }

    @Test
    void checkerIs3dAndGridIgnoresY() {
        CheckerPatternMapNode checker = new CheckerPatternMapNode();
        checker.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(new BlockPos(0, 1, 0), "minecraft:oak_planks", null)
        ));
        checker.setInput("input_primary", "minecraft:dirt");
        checker.setInput("input_secondary", "minecraft:cobblestone");
        checker.processNode(null);
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> checkerOut = assertInstanceOf(List.class, checker.getOutput("output_placements"));
        assertEquals("minecraft:dirt", findAt(checkerOut, 0, 0, 0).blockId());
        assertEquals("minecraft:cobblestone", findAt(checkerOut, 0, 1, 0).blockId());

        GridPatternMapNode grid = new GridPatternMapNode();
        grid.setGridSize(4);
        grid.setLineWidth(1);
        grid.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(new BlockPos(0, 5, 0), "minecraft:oak_planks", null)
        ));
        grid.setInput("input_frame", "minecraft:stone_bricks");
        grid.setInput("input_fill", "minecraft:quartz_block");
        grid.processNode(null);
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> gridOut = assertInstanceOf(List.class, grid.getOutput("output_placements"));
        assertEquals(findAt(gridOut, 0, 0, 0).blockId(), findAt(gridOut, 0, 5, 0).blockId());
    }

    @Test
    void brickDirectionCanLockAxis() {
        BrickPatternMapNode auto = new BrickPatternMapNode();
        auto.setBrickDirection(BrickPatternMapNode.BrickDirection.Auto);
        auto.setBrickLength(2);
        auto.setCourseHeight(1);
        // North-south wall  -> Auto prefers Z
        List<BlockPlacementData> wall = new ArrayList<>();
        for (int z = 0; z < 6; z++) {
            wall.add(new BlockPlacementData(new BlockPos(0, 0, z), "minecraft:oak_planks", null));
        }
        auto.setInput("input_placements", wall);
        auto.setInput("input_primary", "minecraft:bricks");
        auto.setInput("input_secondary", "minecraft:stone_bricks");
        auto.processNode(null);
        assertTrue((Boolean) auto.getOutput("output_valid"));

        BrickPatternMapNode lockedX = new BrickPatternMapNode();
        lockedX.setBrickDirection(BrickPatternMapNode.BrickDirection.X);
        lockedX.setBrickLength(2);
        lockedX.setCourseHeight(1);
        lockedX.setInput("input_placements", wall);
        lockedX.setInput("input_primary", "minecraft:bricks");
        lockedX.setInput("input_secondary", "minecraft:stone_bricks");
        lockedX.processNode(null);
        assertTrue((Boolean) lockedX.getOutput("output_valid"));

        @SuppressWarnings("unchecked")
        List<BlockPlacementData> autoOut = assertInstanceOf(List.class, auto.getOutput("output_placements"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> xOut = assertInstanceOf(List.class, lockedX.getOutput("output_placements"));
        // Locked X on a Z-wall: all along-X indices identical  -> all primary
        assertEquals(findAt(xOut, 0, 0, 0).blockId(), findAt(xOut, 0, 0, 2).blockId());
        assertNotEqualsIds(findAt(autoOut, 0, 0, 0).blockId(), findAt(autoOut, 0, 0, 2).blockId());
    }

    @Test
    void v37ToV38DropsPatternDeconstructWires() {
        SavedGraph v37 = new SavedGraph();
        v37.formatVersion = GraphFormatVersion.V37;

        SavedNode checker = savedNode("checker", "material.pattern_mapping.checker_pattern_map");
        SavedNode stripe = savedNode("stripe", "material.pattern_mapping.stripe_pattern_map");
        SavedNode brick = savedNode("brick", "material.pattern_mapping.brick_pattern_map");
        SavedNode grid = savedNode("grid", "material.pattern_mapping.grid_pattern_map");
        SavedNode preview = savedNode("preview", "output.preview.preview_blocks");

        v37.nodes = new ArrayList<>(List.of(checker, stripe, brick, grid, preview));
        v37.connections = new ArrayList<>(List.of(
                wire("checker", "output_placements", "preview", "input_block_placements"),
                wire("checker", "output_positions", "preview", "input_block_placements"),
                wire("stripe", "output_block_ids", "preview", "input_block_placements"),
                wire("brick", "output_positions", "preview", "input_block_placements"),
                wire("grid", "output_block_ids", "preview", "input_block_placements")
        ));
        v37.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v37);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);

        assertTrue(hasWire(migrated, "checker", "output_placements", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "checker", "output_positions", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "stripe", "output_block_ids", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "brick", "output_positions", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "grid", "output_block_ids", "preview", "input_block_placements"));
    }

    /** Bypasses port type checks so wrong-type Pattern Origin can be exercised. */
    private static final class CheckerProbe extends CheckerPatternMapNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }

    private static void assertNotEqualsIds(String a, String b) {
        assertFalse(a.equals(b), "expected different block ids but both were " + a);
    }

    private static BlockPlacementData findAt(List<BlockPlacementData> placements, int x, int y, int z) {
        return placements.stream()
                .filter(p -> p.pos() != null && p.pos().getX() == x && p.pos().getY() == y && p.pos().getZ() == z)
                .findFirst()
                .orElseThrow();
    }

    private static boolean hasPort(List<? extends IPort> ports, String portId) {
        return ports.stream().anyMatch(p -> portId.equalsIgnoreCase(p.getId()));
    }

    private static NodeDataType portType(List<? extends IPort> ports, String portId) {
        return ports.stream()
                .filter(p -> portId.equalsIgnoreCase(p.getId()))
                .map(IPort::getDataType)
                .findFirst()
                .orElseThrow();
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
        if (graph.connections == null) {
            return false;
        }
        return graph.connections.stream().anyMatch(c ->
                c != null
                        && sourceNode.equals(c.sourceNodeId)
                        && sourcePort.equalsIgnoreCase(c.sourcePortId)
                        && targetNode.equals(c.targetNodeId)
                        && targetPort.equalsIgnoreCase(c.targetPortId));
    }
}
