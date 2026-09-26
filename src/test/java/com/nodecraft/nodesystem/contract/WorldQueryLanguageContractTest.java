package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.world.query.FilterGridPointsNode;
import com.nodecraft.nodesystem.nodes.world.query.FilterPointsByRuleNode;
import com.nodecraft.nodesystem.nodes.world.query.FloodFillNode;
import com.nodecraft.nodesystem.nodes.world.query.GetEntitiesInRegionNode;
import com.nodecraft.nodesystem.nodes.world.query.GetEntityNode;
import com.nodecraft.nodesystem.nodes.world.query.GetNeighborBlocksNode;
import com.nodecraft.nodesystem.nodes.world.query.IsGridPointNode;
import com.nodecraft.nodesystem.nodes.world.query.RaycastNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
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
 * World Query v1 language fence (Graph V60).
 */
class WorldQueryLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "world.query.is_grid_point",
            "world.query.filter_grid_points",
            "world.query.is_point_in_region",
            "world.query.get_neighbors",
            "world.query.flood_fill",
            "world.query.raycast",
            "world.query.get_light_level",
            "world.query.get_fluid_level",
            "world.query.filter_points_by_rule",
            "world.query.get_entities_in_region",
            "world.query.get_entity"
    );

    private static final Map<String, NodeEffect> EXPECTED_EFFECTS = Map.ofEntries(
            Map.entry("world.query.is_grid_point", NodeEffect.PURE),
            Map.entry("world.query.filter_grid_points", NodeEffect.PURE),
            Map.entry("world.query.is_point_in_region", NodeEffect.PURE),
            Map.entry("world.query.filter_points_by_rule", NodeEffect.PURE),
            Map.entry("world.query.get_neighbors", NodeEffect.WORLD_READ),
            Map.entry("world.query.flood_fill", NodeEffect.WORLD_READ),
            Map.entry("world.query.raycast", NodeEffect.WORLD_READ),
            Map.entry("world.query.get_light_level", NodeEffect.WORLD_READ),
            Map.entry("world.query.get_fluid_level", NodeEffect.WORLD_READ),
            Map.entry("world.query.get_entities_in_region", NodeEffect.WORLD_READ),
            Map.entry("world.query.get_entity", NodeEffect.WORLD_READ)
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
    void currentGraphFormatIsV60() {
        assertEquals(60, GraphFormatVersion.V60);
        assertEquals(GraphFormatVersion.V60, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyElevenWorldQueryNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("world.query."))
                .sorted()
                .toList();
        assertEquals(11, ids.size(), ids.toString());
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
    }

    @Test
    void worldQueryNodesHaveUniqueOrderZeroThroughTen() {
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
        assertEquals(11, orders.length);
        for (int i = 0; i < orders.length; i++) {
            assertEquals(i, orders[i], "Expected unique order " + i);
        }
    }

    @Test
    void worldQueryNodesDeclareExpectedEffects() {
        for (Map.Entry<String, NodeEffect> entry : EXPECTED_EFFECTS.entrySet()) {
            INode node = registry.createNodeInstance(entry.getKey());
            assertNotNull(node);
            NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
            assertNotNull(info);
            assertEquals(entry.getValue(), info.effect(), entry.getKey());
        }
    }

    @Test
    void isGridPointUsesCellCenterLattice() {
        IsGridPointNode node = new IsGridPointNode();

        node.setInput("input_point", new PointData(BlockSpace.cellCenter(0, 0, 0)));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(Boolean.TRUE, node.getOutput("output_is_grid_point"));
        assertEquals(new BlockPos(0, 0, 0), node.getOutput("output_nearest_coordinate"));

        node.setInput("input_point", new PointData(0, 0, 0));
        node.processNode(null);
        assertEquals(Boolean.TRUE, node.getOutput("output_valid"));
        assertEquals(Boolean.FALSE, node.getOutput("output_is_grid_point"));
    }

    @Test
    void filterGridPointsRequiresStrictPointList() {
        FilterGridPointsNode node = new FilterGridPointsNode();
        node.setInput("input_points", List.of("not-a-point"));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void filterGridPointsHasNoSkippedCountPort() {
        FilterGridPointsNode node = new FilterGridPointsNode();
        assertFalse(hasPort(node, "output_skipped_count"));
    }

    @Test
    void filterPointsByRuleHasNoLegacyBlockOutputsOrModePort() {
        FilterPointsByRuleNode node = new FilterPointsByRuleNode();
        assertFalse(hasPort(node, "output_filtered_blocks"));
        assertFalse(hasPort(node, "output_removed_blocks"));
        assertFalse(hasPort(node, "input_mode"));
        assertTrue(hasPort(node, "output_filtered_points"));
        assertTrue(hasPort(node, "output_removed_points"));
    }

    @Test
    void filterPointsByRuleConnectedNullInvertFailsClosed() {
        FilterPointsByRuleProbe node = new FilterPointsByRuleProbe();
        node.setInput("input_points", List.of(new PointData(1, 2, 3)));
        node.connectInput("input_invert", NodeDataType.BOOLEAN);
        node.setInput("input_invert", null);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void raycastOriginAndHitPositionArePointHitNormalIsVector() {
        RaycastNode node = new RaycastNode();
        assertPortType(node, "input_origin", NodeDataType.POINT);
        assertPortType(node, "output_hit_pos", NodeDataType.POINT);
        assertPortType(node, "output_hit_normal", NodeDataType.VECTOR);
    }

    @Test
    void raycastNonFiniteMaxDistanceFailsClosed() {
        RaycastProbe node = new RaycastProbe();
        node.setInput("input_origin", new PointData(0, 0, 0));
        node.setInput("input_direction", new VectorData(0, -1, 0));
        node.connectInput("input_max_distance", NodeDataType.DOUBLE);
        node.setInput("input_max_distance", Double.NaN);
        node.processNode(ExecutionContext.createEmpty(null));
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void raycastWithoutPlayerFailsClosedForBlockHits() {
        RaycastNode node = new RaycastNode();
        node.setInput("input_origin", new PointData(0, 0, 0));
        node.setInput("input_direction", new VectorData(0, -1, 0));
        node.setInput("input_max_distance", 16.0d);
        ExecutionContext context = ExecutionContext.createEmpty(null);
        node.processNode(context);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("player"));
    }

    @Test
    void getNeighborBlocksRejectsVolumeAboveHardCap() {
        GetNeighborBlocksProbe node = new GetNeighborBlocksProbe();
        node.setInput("input_center", new BlockPos(0, 0, 0));
        node.connectInput("input_include_diagonals", NodeDataType.BOOLEAN);
        node.setInput("input_include_diagonals", true);
        node.setInput("input_radius", 32);
        node.processNode(ExecutionContext.createEmpty(null));
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).contains(String.valueOf(GenerationLimits.MAX_NEIGHBOR_QUERY_BLOCKS)));
    }

    @Test
    void getNeighborBlocksBlockIdsAreStringList() {
        GetNeighborBlocksNode node = new GetNeighborBlocksNode();
        assertPortType(node, "output_block_ids", NodeDataType.STRING_LIST);
    }

    @Test
    void floodFillRejectsMaxBlocksAboveHardCap() {
        FloodFillProbe node = new FloodFillProbe();
        node.setInput("input_seed", new BlockPos(0, 0, 0));
        node.setInput("input_max_distance", 0);
        node.setInput("input_max_blocks", GenerationLimits.MAX_FLOOD_FILL_BLOCKS + 1);
        node.processNode(ExecutionContext.createEmpty(null));
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).contains(String.valueOf(GenerationLimits.MAX_FLOOD_FILL_BLOCKS)));
    }

    @Test
    void floodFillHasCompleteOutput() {
        FloodFillNode node = new FloodFillNode();
        assertTrue(hasPort(node, "output_complete"));
    }

    @Test
    void getEntitiesInRegionUsesTypedEntityOutputs() {
        GetEntitiesInRegionNode node = new GetEntitiesInRegionNode();
        assertPortType(node, "output_entities_list", NodeDataType.MINECRAFT_ENTITY_LIST);
        assertPortType(node, "output_entity_type_ids", NodeDataType.STRING_LIST);
        assertPortType(node, "output_entity_positions", NodeDataType.POINT_LIST);
    }

    @Test
    void migrateV59ToV60DropsRemovedPortWires() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V59;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();
        graph.nodePositions = new HashMap<>();

        SavedNode ruleNode = savedNode("r1", "world.query.filter_points_by_rule");
        SavedNode gridNode = savedNode("g1", "world.query.filter_grid_points");
        graph.nodes.add(ruleNode);
        graph.nodes.add(gridNode);

        graph.connections.add(wire("r1", "output_filtered_blocks", "t1", "input_stub"));
        graph.connections.add(wire("r1", "output_removed_blocks", "t2", "input_stub"));
        graph.connections.add(wire("t3", "output_stub", "r1", "input_mode"));
        graph.connections.add(wire("g1", "output_skipped_count", "t4", "input_stub"));
        graph.connections.add(wire("r1", "output_filtered_points", "t5", "input_stub"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.V60, migrated.formatVersion);
        assertEquals(1, migrated.connections.size());
        assertEquals("output_filtered_points", migrated.connections.getFirst().sourcePortId);
    }

    @Test
    void filterPointsConnectedNullHeightFailsClosed() {
        FilterPointsByRuleProbe node = new FilterPointsByRuleProbe();
        node.setInput("input_points", List.of(new PointData(0, 10, 0)));
        node.connectInput("input_min_height", NodeDataType.DOUBLE);
        node.setInput("input_min_height", null);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).contains("Min Height"));
    }

    @Test
    void filterPointsSlopeRuleRequiresNormals() {
        FilterPointsByRuleNode node = new FilterPointsByRuleNode();
        node.setInput("input_points", List.of(new PointData(0, 10, 0)));
        node.setInput("input_min_slope", 20.0d);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("normal"));
    }

    @Test
    void filterPointsRejectsReversedBounds() {
        FilterPointsByRuleNode height = new FilterPointsByRuleNode();
        height.setInput("input_points", List.of(new PointData(0, 10, 0)));
        height.setInput("input_min_height", 20.0d);
        height.setInput("input_max_height", 10.0d);
        height.processNode(null);
        assertEquals(Boolean.FALSE, height.getOutput("output_valid"));
        assertTrue(String.valueOf(height.getOutput("output_error")).contains("Min Height"));

        FilterPointsByRuleNode slope = new FilterPointsByRuleNode();
        slope.setInput("input_points", List.of(new PointData(0, 10, 0)));
        slope.setInput("input_normals", List.of(new VectorData(0, 1, 0)));
        slope.setInput("input_min_slope", 50.0d);
        slope.setInput("input_max_slope", 10.0d);
        slope.processNode(null);
        assertEquals(Boolean.FALSE, slope.getOutput("output_valid"));
        assertTrue(String.valueOf(slope.getOutput("output_error")).contains("Min Slope"));
    }

    @Test
    void getEntitiesConnectedNullEntityTypeFailsClosed() {
        GetEntitiesInRegionProbe node = new GetEntitiesInRegionProbe();
        node.connectInput("input_entity_type", NodeDataType.ENTITY_TYPE);
        node.setInput("input_entity_type", null);
        node.processNode(ExecutionContext.createEmpty(null));
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).contains("Entity Type"));
    }

    @Test
    void neighborOverflowFailsClosed() {
        GetNeighborBlocksProbe node = new GetNeighborBlocksProbe();
        node.setInput("input_center", new BlockPos(Integer.MAX_VALUE, 0, 0));
        node.setInput("input_radius", 1);
        node.processNode(ExecutionContext.createEmpty(null));
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("overflow"));
    }

    @Test
    void floodFillCoordinateOverflowFailsClosed() {
        FloodFillProbe node = new FloodFillProbe();
        node.setInput("input_seed", new BlockPos(Integer.MAX_VALUE, 0, 0));
        node.setInput("input_max_distance", 1);
        node.setInput("input_max_blocks", 10);
        node.processNode(ExecutionContext.createEmpty(null));
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("overflow"));
    }

    @Test
    void raycastAndGetEntityRejectDistanceAboveHardCap() {
        RaycastProbe raycast = new RaycastProbe();
        raycast.setInput("input_origin", new PointData(0, 0, 0));
        raycast.setInput("input_direction", new VectorData(0, -1, 0));
        raycast.setInput("input_max_distance", GenerationLimits.MAX_WORLD_QUERY_DISTANCE + 1.0d);
        raycast.processNode(ExecutionContext.createEmpty(null));
        assertEquals(Boolean.FALSE, raycast.getOutput("output_valid"));
        assertTrue(String.valueOf(raycast.getOutput("output_error"))
                .contains(String.valueOf(GenerationLimits.MAX_WORLD_QUERY_DISTANCE)));

        GetEntityProbe getEntity = new GetEntityProbe();
        getEntity.setInput("input_max_distance", GenerationLimits.MAX_WORLD_QUERY_DISTANCE + 1.0d);
        getEntity.processNode(ExecutionContext.createEmpty(null));
        assertEquals(Boolean.FALSE, getEntity.getOutput("output_valid"));
        assertTrue(String.valueOf(getEntity.getOutput("output_error"))
                .contains(String.valueOf(GenerationLimits.MAX_WORLD_QUERY_DISTANCE)));
    }

    @Test
    void raycastRejectsEntityRadiusAboveHardCap() {
        RaycastProbe node = new RaycastProbe();
        node.setInput("input_origin", new PointData(0, 0, 0));
        node.setInput("input_direction", new VectorData(0, -1, 0));
        node.setInput("input_max_distance", 10.0d);
        node.setInput("input_entity_radius", GenerationLimits.MAX_ENTITY_QUERY_RADIUS + 1.0d);
        node.processNode(ExecutionContext.createEmpty(null));
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error"))
                .contains(String.valueOf(GenerationLimits.MAX_ENTITY_QUERY_RADIUS)));
    }

    @Test
    void getEntitiesRejectsRegionAxisAboveHardCap() {
        GetEntitiesInRegionProbe node = new GetEntitiesInRegionProbe();
        int over = GenerationLimits.MAX_ENTITY_QUERY_REGION_AXIS;
        node.setInput("input_region", new RegionData(
                new BlockPos(0, 0, 0),
                new BlockPos(over, 0, 0)
        ));
        node.processNode(ExecutionContext.createEmpty(null));
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error"))
                .contains(String.valueOf(GenerationLimits.MAX_ENTITY_QUERY_REGION_AXIS)));
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

    private static final class PortStubNode extends BaseNode {
        PortStubNode(NodeDataType outputType) {
            super(UUID.randomUUID(), "test.port_stub");
            addOutputPort(new BasePort("output_stub", "Stub", "", outputType, this));
        }

        @Override
        public void processNode(ExecutionContext context) {
        }
    }

    private static final class FilterPointsByRuleProbe extends FilterPointsByRuleNode {
        void connectInput(String portId, NodeDataType outputType) {
            WorldQueryLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class RaycastProbe extends RaycastNode {
        void connectInput(String portId, NodeDataType outputType) {
            WorldQueryLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class GetNeighborBlocksProbe extends GetNeighborBlocksNode {
        void connectInput(String portId, NodeDataType outputType) {
            WorldQueryLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class FloodFillProbe extends FloodFillNode {
    }

    private static final class GetEntitiesInRegionProbe extends GetEntitiesInRegionNode {
        void connectInput(String portId, NodeDataType outputType) {
            WorldQueryLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class GetEntityProbe extends GetEntityNode {
    }
}
