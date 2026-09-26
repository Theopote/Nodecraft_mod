package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.GraphMigrationRegistry;
import com.nodecraft.nodesystem.io.GraphFormatVersion;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.nodes.utilities.morphology.BlockListMorphologyNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Block Morphology v1 language fence (Graph V57).
 */
class BlockMorphologyLanguageContractTest {

    private static final String TYPE_ID = "utilities.morphology.block_list_morphology";

    private static NodeRegistry registry;

    @BeforeAll
    static void init() {
        registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            registry.initialize();
        }
    }

    @Test
    void currentGraphFormatIsV57() {
        assertEquals(57, GraphFormatVersion.V57);
        assertEquals(GraphFormatVersion.V57, GraphFormatVersion.CURRENT);
    }

    @Test
    void exactlyOneMorphologyNodeRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("utilities.morphology."))
                .sorted()
                .toList();
        assertEquals(1, ids.size(), ids.toString());
        assertEquals(TYPE_ID, ids.getFirst());
    }

    @Test
    void morphologyNodeHasOrderZeroAndDisplayName() {
        INode node = registry.createNodeInstance(TYPE_ID);
        assertNotNull(node);
        NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
        assertNotNull(info);
        assertEquals(0, info.order());
        assertEquals("Block Morphology", info.displayName());
        assertEquals(NodeEffect.PURE, info.effect());
    }

    @Test
    void acceptsBlockPosListAndPlainList() {
        BlockListMorphologyNode node = new BlockListMorphologyNode();
        node.setOperation(BlockListMorphologyNode.MorphOp.DILATE);
        node.setConnectivity(BlockListMorphologyNode.Connectivity.SIX);
        node.setIterations(1);

        BlockPosList blockPosList = new BlockPosList();
        blockPosList.add(new BlockPos(0, 0, 0));
        Map<String, Object> fromBlockPosList = node.compute(Map.of("input_blocks", blockPosList));
        assertTrue((Boolean) fromBlockPosList.get("output_valid"));
        assertEquals(7, fromBlockPosList.get("output_output_count"));

        Map<String, Object> fromList = node.compute(Map.of(
                "input_blocks",
                List.of(new BlockPos(0, 0, 0))
        ));
        assertTrue((Boolean) fromList.get("output_valid"));
        assertEquals(7, fromList.get("output_output_count"));
    }

    @Test
    void nullMemberFailsWholeInput() {
        BlockListMorphologyNode node = new BlockListMorphologyNode();
        List<BlockPos> bad = new ArrayList<>();
        bad.add(new BlockPos(0, 0, 0));
        bad.add(null);

        Map<String, Object> outputs = node.compute(Map.of("input_blocks", bad));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(0, outputs.get("output_delta_count"));
    }

    @Test
    void emptyInputIsValidEmptyOutput() {
        BlockListMorphologyNode node = new BlockListMorphologyNode();
        Map<String, Object> outputs = node.compute(Map.of("input_blocks", new BlockPosList()));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(0, outputs.get("output_input_count"));
        assertEquals(0, outputs.get("output_output_count"));
        assertEquals(0, outputs.get("output_delta_count"));
        assertInstanceOf(BlockPosList.class, outputs.get("output_blocks"));
        assertTrue(((BlockPosList) outputs.get("output_blocks")).isEmpty());
    }

    @Test
    void erodeSingleVoxelProducesValidEmptySet() {
        BlockListMorphologyNode node = new BlockListMorphologyNode();
        node.setOperation(BlockListMorphologyNode.MorphOp.ERODE);
        node.setIterations(1);

        Map<String, Object> outputs = node.compute(Map.of(
                "input_blocks",
                List.of(new BlockPos(0, 0, 0))
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(1, outputs.get("output_input_count"));
        assertEquals(0, outputs.get("output_output_count"));
        assertEquals(-1, outputs.get("output_delta_count"));
    }

    @Test
    void iterationsRequireExactIntegerInRange() {
        BlockPosList blocks = new BlockPosList();
        blocks.add(new BlockPos(0, 0, 0));

        MorphologyProbe truncated = new MorphologyProbe();
        truncated.putRawInput("input_blocks", blocks);
        connectInput(truncated, "input_iterations", NodeDataType.INTEGER);
        truncated.putRawInput("input_iterations", 3.8d);
        truncated.processNode(null);
        assertFalse((Boolean) truncated.getOutput("output_valid"));

        MorphologyProbe zero = new MorphologyProbe();
        zero.putRawInput("input_blocks", blocks);
        connectInput(zero, "input_iterations", NodeDataType.INTEGER);
        zero.putRawInput("input_iterations", 0);
        zero.processNode(null);
        assertFalse((Boolean) zero.getOutput("output_valid"));

        MorphologyProbe tooMany = new MorphologyProbe();
        tooMany.putRawInput("input_blocks", blocks);
        connectInput(tooMany, "input_iterations", NodeDataType.INTEGER);
        tooMany.putRawInput("input_iterations", GenerationLimits.MAX_MORPHOLOGY_ITERATIONS + 1);
        tooMany.processNode(null);
        assertFalse((Boolean) tooMany.getOutput("output_valid"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void inputExceedingBlockCapFailsClosed() {
        BlockListMorphologyNode node = new BlockListMorphologyNode();
        BlockPosList blocks = new BlockPosList();
        for (int i = 0; i < GenerationLimits.MAX_MORPHOLOGY_BLOCKS + 1; i++) {
            blocks.add(new BlockPos(i, 0, 0));
        }

        Map<String, Object> outputs = node.compute(Map.of("input_blocks", blocks));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(0, outputs.get("output_output_count"));
        assertEquals(0, outputs.get("output_delta_count"));
        assertTrue(((BlockPosList) outputs.get("output_blocks")).isEmpty());
    }

    @Test
    void dilationOverflowFailsClosedWithoutPartialOutput() {
        BlockListMorphologyNode node = new BlockListMorphologyNode();
        node.setOperation(BlockListMorphologyNode.MorphOp.DILATE);
        node.setConnectivity(BlockListMorphologyNode.Connectivity.TWENTY_SIX);
        node.setIterations(GenerationLimits.MAX_MORPHOLOGY_ITERATIONS);

        Map<String, Object> outputs = node.compute(Map.of(
                "input_blocks",
                List.of(new BlockPos(0, 0, 0))
        ));
        assertFalse((Boolean) outputs.get("output_valid"));
        assertEquals(0, outputs.get("output_output_count"));
        assertEquals(0, outputs.get("output_delta_count"));
        assertTrue(((BlockPosList) outputs.get("output_blocks")).isEmpty());
    }

    @Test
    void sixNeighborDilateExpandsToExpectedShell() {
        BlockListMorphologyNode node = new BlockListMorphologyNode();
        node.setOperation(BlockListMorphologyNode.MorphOp.DILATE);
        node.setConnectivity(BlockListMorphologyNode.Connectivity.SIX);
        node.setIterations(1);

        Map<String, Object> outputs = node.compute(Map.of(
                "input_blocks",
                List.of(new BlockPos(0, 0, 0))
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        BlockPosList result = assertInstanceOf(BlockPosList.class, outputs.get("output_blocks"));
        assertEquals(7, result.size());
        assertTrue(result.contains(new BlockPos(0, 0, 0)));
        assertTrue(result.contains(new BlockPos(1, 0, 0)));
        assertTrue(result.contains(new BlockPos(-1, 0, 0)));
    }

    @Test
    void twentySixNeighborDilateExpandsToThreeByThreeCube() {
        BlockListMorphologyNode node = new BlockListMorphologyNode();
        node.setOperation(BlockListMorphologyNode.MorphOp.DILATE);
        node.setConnectivity(BlockListMorphologyNode.Connectivity.TWENTY_SIX);
        node.setIterations(1);

        Map<String, Object> outputs = node.compute(Map.of(
                "input_blocks",
                List.of(new BlockPos(0, 0, 0))
        ));
        assertTrue((Boolean) outputs.get("output_valid"));
        assertEquals(27, outputs.get("output_output_count"));
    }

    @Test
    void noMaxOutputBlocksPortOrStoppedReasonPort() {
        BlockListMorphologyNode node = new BlockListMorphologyNode();
        Set<String> inputIds = node.getInputPorts().stream().map(IPort::getId).collect(java.util.stream.Collectors.toSet());
        Set<String> outputIds = node.getOutputPorts().stream().map(IPort::getId).collect(java.util.stream.Collectors.toSet());
        assertFalse(inputIds.contains("input_max_output_blocks"));
        assertFalse(outputIds.contains("output_stopped_reason"));
        assertTrue(outputIds.contains("output_error"));
    }

    @Test
    void migrateV56ToV57StripsStateDropsMaxPortAndRemapsErrorPort() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.V56;
        graph.nodes = new ArrayList<>();
        graph.connections = new ArrayList<>();

        SavedNode morphology = savedNode("m1", TYPE_ID);
        Map<String, Object> state = new HashMap<>();
        state.put("maxOutputBlocks", 999);
        state.put("iterations", 2);
        morphology.state = state;
        graph.nodes.add(morphology);

        SavedNode sink = savedNode("s1", "reference.vectors.vector");
        graph.nodes.add(sink);

        graph.connections.add(wire("m1", "output_stopped_reason", "s1", "input_x"));
        graph.connections.add(wire("s1", "output_x", "m1", "input_max_output_blocks"));

        SavedGraph migrated = GraphMigrationRegistry.migrateToCurrent(graph);
        assertEquals(GraphFormatVersion.CURRENT, migrated.formatVersion);

        @SuppressWarnings("unchecked")
        Map<String, Object> migratedState = (Map<String, Object>) nodeOf(migrated, "m1").state;
        assertFalse(migratedState.containsKey("maxOutputBlocks"));
        assertEquals(1, migrated.connections.size());
        assertEquals("output_error", migrated.connections.getFirst().sourcePortId);
    }

    private static SavedNode savedNode(String id, String typeId) {
        SavedNode node = new SavedNode();
        node.nodeId = id;
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

    private static void connectInput(BaseNode target, String inputPortId, NodeDataType outputType) {
        PortStubNode stub = new PortStubNode(outputType);
        BasePort output = (BasePort) stub.getOutputPorts().getFirst();
        BasePort input = (BasePort) target.getInputPorts().stream()
                .filter(port -> inputPortId.equals(port.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(target.getTypeId() + " missing port " + inputPortId));
        assertTrue(output.connectTo(input), inputPortId + " connect failed");
    }

    private static final class MorphologyProbe extends BlockListMorphologyNode {
        void putRawInput(String portId, Object value) {
            inputValues.put(portId, value);
        }
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
}
