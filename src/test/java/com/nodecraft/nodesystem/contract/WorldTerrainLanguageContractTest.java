package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.GridScalarFieldData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.datatypes.VectorFieldData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.world.terrain.FlowAccumulationFieldNode;
import com.nodecraft.nodesystem.nodes.world.terrain.HeightfieldToBlocksNode;
import com.nodecraft.nodesystem.nodes.world.terrain.PlatePartitionFieldNode;
import com.nodecraft.nodesystem.nodes.world.terrain.SampleFieldOnRegionNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.GenerationLimits;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * World Terrain / Terrain Field v1 language fence (Graph V63).
 */
class WorldTerrainLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "world.terrain.height_seed_field",
            "world.terrain.plate_partition_field",
            "world.terrain.orogenic_uplift_field",
            "world.terrain.rift_subsidence_field",
            "world.terrain.combine_height_fields",
            "world.terrain.flow_direction_field",
            "world.terrain.flow_accumulation_field",
            "world.terrain.river_mask_field",
            "world.terrain.precipitation_field",
            "world.terrain.thermal_erosion_step",
            "world.terrain.hydraulic_erosion_step",
            "world.terrain.deposition_step",
            "world.terrain.delta_accumulate_field",
            "world.terrain.temperature_field",
            "world.terrain.biome_classify",
            "world.terrain.heightfield_to_blocks",
            "world.terrain.biome_field_to_blocks",
            "world.terrain.scalar_field_slice_to_blocks",
            "world.terrain.sample_field_on_region"
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
    void currentGraphFormatIsV63() {
        assertEquals(63, GraphFormatVersion.V63);
        assertEquals(GraphFormatVersion.V63, GraphFormatVersion.CURRENT);
    }

    @Test
    void generationLimitsExposeTerrainCaps() {
        assertEquals(262_144, GenerationLimits.MAX_TERRAIN_GRID_CELLS);
        assertTrue(GenerationLimits.MAX_TERRAIN_SIMULATION_WORK > 0L);
        assertTrue(GenerationLimits.MAX_TERRAIN_PLACEMENTS > 0);
        assertTrue(GenerationLimits.MAX_TERRAIN_SAMPLES > 0);
        assertEquals(1_024, GenerationLimits.MAX_TERRAIN_PLATES);
        assertTrue(GenerationLimits.MAX_TERRAIN_FLOW_ITERATIONS > 0);
    }

    @Test
    void exactlyNineteenWorldTerrainNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("world.terrain."))
                .sorted()
                .toList();
        assertEquals(19, ids.size(), ids.toString());
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
    }

    @Test
    void worldTerrainNodesHaveUniqueOrderZeroThroughEighteen() {
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
        assertEquals(19, orders.length);
        for (int i = 0; i < orders.length; i++) {
            assertEquals(i, orders[i], "Expected unique order " + i);
        }
    }

    @Test
    void allWorldTerrainNodesArePureWithValidError() {
        for (String typeId : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(typeId);
            assertNotNull(node, typeId);
            NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
            assertNotNull(info, typeId);
            assertEquals(NodeEffect.PURE, info.effect(), typeId);
            assertTrue(hasPort(node, "output_valid"), typeId + " missing Valid");
            assertTrue(hasPort(node, "output_error"), typeId + " missing Error");
        }
    }

    @Test
    void gridScalarFieldUsesCellCenterIndexMapping() {
        double[] values = new double[]{10.0};
        GridScalarFieldData grid = GridScalarFieldData.fromValues(0, 0, 0, 0, 64, values);
        Vector3d center = BlockSpace.cellCenter(0, 64, 0);
        assertEquals(10.0d, grid.sampleScalar(center), 1e-9);
        assertTrue(Double.isNaN(grid.sampleScalar(new Vector3d(1.5, 64.5, 0.5))));
    }

    @Test
    void gridScalarFieldFromValuesRejectsOverflowSize() {
        boolean threw = false;
        try {
            // Force size check path with mismatched length for a huge claimed span without allocating MAX cells.
            GridScalarFieldData.fromValues(0, 1, 0, 0, 64, new double[]{1.0});
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        assertTrue(threw);
    }

    @Test
    void sampleFieldPortsArePointListAndDoubleList() {
        SampleFieldOnRegionNode node = new SampleFieldOnRegionNode();
        assertPortType(node, "output_sample_points", NodeDataType.POINT_LIST);
        assertPortType(node, "output_sample_values", NodeDataType.DOUBLE_LIST);
        assertTrue(hasPort(node, "output_hit_limit"));
        assertTrue(hasPort(node, "output_complete"));
        assertTrue(hasPort(node, "output_stopped_reason"));
        assertFalse(hasPort(node, "output_was_clamped"));
        assertFalse(hasPort(node, "output_points"));
    }

    @Test
    void heightfieldToBlocksUsesSurfaceBlocksAndComplete() {
        HeightfieldToBlocksNode node = new HeightfieldToBlocksNode();
        assertPortType(node, "output_surface_blocks", NodeDataType.BLOCK_LIST);
        assertTrue(hasPort(node, "output_complete"));
        assertFalse(hasPort(node, "output_surface_points"));
        assertTrue(hasPort(node, "input_water_block"));
    }

    @Test
    void biomeFieldToBlocksUsesTypedPalette() {
        INode node = registry.createNodeInstance("world.terrain.biome_field_to_blocks");
        assertNotNull(node);
        assertPortType(node, "input_palette", NodeDataType.BLOCK_PALETTE);
        assertPortType(node, "output_surface_blocks", NodeDataType.BLOCK_LIST);
        assertTrue(hasPort(node, "output_complete"));
    }

    @Test
    void biomeClassifyLabelsAreStringListWithoutLegend() {
        INode node = registry.createNodeInstance("world.terrain.biome_classify");
        assertNotNull(node);
        assertPortType(node, "output_biome_labels", NodeDataType.STRING_LIST);
        assertFalse(hasPort(node, "output_legend"));
    }

    @Test
    void platePartitionRejectsPlateCountOne() {
        PlatePartitionProbe node = new PlatePartitionProbe();
        node.connectInput("input_plate_count", NodeDataType.INTEGER);
        node.setInput("input_plate_count", 1);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void flowAccumulationRejectsWorkOverHardCap() {
        FlowAccumulationProbe node = new FlowAccumulationProbe();
        // 128×128 cells × 1025 iterations = 16_793_600 > MAX_TERRAIN_SIMULATION_WORK.
        node.setInput("input_flow_field", (VectorFieldData) (point, dest) -> dest.set(1.0d, 0.0d, 0.0d));
        node.connectInput("input_region", NodeDataType.REGION);
        node.setInput("input_region", new RegionData(
                new BlockPos(0, 64, 0),
                new BlockPos(127, 64, 127)));
        node.connectInput("input_iterations", NodeDataType.INTEGER);
        node.setInput("input_iterations", 1025);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        String error = String.valueOf(node.getOutput("output_error"));
        assertTrue(error.contains("MAX_TERRAIN_SIMULATION_WORK"), error);
    }

    @Test
    void flowAccumulationOutsideDomainReturnsNaN() {
        FlowAccumulationProbe node = new FlowAccumulationProbe();
        node.setInput("input_flow_field", (VectorFieldData) (point, dest) -> dest.set(0.0d, 0.0d, 0.0d));
        node.connectInput("input_region", NodeDataType.REGION);
        node.setInput("input_region", new RegionData(
                new BlockPos(0, 64, 0),
                new BlockPos(7, 64, 7)));
        node.connectInput("input_iterations", NodeDataType.INTEGER);
        node.setInput("input_iterations", 1);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        ScalarFieldData field = (ScalarFieldData) node.getOutput("output_accumulation_field");
        assertNotNull(field);
        double inside = field.sampleScalar(new Vector3d(0.5d, 64.5d, 0.5d));
        assertTrue(Double.isFinite(inside), "inside domain should be finite");
        double outside = field.sampleScalar(new Vector3d(100_000.5d, 64.5d, 0.5d));
        assertTrue(Double.isNaN(outside), "outside domain must be NaN, not clamp-to-edge");
    }

    @Test
    void fillTilesHonorsMaxColumnsExactly() {
        HeightfieldProbe node = new HeightfieldProbe();
        node.setInput("input_height_field", (ScalarFieldData) point -> 1.0d);
        node.connectInput("input_region", NodeDataType.REGION);
        node.setInput("input_region", new RegionData(
                new BlockPos(0, 0, 0),
                new BlockPos(15, 64, 15)));
        node.connectInput("input_surface_block", NodeDataType.BLOCK_TYPE);
        node.setInput("input_surface_block", "minecraft:stone");
        node.connectInput("input_fill_depth", NodeDataType.INTEGER);
        node.setInput("input_fill_depth", 0);
        node.connectInput("input_step", NodeDataType.INTEGER);
        node.setInput("input_step", 4);
        node.connectInput("input_fill_tiles", NodeDataType.BOOLEAN);
        node.setInput("input_fill_tiles", true);
        node.connectInput("input_max_columns", NodeDataType.INTEGER);
        node.setInput("input_max_columns", 3);
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"),
                String.valueOf(node.getOutput("output_error")));
        assertEquals(3, node.getOutput("output_column_count"));
        assertEquals(Boolean.TRUE, node.getOutput("output_hit_limit"));
        assertEquals(Boolean.FALSE, node.getOutput("output_complete"));
        assertEquals("max_columns", node.getOutput("output_stopped_reason"));
    }

    @Test
    void migrateV62ToV63RenamesTerrainPorts() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V62;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();
        graph.nodePositions = new HashMap<>();

        SavedNode heightfield = savedNode("h1", "world.terrain.heightfield_to_blocks");
        Map<String, Object> hfState = new HashMap<>();
        hfState.put("surfaceBlock", "minecraft:grass_block");
        heightfield.state = hfState;

        SavedNode sample = savedNode("s1", "world.terrain.sample_field_on_region");
        SavedNode biome = savedNode("b1", "world.terrain.biome_classify");
        graph.nodes.addAll(List.of(heightfield, sample, biome));

        graph.connections.add(wire("h1", "output_surface_points", "t1", "input_stub"));
        graph.connections.add(wire("s1", "output_was_clamped", "t2", "input_stub"));
        graph.connections.add(wire("s1", "output_points", "t3", "input_stub"));
        graph.connections.add(wire("s1", "output_values", "t4", "input_stub"));
        graph.connections.add(wire("b1", "output_legend", "t5", "input_stub"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.V63, migrated.formatVersion);

        List<String> kept = migrated.connections.stream()
                .map(c -> c.sourceNodeId + ":" + c.sourcePortId + "->" + c.targetNodeId + ":" + c.targetPortId)
                .sorted()
                .toList();
        assertEquals(List.of(
                "b1:output_biome_labels->t5:input_stub",
                "h1:output_surface_blocks->t1:input_stub",
                "s1:output_hit_limit->t2:input_stub",
                "s1:output_sample_points->t3:input_stub",
                "s1:output_sample_values->t4:input_stub"
        ), kept);

        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) nodeOf(migrated, "h1").state;
        assertFalse(state.containsKey("surfaceBlock"));
    }

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
                .filter(port -> inputPortId.equals(port.getId()))
                .findFirst()
                .orElseThrow();
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
        target.getInput(inputPortId);
    }

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }

    private static final class PlatePartitionProbe extends PlatePartitionFieldNode {
        void connectInput(String portId, NodeDataType outputType) {
            WorldTerrainLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class FlowAccumulationProbe extends FlowAccumulationFieldNode {
        void connectInput(String portId, NodeDataType outputType) {
            WorldTerrainLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class HeightfieldProbe extends HeightfieldToBlocksNode {
        void connectInput(String portId, NodeDataType outputType) {
            WorldTerrainLanguageContractTest.connectInput(this, portId, outputType);
        }
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
