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
import com.nodecraft.nodesystem.nodes.material.basic_assignment.AssignBlockTypeNode;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.BasicAssignmentUtils;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.BlockPaletteNode;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.CreateBlockPaletteNode;
import com.nodecraft.nodesystem.nodes.material.basic_assignment.WeightedBlockPaletteNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.BlockStateData;
import net.minecraft.util.math.BlockPos;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basic Assignment v1 language fence: four PURE material entry nodes, Valid gates, V40 migration.
 */
class BasicAssignmentLanguageContractTest {

    private static NodeRegistry registry;

    private static final Set<String> EXPECTED_IDS = Set.of(
            "material.basic_assignment.assign_block_type",
            "material.basic_assignment.create_block_palette",
            "material.basic_assignment.block_palette",
            "material.basic_assignment.weighted_palette"
    );

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV41() {
        assertEquals(40, GraphFormatVersion.V40);
        assertEquals(41, GraphFormatVersion.V41);
        assertEquals(GraphFormatVersion.V57, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyFourBasicAssignmentNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("material.basic_assignment."))
                .sorted()
                .toList();
        assertEquals(4, ids.size(), "Expected 4 material.basic_assignment nodes: " + ids);
        assertEquals(EXPECTED_IDS, Set.copyOf(ids));
    }

    @Test
    void allBasicAssignmentNodesArePure() {
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(AssignBlockTypeNode.class,
                "material.basic_assignment.assign_block_type"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(CreateBlockPaletteNode.class,
                "material.basic_assignment.create_block_palette"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(BlockPaletteNode.class,
                "material.basic_assignment.block_palette"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(WeightedBlockPaletteNode.class,
                "material.basic_assignment.weighted_palette"));
    }

    @Test
    void assignBlockTypeRequiresBlockTypeAndHasNoDeconstructOutputs() {
        INode node = registry.createNodeInstance("material.basic_assignment.assign_block_type");
        assertFalse(hasPort(node.getOutputPorts(), "output_positions"));
        assertFalse(hasPort(node.getOutputPorts(), "output_block_ids"));
        assertFalse(hasPort(node.getOutputPorts(), "output_positions_tree"));
        assertFalse(hasPort(node.getOutputPorts(), "output_block_ids_tree"));
        assertTrue(hasPort(node.getOutputPorts(), "output_placements"));
        assertTrue(hasPort(node.getOutputPorts(), "output_valid"));
        assertTrue(hasPort(node.getOutputPorts(), "output_error"));

        AssignBlockTypeNode assign = new AssignBlockTypeNode();
        assign.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        assign.processNode(null);
        assertFalse((Boolean) assign.getOutput("output_valid"));

        assign.setInput("input_block_type", "minecraft:stone");
        assign.processNode(null);
        assertTrue((Boolean) assign.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, assign.getOutput("output_placements"));
        assertEquals("minecraft:stone", out.getFirst().blockId());
    }

    @Test
    void assignPreservesStateData() {
        BlockStateData state = new BlockStateData();
        state.setProperty("facing", "east");

        AssignBlockTypeNode assign = new AssignBlockTypeNode();
        assign.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_stairs", state)
        ));
        assign.setInput("input_block_type", "minecraft:stone_bricks");
        assign.processNode(null);
        assertTrue((Boolean) assign.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, assign.getOutput("output_placements"));
        assertEquals("minecraft:stone_bricks", out.getFirst().blockId());
        assertEquals("east", out.getFirst().stateData().get("facing"));
    }

    @Test
    void createBlockPaletteUsesTypedListsAndFailsOnMixedInputs() {
        INode meta = registry.createNodeInstance("material.basic_assignment.create_block_palette");
        assertEquals(NodeDataType.STRING_LIST, portType(meta.getInputPorts(), "input_block_ids"));
        assertEquals(NodeDataType.DOUBLE_LIST, portType(meta.getInputPorts(), "input_weights"));
        assertTrue(hasPort(meta.getOutputPorts(), "output_valid"));

        CreateBlockPaletteProbe mixed = new CreateBlockPaletteProbe();
        mixed.putInput("input_block_ids", List.of("minecraft:stone", 123, "minecraft:dirt"));
        mixed.processNode(null);
        assertFalse((Boolean) mixed.getOutput("output_valid"));

        CreateBlockPaletteProbe badWeights = new CreateBlockPaletteProbe();
        badWeights.putInput("input_block_ids", List.of("minecraft:stone", "minecraft:dirt"));
        badWeights.putInput("input_weights", List.of(1.0d, "bad"));
        badWeights.processNode(null);
        assertFalse((Boolean) badWeights.getOutput("output_valid"));

        CreateBlockPaletteNode mismatch = new CreateBlockPaletteNode();
        mismatch.setInput("input_block_ids", List.of("minecraft:stone", "minecraft:dirt"));
        mismatch.setInput("input_weights", List.of(1.0d));
        mismatch.processNode(null);
        assertFalse((Boolean) mismatch.getOutput("output_valid"));
    }

