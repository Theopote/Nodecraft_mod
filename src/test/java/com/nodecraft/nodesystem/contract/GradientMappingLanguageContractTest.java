package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.SphereSdfData;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.math.RandomOps;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.DistanceBasedMaterialNode;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.GradientRampMapNode;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.HeightGradientMapNode;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.NoiseMaterialNode;
import com.nodecraft.nodesystem.nodes.material.gradient_mapping.SdfDrivenMaterialNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPaletteData;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gradient Mapping v1 language fence: five PURE blockId-only nodes, Valid gates, V37 migration.
 */
class GradientMappingLanguageContractTest {

    private static NodeRegistry registry;

    private static final Set<String> EXPECTED_IDS = Set.of(
            "material.gradient_mapping.height_gradient_map",
            "material.gradient_mapping.noise_material",
            "material.gradient_mapping.gradient_ramp_map",
            "material.gradient_mapping.distance_material",
            "material.gradient_mapping.sdf_material"
    );

    @BeforeAll
    static void ensureRegistry() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsAtLeastV37() {
        assertEquals(36, GraphFormatVersion.V36);
        assertEquals(37, GraphFormatVersion.V37);
        assertTrue(GraphFormatVersion.CURRENT >= GraphFormatVersion.V58);
    }

    @Test
    void exactlyFiveGradientMappingNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("material.gradient_mapping."))
                .sorted()
                .toList();
        assertEquals(5, ids.size(), "Expected 5 material.gradient_mapping nodes: " + ids);
        assertEquals(EXPECTED_IDS, Set.copyOf(ids));
    }

    @Test
    void allGradientMappingNodesArePure() {
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(HeightGradientMapNode.class,
                "material.gradient_mapping.height_gradient_map"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(NoiseMaterialNode.class,
                "material.gradient_mapping.noise_material"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(GradientRampMapNode.class,
                "material.gradient_mapping.gradient_ramp_map"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(DistanceBasedMaterialNode.class,
                "material.gradient_mapping.distance_material"));
        assertEquals(NodeEffect.PURE, NodeEffectResolver.resolve(SdfDrivenMaterialNode.class,
                "material.gradient_mapping.sdf_material"));
    }

    @Test
    void gradientMappingNodesHaveNoDeconstructOutputsAndHaveValidError() {
        for (String typeId : EXPECTED_IDS) {
            INode node = registry.createNodeInstance(typeId);
            assertFalse(hasPort(node.getOutputPorts(), "output_positions"), typeId);
            assertFalse(hasPort(node.getOutputPorts(), "output_block_ids"), typeId);
            assertTrue(hasPort(node.getOutputPorts(), "output_placements"), typeId);
            assertTrue(hasPort(node.getOutputPorts(), "output_valid"), typeId);
            assertTrue(hasPort(node.getOutputPorts(), "output_error"), typeId);
        }

        INode noise = registry.createNodeInstance("material.gradient_mapping.noise_material");
        assertEquals(NodeDataType.DOUBLE_LIST, portType(noise.getOutputPorts(), "output_noise_values"));

        INode distance = registry.createNodeInstance("material.gradient_mapping.distance_material");
        assertEquals(NodeDataType.POINT, portType(distance.getInputPorts(), "input_reference_point"));
        assertEquals(NodeDataType.DOUBLE_LIST, portType(distance.getOutputPorts(), "output_distances"));

        INode sdf = registry.createNodeInstance("material.gradient_mapping.sdf_material");
        assertEquals(NodeDataType.DOUBLE_LIST, portType(sdf.getOutputPorts(), "output_distances"));
        assertEquals(NodeDataType.DOUBLE_LIST, portType(sdf.getOutputPorts(), "output_weights"));

        INode ramp = registry.createNodeInstance("material.gradient_mapping.gradient_ramp_map");
        assertEquals("Height Palette Map", ramp.getDisplayName());
        assertEquals(NodeDataType.BLOCK_PALETTE, portType(ramp.getInputPorts(), "input_palette"));
    }

    @Test
    void heightGradientPreservesPartialBandsAndSingleYUsesBottom() {
        HeightGradientMapNode node = new HeightGradientMapNode();

        BlockStateData state = new BlockStateData();
        state.setProperty("facing", "north");

        BlockPlacementData low = new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:oak_planks", state);
        BlockPlacementData high = new BlockPlacementData(new BlockPos(0, 10, 0), "minecraft:oak_planks", state);

        node.setInput("input_placements", List.of(low, high));
        node.setInput("input_bottom", "minecraft:dirt");
        // middle/top/peak unconnected  -> preserve source for those bands
        node.processNode(null);

        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals("minecraft:dirt", findAt(out, 0, 0, 0).blockId());
        assertEquals("north", findAt(out, 0, 0, 0).stateData().get("facing"));
        assertEquals("minecraft:oak_planks", findAt(out, 0, 10, 0).blockId());

        HeightGradientMapNode single = new HeightGradientMapNode();
        single.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(1, 5, 1), "minecraft:stone", null)
        ));
        single.setInput("input_bottom", "minecraft:dirt");
        single.setInput("input_peak", "minecraft:snow_block");
        single.processNode(null);
        assertTrue((Boolean) single.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> singleOut = assertInstanceOf(List.class, single.getOutput("output_placements"));
        assertEquals("minecraft:dirt", singleOut.getFirst().blockId());
    }

    @Test
    void heightGradientRejectsNaNRatio() {
        HeightGradientMapNode node = new HeightGradientMapNode();
        node.setLowerEndRatio(Double.NaN);
        node.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:dirt", null)
        ));
        node.setInput("input_bottom", "minecraft:stone");
        node.processNode(null);
        assertFalse((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertTrue(out.isEmpty());
    }

    @Test
    void noiseUsesRandomOpsKernelAndRejectsZeroWidthThreshold() {
        NoiseMaterialNode node = new NoiseMaterialNode();
        node.setScale(0.12d);
        node.setOctaves(1);
        node.setPersistence(0.5d);
        node.setLacunarity(2.0d);

        BlockPlacementData placement = new BlockPlacementData(new BlockPos(2, 3, 4), "minecraft:dirt", null);
        node.setInput("input_placements", List.of(placement));
        node.setInput("input_palette", BlockPaletteData.ofBlockIds(List.of("minecraft:stone", "minecraft:dirt")));
        node.setInput("input_seed", 42);
        node.setInput("input_threshold_low", 0.0d);
        node.setInput("input_threshold_high", 1.0d);
        node.processNode(null);

        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<Double> noiseValues = assertInstanceOf(List.class, node.getOutput("output_noise_values"));
        assertEquals(1, noiseValues.size());

        double expected = GradientMaterialUtilsClamp01(
                (RandomOps.valueNoise3(2 * 0.12d, 3 * 0.12d, 4 * 0.12d, 42) + 1.0d) * 0.5d
        );
        assertEquals(expected, noiseValues.getFirst(), 1.0e-12d);

        NoiseMaterialNode invalid = new NoiseMaterialNode();
        invalid.setInput("input_placements", List.of(placement));
        invalid.setInput("input_threshold_low", 0.5d);
        invalid.setInput("input_threshold_high", 0.5d);
        invalid.processNode(null);
        assertFalse((Boolean) invalid.getOutput("output_valid"));
    }

    @Test
    void noiseSeedIsIntegerOnly() {
        assertEquals(7, RandomOps.resolveSeed(7));
        assertEquals(0, RandomOps.resolveSeed(7.0d));
        assertEquals(0, RandomOps.resolveSeed("7"));
        assertEquals(0, RandomOps.resolveSeed(null));
    }

    @Test
    void heightPaletteEmptyPreservesPlacementsWithoutStone() {
        GradientRampMapNode node = new GradientRampMapNode();
        BlockPlacementData placement = new BlockPlacementData(new BlockPos(0, 1, 0), "minecraft:oak_log", null);
        node.setInput("input_placements", List.of(placement));
        node.setInput("input_palette", BlockPaletteData.empty());
        node.processNode(null);

        assertTrue((Boolean) node.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<BlockPlacementData> out = assertInstanceOf(List.class, node.getOutput("output_placements"));
        assertEquals(1, out.size());
        assertEquals("minecraft:oak_log", out.getFirst().blockId());
        assertFalse(out.getFirst().blockId().contains("stone"));
    }

    @Test
    void distanceRequiresExactlyOneReferenceAndPointType() {
        DistanceBasedMaterialNode noRef = new DistanceBasedMaterialNode();
        noRef.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:dirt", null)
        ));
        noRef.setInput("input_min_distance", 0.0d);
        noRef.setInput("input_max_distance", 10.0d);
        noRef.processNode(null);
        assertFalse((Boolean) noRef.getOutput("output_valid"));

        DistanceBasedMaterialNode vectorRejected = new DistanceBasedMaterialNode();
        vectorRejected.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:dirt", null)
        ));
        vectorRejected.setInput("input_min_distance", 0.0d);
        vectorRejected.setInput("input_max_distance", 10.0d);
        vectorRejected.setInput("input_reference_point", new Vector3d(0, 0, 0));
        vectorRejected.processNode(null);
        assertFalse((Boolean) vectorRejected.getOutput("output_valid"));

        DistanceBasedMaterialNode twoRefs = new DistanceBasedMaterialNode();
        twoRefs.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:dirt", null)
        ));
        twoRefs.setInput("input_min_distance", 0.0d);
        twoRefs.setInput("input_max_distance", 10.0d);
        twoRefs.setInput("input_reference_point", new PointData(0, 0, 0));
        twoRefs.setInput("input_reference_plane", new PlaneData(
                new Vector3d(0, 0, 0),
                new Vector3d(0, 1, 0)
        ));
        twoRefs.processNode(null);
        assertFalse((Boolean) twoRefs.getOutput("output_valid"));

        DistanceBasedMaterialNode ok = new DistanceBasedMaterialNode();
        ok.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(3, 0, 0), "minecraft:dirt", null)
        ));
        ok.setInput("input_palette", BlockPaletteData.ofBlockIds(List.of("minecraft:stone", "minecraft:dirt")));
        ok.setInput("input_min_distance", 0.0d);
        ok.setInput("input_max_distance", 10.0d);
        ok.setInput("input_reference_point", new PointData(0, 0, 0));
        ok.processNode(null);
        assertTrue((Boolean) ok.getOutput("output_valid"));
        @SuppressWarnings("unchecked")
        List<Double> distances = assertInstanceOf(List.class, ok.getOutput("output_distances"));
        assertEquals(1, distances.size());
        assertTrue(Double.isFinite(distances.getFirst()));
    }

    @Test
    void sdfMissingOrNonPositiveHalfWidthIsInvalid() {
        SdfDrivenMaterialNode missing = new SdfDrivenMaterialNode();
        missing.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:dirt", null)
        ));
        missing.processNode(null);
        assertFalse((Boolean) missing.getOutput("output_valid"));
        assertTrue(((String) missing.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("sdf"));

        SdfDrivenMaterialNode halfWidth = new SdfDrivenMaterialNode();
        halfWidth.setInput("input_placements", List.of(
                new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:dirt", null)
        ));
        halfWidth.setInput("input_sdf", new SphereSdfData(new Vector3d(0, 0, 0), 1.0d));
        halfWidth.setInput("input_half_width", 0.0d);
        halfWidth.processNode(null);
        assertFalse((Boolean) halfWidth.getOutput("output_valid"));
        assertTrue(((String) halfWidth.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("half"));
    }

    @Test
    void v36ToV37DropsGradientDeconstructAndLegacyWires() {
        SavedGraph v36 = new SavedGraph();
        v36.formatVersion = GraphFormatVersion.V36;

        SavedNode height = savedNode("height", "material.gradient_mapping.height_gradient_map");
        SavedNode noise = savedNode("noise", "material.gradient_mapping.noise_material");
        SavedNode ramp = savedNode("ramp", "material.gradient_mapping.gradient_ramp_map");
        Map<String, Object> rampState = new HashMap<>();
        rampState.put("rampBlocks", "minecraft:stone,minecraft:dirt");
        ramp.state = rampState;
        SavedNode distance = savedNode("distance", "material.gradient_mapping.distance_material");
        SavedNode preview = savedNode("preview", "output.preview.preview_blocks");
        SavedNode vector = savedNode("vector", "reference.vectors.construct_vector");

        v36.nodes = new ArrayList<>(List.of(height, noise, ramp, distance, preview, vector));
        v36.connections = new ArrayList<>(List.of(
                wire("height", "output_placements", "preview", "input_block_placements"),
                wire("height", "output_positions", "preview", "input_block_placements"),
                wire("noise", "output_block_ids", "preview", "input_block_placements"),
                wire("vector", "output_vector", "distance", "input_reference_point")
        ));
        v36.nodePositions = Map.of();

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(v36);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);

        assertTrue(hasWire(migrated, "height", "output_placements", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "height", "output_positions", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "noise", "output_block_ids", "preview", "input_block_placements"));
        assertFalse(hasWire(migrated, "vector", "output_vector", "distance", "input_reference_point"));

        SavedNode migratedRamp = migrated.nodes.stream()
                .filter(n -> "ramp".equals(n.nodeId))
                .findFirst()
                .orElseThrow();
        assertInstanceOf(Map.class, migratedRamp.state);
        @SuppressWarnings("unchecked")
        Map<String, Object> cleaned = (Map<String, Object>) migratedRamp.state;
        assertFalse(cleaned.containsKey("rampBlocks"));
    }

    /** Local clamp matching GradientMaterialUtils.clamp01 for the single-octave noise spot-check. */
    private static double GradientMaterialUtilsClamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
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
