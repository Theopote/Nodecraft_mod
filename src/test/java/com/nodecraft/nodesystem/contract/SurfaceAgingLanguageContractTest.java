package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.material.surface_aging.CrackPatternNode;
import com.nodecraft.nodesystem.nodes.material.surface_aging.MossGrowthNode;
import com.nodecraft.nodesystem.nodes.material.surface_aging.WeatheringNode;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Surface Aging v1 language fence: three PURE topology aging nodes, Valid gates, V39 migration.
 */
class SurfaceAgingLanguageContractTest {

    private static NodeRegistry registry;

    private static final Set<String> EXPECTED_IDS = Set.of(
            "material.surface_aging.weathering",
            "material.surface_aging.moss_growth",
            "material.surface_aging.crack_pattern"
    );

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV39() {
        assertEquals(38, GraphFormatVersion.V38);
        assertEquals(39, GraphFormatVersion.V39);
        assertEquals(GraphFormatVersion.V46, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyThreeSurfaceAgingNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("material.surface_aging."))
                .sorted()
                .toList();
        assertEquals(3, ids.size(), "Expected 3 material.surface_aging nodes: " + ids);
        assertEquals(EXPECTED_IDS, Set.copyOf(ids));
    }

    @Test
    void allSurfaceAgingNodesArePure() {
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(WeatheringNode.class,
                "material.surface_aging.weathering"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(MossGrowthNode.class,
                "material.surface_aging.moss_growth"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(CrackPatternNode.class,
                "material.surface_aging.crack_pattern"));
    }

    @Test
    void surfaceAgingNodesHavePlacementsValidErrorAffectedAndOriginNoDeconstruct() {
        for (String typeId : EXPECTED_IDS) {
            INode node = registry.createNodeInstance(typeId);
            assertTrue(hasPort(node.getInputPorts(), "input_placements"), typeId);
            assertTrue(hasPort(node.getInputPorts(), "input_aging_origin"), typeId);
            assertEquals(NodeDataType.BLOCK_POS, portType(node.getInputPorts(), "input_aging_origin"), typeId);
            assertFalse(hasPort(node.getOutputPorts(), "output_positions"), typeId);
            assertFalse(hasPort(node.getOutputPorts(), "output_block_ids"), typeId);
            assertTrue(hasPort(node.getOutputPorts(), "output_placements"), typeId);
            assertTrue(hasPort(node.getOutputPorts(), "output_valid"), typeId);
            assertTrue(hasPort(node.getOutputPorts(), "output_error"), typeId);
            assertTrue(hasPort(node.getOutputPorts(), "output_affected_count"), typeId);
            assertEquals(NodeDataType.INTEGER, portType(node.getOutputPorts(), "output_affected_count"), typeId);
        }
        INode crack = registry.createNodeInstance("material.surface_aging.crack_pattern");
        assertFalse(hasPort(crack.getInputPorts(), "input_interval"));
        assertEquals("Surface Cracks", crack.getDisplayName());
    }

    @Test
    void mossAndCrackPreserveStateDataThroughRemap() {
        BlockStateData state = new BlockStateData();
        state.setProperty("facing", "east");

        List<BlockPlacementData> placements = List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", state)
        );

        MossGrowthNode moss = new MossGrowthNode();
        moss.setInput("input_placements", placements);
        moss.setInput("input_moss", "minecraft:moss_block");
        moss.setInput("input_amount", 1.0d);
        moss.setInput("input_seed", 0);
        moss.processNode(null);
        assertTrue((Boolean) moss.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> mossOut = assertInstanceOf(List.class, moss.getOutput("output_placements"));
        assertEquals("minecraft:moss_block", mossOut.getFirst().blockId());
        assertEquals("east", mossOut.getFirst().stateData().get("facing"));

        CrackPatternNode crack = new CrackPatternNode();
        crack.setInput("input_placements", placements);
        crack.setInput("input_crack", "minecraft:cracked_stone_bricks");
        crack.setInput("input_amount", 1.0d);
        crack.setInput("input_seed", 0);
        crack.processNode(null);
        assertTrue((Boolean) crack.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> crackOut = assertInstanceOf(List.class, crack.getOutput("output_placements"));
        assertEquals("minecraft:cracked_stone_bricks", crackOut.getFirst().blockId());
        assertEquals("east", crackOut.getFirst().stateData().get("facing"));
    }

    @Test
    void geometryWithoutMaterialsIsInvalidAndDoesNotInventStoneBricks() {
        WeatheringNode node = new WeatheringNode();
        BlockPosList coords = new BlockPosList();
        coords.add(new BlockPos(0, 0, 0));
        node.setInput("input_coordinates", coords);
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertTrue(out.isEmpty());
        String error = ((String) node.getOutput("output_error")).toLowerCase(Locale.ROOT);
        assertFalse(error.contains("stone_bricks"));
        assertTrue(error.contains("base"));
    }

    @Test
    void geometryWithAgingRoleOnlyRequiresBaseBlock() {
        BlockPosList coords = new BlockPosList();
        coords.add(new BlockPos(0, 0, 0));
        coords.add(new BlockPos(1, 0, 0));

        WeatheringNode weathering = new WeatheringNode();
        weathering.setInput("input_coordinates", coords);
        weathering.setInput("input_aged_block", "minecraft:cobblestone");
        weathering.setInput("input_amount", 0.2d);
        weathering.processNode(null);
        assertFalse((Boolean) weathering.getOutput("output_valid"));
        assertTrue(((String) weathering.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("base"));

        MossGrowthNode moss = new MossGrowthNode();
        moss.setInput("input_coordinates", coords);
        moss.setInput("input_moss", "minecraft:moss_block");
        moss.setInput("input_amount", 0.2d);
        moss.processNode(null);
        assertFalse((Boolean) moss.getOutput("output_valid"));
        assertTrue(((String) moss.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("base"));

        CrackPatternNode crack = new CrackPatternNode();
        crack.setInput("input_coordinates", coords);
        crack.setInput("input_crack", "minecraft:cracked_stone_bricks");
        crack.setInput("input_amount", 0.2d);
        crack.processNode(null);
        assertFalse((Boolean) crack.getOutput("output_valid"));
        assertTrue(((String) crack.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("base"));
    }

    @Test
    void geometryWithBaseAndAgedIsValidAndPreservesUnagedCells() {
        BlockPosList coords = new BlockPosList();
        coords.add(new BlockPos(0, 0, 0));
        coords.add(new BlockPos(1, 0, 0));
        coords.add(new BlockPos(2, 0, 0));

        WeatheringNode node = new WeatheringNode();
        node.setInput("input_coordinates", coords);
        node.setInput("input_base_block", "minecraft:oak_planks");
        node.setInput("input_aged_block", "minecraft:cobblestone");
        node.setInput("input_amount", 0.0d);
        node.setInput("input_seed", 0);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals(3, out.size());
        assertEquals("minecraft:oak_planks", findAt(out, 0, 0, 0).blockId());
        assertEquals("minecraft:oak_planks", findAt(out, 1, 0, 0).blockId());
        assertEquals("minecraft:oak_planks", findAt(out, 2, 0, 0).blockId());
        assertEquals(0, (Integer) node.getOutput("output_affected_count"));
    }

    @Test
    void weatheringAgesSurfaceNotInteriorAndAmountNaNFails() {
        List<BlockPlacementData> cube = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    cube.add(new BlockPlacementData(new BlockPos(x, y, z), "minecraft:oak_planks", null));
                }
            }
        }

        WeatheringNode node = new WeatheringNode();
        node.setInput("input_placements", cube);
        node.setInput("input_aged_block", "minecraft:cobblestone");
        node.setInput("input_amount", 1.0d);
        node.setInput("input_seed", 0);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals("minecraft:oak_planks", findAt(out, 1, 1, 1).blockId());
        assertEquals("minecraft:cobblestone", findAt(out, 0, 1, 1).blockId());
        assertEquals(26, (Integer) node.getOutput("output_affected_count"));

        WeatheringNode nan = new WeatheringNode();
        nan.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null)
        ));
        nan.setInput("input_aged_block", "minecraft:cobblestone");
        nan.setInput("input_amount", Double.NaN);
        nan.processNode(null);
        assertFalse((Boolean) nan.getOutput("output_valid"));
    }

    @Test
    void weatheringSeedIsIntegerOnlyViaRandomOps() {
        List<BlockPlacementData> placements = List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(new BlockPos(1, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(new BlockPos(2, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(new BlockPos(0, 0, 1), "minecraft:oak_planks", null)
        );

        WeatheringNode intSeed = new WeatheringNode();
        intSeed.setInput("input_placements", placements);
        intSeed.setInput("input_aged_block", "minecraft:cobblestone");
        intSeed.setInput("input_amount", 0.5d);
        intSeed.setInput("input_seed", 42);
        intSeed.processNode(null);
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> intOut = assertInstanceOf(List.class, intSeed.getOutput("output_placements"));

        WeatheringProbe doubleSeed = new WeatheringProbe();
        doubleSeed.putInput("input_placements", placements);
        doubleSeed.putInput("input_aged_block", "minecraft:cobblestone");
        doubleSeed.putInput("input_amount", 0.5d);
        doubleSeed.putInput("input_seed", 42.0d);
        doubleSeed.processNode(null);
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> doubleOut = assertInstanceOf(List.class, doubleSeed.getOutput("output_placements"));

        WeatheringNode zeroSeed = new WeatheringNode();
        zeroSeed.setInput("input_placements", placements);
        zeroSeed.setInput("input_aged_block", "minecraft:cobblestone");
        zeroSeed.setInput("input_amount", 0.5d);
        zeroSeed.setInput("input_seed", 0);
        zeroSeed.processNode(null);
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> zeroOut = assertInstanceOf(List.class, zeroSeed.getOutput("output_placements"));

        assertNotEquals(blockIds(intOut), blockIds(zeroOut));
        assertEquals(blockIds(doubleOut), blockIds(zeroOut));
    }

    @Test
    void mossOnlyAgesTopExposedVoxels() {
        // 2-high column: bottom has up neighbor (side-exposed only); top is top-exposed.
        List<BlockPlacementData> column = List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(new BlockPos(0, 1, 0), "minecraft:oak_planks", null)
        );

        MossGrowthNode node = new MossGrowthNode();
        node.setInput("input_placements", column);
        node.setInput("input_moss", "minecraft:moss_block");
        node.setInput("input_amount", 1.0d);
        node.setInput("input_seed", 0);
        node.processNode(null);
        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals("minecraft:oak_planks", findAt(out, 0, 0, 0).blockId());
        assertEquals("minecraft:moss_block", findAt(out, 0, 1, 0).blockId());
        assertEquals(1, (Integer) node.getOutput("output_affected_count"));
    }

    @Test
    void crackUsesSurfaceRandomOpsMaskNotWorldStripesAndOriginRules() {
        INode crackMeta = registry.createNodeInstance("material.surface_aging.crack_pattern");
        assertNull(crackMeta.getInputPorts().stream()
                .filter(p -> "input_interval".equalsIgnoreCase(p.getId()))
                .findFirst()
                .orElse(null));

        List<BlockPlacementData> placements = List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(new BlockPos(1, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(new BlockPos(2, 0, 0), "minecraft:oak_planks", null),
                new BlockPlacementData(new BlockPos(3, 0, 0), "minecraft:oak_planks", null)
        );

        CrackPatternNode full = new CrackPatternNode();
        full.setInput("input_placements", placements);
        full.setInput("input_crack", "minecraft:cracked_stone_bricks");
        full.setInput("input_amount", 1.0d);
        full.setInput("input_seed", 7);
        full.processNode(null);
        assertTrue((Boolean) full.getOutput("output_valid"));
        assertEquals(4, (Integer) full.getOutput("output_affected_count"));

        CrackPatternNode missingOrigin = new CrackPatternNode();
        missingOrigin.compute(Map.of(
                "input_placements", placements,
                "input_crack", "minecraft:cracked_stone_bricks",
                "input_amount", 1.0d,
                "input_seed", 7
        ));
        assertTrue((Boolean) missingOrigin.getOutput("output_valid"));

        CrackProbe pointOrigin = new CrackProbe();
        pointOrigin.putInput("input_placements", placements);
        pointOrigin.putInput("input_crack", "minecraft:cracked_stone_bricks");
        pointOrigin.putInput("input_amount", 1.0d);
        pointOrigin.putInput("input_seed", 7);
        pointOrigin.putInput("input_aging_origin", new PointData(0, 0, 0));
        pointOrigin.processNode(null);
        assertFalse((Boolean) pointOrigin.getOutput("output_valid"));
        assertTrue(((String) pointOrigin.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("block_pos"));

        CrackProbe vectorOrigin = new CrackProbe();
        vectorOrigin.putInput("input_placements", placements);
        vectorOrigin.putInput("input_crack", "minecraft:cracked_stone_bricks");
        vectorOrigin.putInput("input_amount", 1.0d);
        vectorOrigin.putInput("input_seed", 7);
        vectorOrigin.putInput("input_aging_origin", new Vector3d(0, 0, 0));
        vectorOrigin.processNode(null);
        assertFalse((Boolean) vectorOrigin.getOutput("output_valid"));
    }

    @Test
    void v38ToV39DropsSurfaceAgingDeconstructAndCrackIntervalWires() {
        SavedGraph v38 = new SavedGraph();
        v38.formatVersion = GraphFormatVersion.V38;

        SavedNode weathering = savedNode("weathering", "material.surface_aging.weathering");
        SavedNode moss = savedNode("moss", "material.surface_aging.moss_growth");
        SavedNode crack = savedNode("crack", "material.surface_aging.crack_pattern");
        SavedNode preview = savedNode("preview", "output.preview.preview_blocks");
        SavedNode amount = savedNode("amount", "input.numeric.float_constant");

        v38.nodes = new ArrayList<>(List.of(weathering, moss, crack, preview, amount));
        v38.connections = new ArrayList<>(List.of(
                wire("weathering", "output_placements", "preview", "input_block_placements"),
                wire("weathering", "output_positions", "preview", "input_block_placements"),
                wire("moss", "output_block_ids", "preview", "input_block_placements"),
                wire("crack", "output_positions", "preview", "input_block_placements"),
                wire("amount", "output_value", "crack", "input_interval")
        ));
        v38.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v38);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);

        assertTrue(hasWire(migrated, "weathering", "output_placements", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "weathering", "output_positions", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "moss", "output_block_ids", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "crack", "output_positions", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "amount", "output_value", "crack", "input_interval"));
    }

    /** Bypasses port type checks so wrong-type Aging Origin / Seed can be exercised. */
    private static final class WeatheringProbe extends WeatheringNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }

    private static final class CrackProbe extends CrackPatternNode {
        void putInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
    }

    private static List<String> blockIds(List<BlockPlacementData> placements) {
        return placements.stream().map(BlockPlacementData::blockId).toList();
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
