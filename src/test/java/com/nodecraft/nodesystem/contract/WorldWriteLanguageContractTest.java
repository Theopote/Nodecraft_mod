package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.nodes.world.write.SetBlockNode;
import com.nodecraft.nodesystem.nodes.world.write.SetBlocksNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * World Write v1 language fence (Graph V64).
 */
class WorldWriteLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "world.write.set_block",
            "world.write.set_blocks",
            "world.write.fill_region",
            "world.write.replace_blocks",
            "world.write.clone_region",
            "world.write.remove_blocks",
            "world.write.set_block_nbt",
            "world.write.spawn_entity",
            "world.write.entity_teleport",
            "world.write.remove_entities",
            "world.write.write_sign_text",
            "world.write.apply_redstone_power",
            "world.write.simulate_right_click",
            "world.write.execute_command",
            "world.write.undo_last_write",
            "world.write.redo_last_write",
            "world.write.peek_last_undo",
            "world.write.clear_undo_history"
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
    void generationLimitsExposeWorldWriteCaps() {
        assertEquals(262_144, GenerationLimits.MAX_WORLD_WRITE_BLOCKS);
        assertEquals(32_768, GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS);
        assertEquals(262_144, GenerationLimits.MAX_UNDO_TOTAL_BLOCKS_PER_ACTOR);
        assertEquals(4_096, GenerationLimits.MAX_WORLD_WRITE_ENTITIES);
        assertEquals(65_536, GenerationLimits.MAX_WORLD_WRITE_SNBT_CHARS);
        assertEquals(1_024, GenerationLimits.MAX_WORLD_WRITE_COMMAND_CHARS);
    }

    @Test
    void exactlyEighteenWorldWriteNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("world.write."))
                .sorted()
                .toList();
        assertEquals(18, ids.size(), ids.toString());
        assertEquals(CANONICAL_IDS, Set.copyOf(ids));
    }

    @Test
    void worldWriteOrdersAreZeroThroughSeventeen() {
        for (String id : CANONICAL_IDS) {
            INode node = registry.createNodeInstance(id);
            assertNotNull(node, id);
            NodeInfo annotation = node.getClass().getAnnotation(NodeInfo.class);
            assertNotNull(annotation, id);
            assertTrue(annotation.order() >= 0 && annotation.order() <= 17, id + " order=" + annotation.order());
        }
        assertEquals(0, registry.createNodeInstance("world.write.set_block")
                .getClass().getAnnotation(NodeInfo.class).order());
        assertEquals(17, registry.createNodeInstance("world.write.clear_undo_history")
                .getClass().getAnnotation(NodeInfo.class).order());
    }

    @Test
    void effectsMatchWorldWriteContract() {
        assertEquals(NodeEffect.CONTEXT_READ, NodeEffectResolver.resolve(
                registry.createNodeInstance("world.write.peek_last_undo").getClass(),
                "world.write.peek_last_undo"));
        assertEquals(NodeEffect.CONTEXT_WRITE, NodeEffectResolver.resolve(
                registry.createNodeInstance("world.write.clear_undo_history").getClass(),
                "world.write.clear_undo_history"));
        assertEquals(NodeEffect.WORLD_WRITE, NodeEffectResolver.resolve(
                registry.createNodeInstance("world.write.set_block").getClass(),
                "world.write.set_block"));
        assertEquals(NodeEffect.WORLD_WRITE, NodeEffectResolver.resolve(
                registry.createNodeInstance("world.write.entity_teleport").getClass(),
                "world.write.entity_teleport"));
    }

    @Test
    void requireBlockPosRejectsPointAndVector() {
        SetBlockProbe node = new SetBlockProbe();
        node.setTrigger(true);
        node.setInput("input_coordinate", new PointData(0.5, 64.5, 0.5));
        node.setInput("input_block_info", "minecraft:stone");
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("block_pos"));

        node = new SetBlockProbe();
        node.setTrigger(true);
        node.setInput("input_coordinate", new Vector3d(1, 2, 3));
        node.setInput("input_block_info", "minecraft:stone");
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
    }

    @Test
    void triggerConnectedNullFailsClosed() {
        SetBlockProbe node = new SetBlockProbe();
        node.setTrigger(true);
        node.connectInput("input_trigger", NodeDataType.BOOLEAN);
        node.setInput("input_trigger", null);
        node.setInput("input_coordinate", new BlockPos(0, 64, 0));
        node.setInput("input_block_info", "minecraft:stone");
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(Boolean.FALSE, node.getOutput("output_success"));
    }

    @Test
    void maxBlocksZeroFailsClosed() {
        SetBlocksProbe node = new SetBlocksProbe();
        node.setTrigger(true);
        node.connectInput("input_max_blocks", NodeDataType.INTEGER);
        node.setInput("input_max_blocks", 0);
        node.setInput("input_coordinates", List.of(new BlockPos(0, 64, 0)));
        node.setInput("input_block_info", "minecraft:stone");
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).contains("Max Blocks"));
    }

    @Test
    void blockInfoListLengthMismatchFailsClosed() {
        SetBlocksProbe node = new SetBlocksProbe();
        node.setTrigger(true);
        node.setInput("input_coordinates", List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)));
        node.setInput("input_block_info_list", List.of("minecraft:stone"));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("size"));
    }

    @Test
    void setBlockTriggerDefaultsFalseWithoutV63Wording() throws Exception {
        SetBlockNode node = new SetBlockNode();
        assertFalse(node.isTrigger());
        String src = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/com/nodecraft/nodesystem/nodes/world/write/SetBlockNode.java"));
        assertFalse(src.contains("V63"));
        String fill = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/com/nodecraft/nodesystem/nodes/world/write/FillRegionNode.java"));
        assertFalse(fill.contains("V63"));
    }

    @Test
    void pointAndBatchWritersAdvertiseLoadedOnlyError() throws Exception {
        List<String> files = List.of(
            "SetBlockNode.java",
            "SetBlockNbtNode.java",
            "WriteSignTextNode.java",
            "SpawnEntityNode.java",
            "ApplyRedstonePowerNode.java",
            "SimulateRightClickNode.java",
            "ExecuteCommandNode.java",
            "SetBlocksNode.java",
            "FillRegionNode.java",
            "ReplaceBlocksNode.java",
            "CloneRegionNode.java",
            "RemoveBlocksNode.java"
        );
        for (String file : files) {
            String src = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/nodecraft/nodesystem/nodes/world/write/" + file));
            assertTrue(src.contains("WorldWriteUtils.UNLOADED_CHUNK_ERROR")
                    || src.contains("WorldWriteUtils.isChunkLoaded"),
                file + " must gate on loaded chunks");
        }
        String utils = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/com/nodecraft/nodesystem/nodes/world/write/WorldWriteUtils.java"));
        assertTrue(utils.contains("Target chunk is not loaded"));
    }

    @Test
    void teleportAndRemoveAreNotSimulatedStubs() throws Exception {
        String teleportSrc = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/nodecraft/nodesystem/nodes/world/write/EntityTeleportNode.java"));
        String removeSrc = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/nodecraft/nodesystem/nodes/world/write/RemoveEntitiesNode.java"));
        assertFalse(teleportSrc.contains("模拟成功传送"));
        assertFalse(removeSrc.contains("模拟成功移除"));
        assertTrue(teleportSrc.contains("requestTeleport") || teleportSrc.contains("refreshPositionAndAngles"));
        assertTrue(removeSrc.contains("discard()"));
    }

    @Test
    void typedEntityListPorts() {
        INode teleport = registry.createNodeInstance("world.write.entity_teleport");
        INode remove = registry.createNodeInstance("world.write.remove_entities");
        assertNotNull(teleport);
        assertNotNull(remove);
        assertPortType(teleport, "output_teleported_entities", NodeDataType.MINECRAFT_ENTITY_LIST);
        assertPortType(remove, "output_failed_entities", NodeDataType.MINECRAFT_ENTITY_LIST);
        assertFalse(hasPort(teleport, "input_dimension"));
        assertFalse(hasPort(remove, "input_entity_uuid"));
        assertFalse(hasPort(remove, "input_entity_type"));
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

    private static final class SetBlockProbe extends SetBlockNode {
        void connectInput(String portId, NodeDataType outputType) {
            WorldWriteLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static final class SetBlocksProbe extends SetBlocksNode {
        void connectInput(String portId, NodeDataType outputType) {
            WorldWriteLanguageContractTest.connectInput(this, portId, outputType);
        }
    }

    private static void assertPortType(INode node, String portId, NodeDataType expected) {
        IPort port = findPort(node, portId);
        assertNotNull(port, portId);
        assertEquals(expected, port.getDataType(), portId);
    }

    private static boolean hasPort(INode node, String portId) {
        return findPort(node, portId) != null;
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
}
