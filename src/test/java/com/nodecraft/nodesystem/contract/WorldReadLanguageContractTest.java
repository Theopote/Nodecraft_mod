package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.world.read.FindBlocksNode;
import com.nodecraft.nodesystem.nodes.world.read.GetBiomeNode;
import com.nodecraft.nodesystem.nodes.world.read.GetBlockNbtNode;
import com.nodecraft.nodesystem.nodes.world.read.GetBlockNode;
import com.nodecraft.nodesystem.nodes.world.read.GetBlockPositionsInRegionNode;
import com.nodecraft.nodesystem.nodes.world.read.GetBlocksInRegionNode;
import com.nodecraft.nodesystem.nodes.world.read.GetEntityNbtNode;
import com.nodecraft.nodesystem.nodes.world.read.GetHeightmapNode;
import com.nodecraft.nodesystem.nodes.world.read.GetSurfaceBlocksNode;
import com.nodecraft.nodesystem.nodes.world.read.ReadSignTextNode;
import com.nodecraft.nodesystem.nodes.world.read.ScanRegionByTypeNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * World Read v1 language fence (Graph V61).
 */
class WorldReadLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "world.read.get_block",
            "world.read.get_blocks_in_region",
            "world.read.find_blocks",
            "world.read.get_biome",
            "world.read.get_block_positions_in_region",
            "world.read.get_heightmap",
            "world.read.get_surface_blocks",
            "world.read.scan_region_by_type",
            "world.read.get_block_nbt",
            "world.read.get_entity_nbt",
            "world.read.read_sign_text"
    );

    private static final Map<String, NodeEffect> EXPECTED_EFFECTS = Map.ofEntries(
            Map.entry("world.read.get_block", NodeEffect.WORLD_READ),
            Map.entry("world.read.get_blocks_in_region", NodeEffect.WORLD_READ),
            Map.entry("world.read.find_blocks", NodeEffect.WORLD_READ),
            Map.entry("world.read.get_biome", NodeEffect.WORLD_READ),
            Map.entry("world.read.get_block_positions_in_region", NodeEffect.PURE),
            Map.entry("world.read.get_heightmap", NodeEffect.WORLD_READ),
            Map.entry("world.read.get_surface_blocks", NodeEffect.WORLD_READ),
            Map.entry("world.read.scan_region_by_type", NodeEffect.WORLD_READ),
            Map.entry("world.read.get_block_nbt", NodeEffect.WORLD_READ),
            Map.entry("world.read.get_entity_nbt", NodeEffect.WORLD_READ),
            Map.entry("world.read.read_sign_text", NodeEffect.WORLD_READ)
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
    void currentGraphFormatIsV61() {
        assertEquals(61, GraphFormatVersion.V61);
        assertEquals(GraphFormatVersion.V61, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyElevenWorldReadNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("world.read."))
                .sorted()
                .toList();
        assertEquals(11, ids.size(), ids.toString());
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
        assertFalse(ids.contains("world.read.biome_at_player"));
        assertFalse(ids.contains("world.read.get_points_in_region"));
    }

    @Test
    void worldReadNodesHaveUniqueOrderZeroThroughTen() {
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
    void worldReadNodesDeclareExpectedEffects() {
        for (Map.Entry<String, NodeEffect> entry : EXPECTED_EFFECTS.entrySet()) {
            INode node = registry.createNodeInstance(entry.getKey());
            assertNotNull(node);
            NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
            assertNotNull(info);
            assertEquals(entry.getValue(), info.effect(), entry.getKey());
        }
    }

    @Test
    void requireBlockPosRejectsVector() {
        GetBlockNode node = new GetBlockNode();
        node.setInput("input_coordinate", new VectorData(0.5, 0.5, 0.5));
        node.processNode(ExecutionContext.createEmpty(null));
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void hardCapsFailClosedOnBlockPositionsBudget() {
        GetBlockPositionsInRegionNode node = new GetBlockPositionsInRegionNode();
        node.setInput("input_region", new RegionData(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1)));
        node.setInput("input_max_points", GenerationLimits.MAX_WORLD_READ_BLOCKS + 1);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void volumeOverflowFailsClosedOnBlockPositions() {
        GetBlockPositionsInRegionNode node = new GetBlockPositionsInRegionNode();
        node.setInput("input_region", new RegionData(
                new BlockPos(0, 0, 0),
                new BlockPos(Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2)
        ));
        node.setInput("input_max_points", 16);
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("overflow"));
    }

    @Test
    void scannersExposeCompleteAndTypedLists() {
        GetBlocksInRegionNode blocks = new GetBlocksInRegionNode();
        assertTrue(hasPort(blocks, "output_complete"));
        assertPortType(blocks, "output_blocks_list", NodeDataType.BLOCK_INFO_LIST);

        FindBlocksNode find = new FindBlocksNode();
        assertTrue(hasPort(find, "output_complete"));

        GetHeightmapNode heightmap = new GetHeightmapNode();
        assertTrue(hasPort(heightmap, "output_complete"));
        assertPortType(heightmap, "output_height_values", NodeDataType.INTEGER_LIST);

        GetSurfaceBlocksNode surface = new GetSurfaceBlocksNode();
        assertTrue(hasPort(surface, "output_complete"));
        assertPortType(surface, "output_surface_blocks", NodeDataType.BLOCK_INFO_LIST);
        assertPortType(surface, "output_block_types", NodeDataType.STRING_LIST);

        ScanRegionByTypeNode scan = new ScanRegionByTypeNode();
        assertTrue(hasPort(scan, "output_complete"));
        assertPortType(scan, "input_target_block", NodeDataType.BLOCK_TYPE);
        assertPortType(scan, "output_block_type_ids", NodeDataType.STRING_LIST);
        assertPortType(scan, "output_counts", NodeDataType.INTEGER_LIST);
        assertFalse(hasPort(scan, "output_entries"));
        assertFalse(hasPort(scan, "output_type_counts"));
    }

    @Test
    void getEntityNbtIsEntityOnly() {
        GetEntityNbtNode node = new GetEntityNbtNode();
        assertPortType(node, "input_entity", NodeDataType.MINECRAFT_ENTITY);
        assertFalse(hasPort(node, "input_uuid"));
        assertFalse(hasPort(node, "input_entity_type"));
        assertFalse(hasPort(node, "input_find_nearest"));
        assertFalse(hasPort(node, "input_max_distance"));
        assertFalse(hasPort(node, "output_distance"));
        assertTrue(hasPort(node, "output_valid"));
        assertTrue(hasPort(node, "output_error"));
    }

    @Test
    void getBiomeDropsHeuristicPorts() {
        GetBiomeNode node = new GetBiomeNode();
        assertFalse(hasPort(node, "output_is_ocean"));
        assertFalse(hasPort(node, "output_downfall"));
        assertTrue(hasPort(node, "output_biome_temperature"));
    }

    @Test
    void getBlockNbtAndSignUseValidNotSuccess() {
        GetBlockNbtNode nbt = new GetBlockNbtNode();
        assertTrue(hasPort(nbt, "output_valid"));
        assertFalse(hasPort(nbt, "output_success"));

        ReadSignTextNode sign = new ReadSignTextNode();
        assertTrue(hasPort(sign, "output_valid"));
        assertFalse(hasPort(sign, "output_success"));
        assertFalse(hasPort(sign, "input_include_formatting"));
        assertPortType(sign, "output_text_lines", NodeDataType.STRING_LIST);
    }

    @Test
    void getBlockPositionsIsPureAndRenamed() {
        GetBlockPositionsInRegionNode node = new GetBlockPositionsInRegionNode();
        NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
        assertNotNull(info);
        assertEquals(NodeEffect.PURE, info.effect());
        assertEquals("world.read.get_block_positions_in_region", info.id());
        assertTrue(hasPort(node, "output_complete"));
    }

    @Test
    void migrateV60ToV61RenamesDropsAndRemapsPorts() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V60;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();
        graph.nodePositions = new HashMap<>();

        SavedNode points = savedNode("p1", "world.read.get_points_in_region");
        SavedNode biomePlayer = savedNode("b1", "world.read.biome_at_player");
        SavedNode scan = savedNode("s1", "world.read.scan_region_by_type");
        SavedNode entityNbt = savedNode("e1", "world.read.get_entity_nbt");
        SavedNode blockNbt = savedNode("n1", "world.read.get_block_nbt");
        SavedNode sign = savedNode("g1", "world.read.read_sign_text");
        SavedNode biome = savedNode("m1", "world.read.get_biome");
        graph.nodes.addAll(List.of(points, biomePlayer, scan, entityNbt, blockNbt, sign, biome));
        graph.nodePositions.put("b1", null);

        graph.connections.add(wire("s1", "output_entries", "t1", "input_stub"));
        graph.connections.add(wire("s1", "output_type_counts", "t2", "input_stub"));
        graph.connections.add(wire("s1", "output_block_type_ids", "t3", "input_stub"));
        graph.connections.add(wire("t4", "output_stub", "e1", "input_uuid"));
        graph.connections.add(wire("e1", "output_distance", "t5", "input_stub"));
        graph.connections.add(wire("n1", "output_success", "t6", "input_stub"));
        graph.connections.add(wire("g1", "output_success", "t7", "input_stub"));
        graph.connections.add(wire("t8", "output_stub", "g1", "input_include_formatting"));
        graph.connections.add(wire("m1", "output_is_ocean", "t9", "input_stub"));
        graph.connections.add(wire("b1", "output_biome", "t10", "input_stub"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.V61, migrated.formatVersion);

        assertEquals("world.read.get_block_positions_in_region",
                migrated.nodes.stream().filter(n -> "p1".equals(n.nodeId)).findFirst().orElseThrow().typeId);
        assertTrue(migrated.nodes.stream().noneMatch(n -> "b1".equals(n.nodeId)));

        List<String> keptSources = migrated.connections.stream()
                .map(c -> c.sourceNodeId + ":" + c.sourcePortId)
                .sorted()
                .toList();
        assertEquals(List.of(
                "g1:output_valid",
                "n1:output_valid",
                "s1:output_block_type_ids"
        ), keptSources);
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
}