    @Test
    void createBlockPaletteAppendsBlockAbcdAfterList() {
        CreateBlockPaletteNode node = new CreateBlockPaletteNode();
        node.setInput("input_block_ids", List.of("minecraft:stone"));
        node.setInput("input_block_a", "minecraft:dirt");
        node.setInput("input_block_b", "minecraft:gravel");
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        BlockPaletteData palette = assertInstanceOf(BlockPaletteData.class, node.getOutput("output_palette"));
        assertEquals(List.of("minecraft:stone", "minecraft:dirt", "minecraft:gravel"), palette.blockIds());
    }

    @Test
    void createBlockPaletteEmptyIsValid() {
        CreateBlockPaletteNode node = new CreateBlockPaletteNode();
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        BlockPaletteData palette = assertInstanceOf(BlockPaletteData.class, node.getOutput("output_palette"));
        assertTrue(palette.isEmpty());
    }

    @Test
    void blockPaletteHasNoFallbackAndStartIndexIsIntegerOnly() {
        INode meta = registry.createNodeInstance("material.basic_assignment.block_palette");
        assertFalse(hasPort(meta.getInputPorts(), "input_fallback_block_type"));
        assertFalse(hasPort(meta.getOutputPorts(), "output_positions"));

        BlockPaletteNode preserve = new BlockPaletteNode();
        preserve.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(new BlockPos(1, 0, 0), "minecraft:oak_planks", null)
        ));
        preserve.processNode(null);
        assertTrue((Boolean) preserve.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, preserve.getOutput("output_placements"));
        assertEquals("minecraft:oak_planks", out.getFirst().blockId());

        BlockPaletteNode geometryEmpty = new BlockPaletteNode();
        BlockPosList coords = new BlockPosList();
        coords.add(new BlockPos(0, 0, 0));
        geometryEmpty.setInput("input_coordinates", coords);
        geometryEmpty.processNode(null);
        assertFalse((Boolean) geometryEmpty.getOutput("output_valid"));

        BlockPaletteProbe startProbe = new BlockPaletteProbe();
        startProbe.putInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        startProbe.putInput("input_palette", BlockPaletteData.ofBlockIds(List.of("minecraft:stone")));
        startProbe.putInput("input_start_index", 2.8d);
        startProbe.processNode(null);
        assertFalse((Boolean) startProbe.getOutput("output_valid"));
    }

    @Test
    void blockPaletteFlatCyclesPerItem() {
        BlockPaletteNode node = new BlockPaletteNode();
        node.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(new BlockPos(1, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(new BlockPos(2, 0, 0), "minecraft:oak_planks", null)
        ));
        node.setInput("input_palette", BlockPaletteData.ofBlockIds(
                List.of("minecraft:stone", "minecraft:dirt")));
        node.processNode(null);
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals("minecraft:stone", out.get(0).blockId());
        assertEquals("minecraft:dirt", out.get(1).blockId());
        assertEquals("minecraft:stone", out.get(2).blockId());
    }

    @Test
    void weightedPaletteUsesRandomOpsAndIsPositionStable() {
        INode meta = registry.createNodeInstance("material.basic_assignment.weighted_palette");
        assertFalse(hasPort(meta.getInputPorts(), "input_fallback_block_type"));
        assertEquals(NodeDataType.DOUBLE_LIST, portType(meta.getInputPorts(), "input_weights"));

        BlockPaletteData palette = BasicAssignmentUtils.buildPalette(
                List.of("minecraft:stone", "minecraft:dirt"),
                List.of(1.0d, 1.0d));

        BlockPos pos = new BlockPos(10, 20, 30);
        WeightedBlockPaletteNode first = new WeightedBlockPaletteNode();
        first.setInput("input_placements", List.of(
                new BlockPlacementData(pos, "minecraft:oak_planks", null)
        ));
        first.setInput("input_palette", palette);
        first.setInput("input_seed", 42);
        first.processNode(null);
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> firstOut = assertInstanceOf(List.class, first.getOutput("output_placements"));
        String firstId = firstOut.getFirst().blockId();

        WeightedBlockPaletteNode reordered = new WeightedBlockPaletteNode();
        reordered.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(pos, "minecraft:oak_planks", null)
        ));
        reordered.setInput("input_palette", palette);
        reordered.setInput("input_seed", 42);
        reordered.processNode(null);
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> reorderedOut = assertInstanceOf(List.class, reordered.getOutput("output_placements"));
        BlockPlacementData atPos = reorderedOut.stream()
                .filter(p -> p.pos() != null && p.pos().equals(pos))
                .findFirst()
                .orElseThrow();
        assertEquals(firstId, atPos.blockId());

        WeightedProbe doubleSeed = new WeightedProbe();
        doubleSeed.putInput("input_placements", List.of(
                new BlockPlacementData(pos, "minecraft:oak_planks", null)
        ));
        doubleSeed.putInput("input_palette", palette);
        doubleSeed.putInput("input_seed", 42.0d);
        doubleSeed.processNode(null);
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> doubleOut = assertInstanceOf(List.class, doubleSeed.getOutput("output_placements"));

        WeightedBlockPaletteNode intSeed = new WeightedBlockPaletteNode();
        intSeed.setInput("input_placements", List.of(
                new BlockPlacementData(pos, "minecraft:oak_planks", null)
        ));
        intSeed.setInput("input_palette", palette);
        intSeed.setInput("input_seed", 0);
        intSeed.processNode(null);
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> zeroOut = assertInstanceOf(List.class, intSeed.getOutput("output_placements"));
        assertNotEquals(firstId, zeroOut.getFirst().blockId());
        assertEquals(doubleOut.getFirst().blockId(), zeroOut.getFirst().blockId());
    }

    @Test
    void weightedPaletteZeroWeightEntriesNeverSelected() {
        assertEquals(1, BasicAssignmentUtils.pickWeightedIndex(0.0d, List.of(0.0d, 1.0d)));
        assertEquals(0, BasicAssignmentUtils.pickWeightedIndex(0.0d, List.of(1.0d, 0.0d, 0.0d)));
        assertEquals(0, BasicAssignmentUtils.pickWeightedIndex(1.0d, List.of(1.0d, 0.0d, 0.0d)));
    }

    @Test
    void weightedPaletteStrictWeightsOverride() {
        BlockPaletteData palette = BlockPaletteData.ofBlockIds(
                List.of("minecraft:stone", "minecraft:dirt", "minecraft:gravel"));

        WeightedBlockPaletteNode node = new WeightedBlockPaletteNode();
        node.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        node.setInput("input_palette", palette);
        node.setInput("input_weights", List.of(1.0d, 1.0d));
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
    }

    @Test
    void v39ToV40DropsBasicAssignmentDeconstructFallbackAndListWires() {
        SavedGraph v39 = new SavedGraph();
        v39.formatVersion = GraphFormatVersion.V39;

        SavedNode assign = savedNode("assign", "material.basic_assignment.assign_block_type");
        SavedNode blockPalette = savedNode("palette", "material.basic_assignment.block_palette");
        SavedNode weighted = savedNode("weighted", "material.basic_assignment.weighted_palette");
        SavedNode create = savedNode("create", "material.basic_assignment.create_block_palette");
        SavedNode preview = savedNode("preview", "output.preview.preview_blocks");
        SavedNode listSrc = savedNode("list", "input.values.value_list");

        v39.nodes = new ArrayList<>(List.of(assign, blockPalette, weighted, create, preview, listSrc));
        v39.connections = new ArrayList<>(List.of(
                wire("assign", "output_placements", "preview", "input_block_placements"),
                wire("assign", "output_positions", "preview", "input_block_placements"),
                wire("palette", "output_block_ids", "preview", "input_block_placements"),
                wire("weighted", "output_positions_tree", "preview", "input_block_placements"),
                wire("list", "output_list", "create", "input_block_ids"),
                wire("list", "output_list", "weighted", "input_weights"),
                wire("list", "output_value", "palette", "input_fallback_block_type")
        ));
        v39.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v39);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);

        assertTrue(hasWire(migrated, "assign", "output_placements", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "assign", "output_positions", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "palette", "output_block_ids", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "weighted", "output_positions_tree", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "list", "output_value", "palette", "input_fallback_block_type"));
    }

    /** Bypasses port type checks so wrong-type list elements can be exercised. */
    private static final class CreateBlockPaletteProbe extends CreateBlockPaletteNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }

    private static final class BlockPaletteProbe extends BlockPaletteNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }

    private static final class WeightedProbe extends WeightedBlockPaletteNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
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
