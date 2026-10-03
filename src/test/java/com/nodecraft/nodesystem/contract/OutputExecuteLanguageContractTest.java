package com.nodecraft.nodesystem.contract;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.bake.BakeTaskState;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.runtime.NodeEffectResolver;
import com.nodecraft.nodesystem.nodes.output.execute.ApplyChangesNode;
import com.nodecraft.nodesystem.nodes.output.execute.BakeStatusNode;
import com.nodecraft.nodesystem.nodes.output.execute.CancelBakeNode;
import com.nodecraft.nodesystem.nodes.output.execute.MergeBlockPlacementsNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import com.nodecraft.nodesystem.util.BlockPlacementData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputExecuteLanguageContractTest {

    private static final Set<String> CANONICAL_IDS = Set.of(
            "output.execute.apply_changes",
            "output.execute.bake_status",
            "output.execute.undo_last_bake",
            "output.execute.redo_last_bake",
            "output.execute.merge_block_placements",
            "output.execute.sdf_to_blocks",
            "output.execute.bake_surface_strip_to_blocks",
            "output.execute.clear_preview",
            "output.execute.cancel_bake"
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
    void generationLimitsExposeExecuteCaps() {
        assertEquals(262_144, GenerationLimits.MAX_BLOCK_PLACEMENTS);
        assertEquals(GenerationLimits.MAX_WORLD_WRITE_BLOCKS, GenerationLimits.MAX_BLOCK_PLACEMENTS);
        assertEquals(16_384, GenerationLimits.MAX_BLOCKS_PER_TICK);
        assertEquals(50, GenerationLimits.MAX_TICK_BUDGET_MS);
        assertEquals(600, GenerationLimits.MAX_EXECUTION_TIMEOUT_SECONDS);
    }

    @Test
    void exactlyNineOutputExecuteNodesRegistered() {
        List<String> ids = registry.getAllNodeIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith("output.execute."))
                .sorted()
                .toList();
        assertEquals(CANONICAL_IDS, Set.copyOf(ids), ids.toString());
    }

    @Test
    void bakeStatusIsContextReadWithoutTrigger() {
        INode node = registry.createNodeInstance("output.execute.bake_status");
        assertNotNull(node);
        NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
        assertEquals(NodeEffect.CONTEXT_READ, info.effect());
        assertEquals(NodeEffect.CONTEXT_READ,
                NodeEffectResolver.resolve(BakeStatusNode.class, "output.execute.bake_status"));
        assertEquals(NodeEffect.CONTEXT_READ,
                NodeEffectResolver.inferFromTypeId("output.execute.bake_status"));
        assertFalse(hasPort(node, "input_trigger"));
        assertNotNull(findPort(node, "output_valid"));
        assertNotNull(findPort(node, "output_error"));

        BakeStatusNode status = new BakeStatusNode();
        status.processNode(null);
        assertEquals(Boolean.TRUE, status.getOutput("output_valid"));
        assertEquals(Boolean.FALSE, status.getOutput("output_found"));
        assertEquals("Idle", status.getOutput("output_state"));

        status.setInput("input_task_id", "not-a-uuid");
        status.processNode(null);
        assertEquals(Boolean.FALSE, status.getOutput("output_valid"));
        assertEquals("Invalid Task ID", status.getOutput("output_error"));
    }

    @Test
    void cancelBakeIsWorldWriteWithExecTrigger() {
        INode node = registry.createNodeInstance("output.execute.cancel_bake");
        assertNotNull(node);
        NodeInfo info = node.getClass().getAnnotation(NodeInfo.class);
        assertEquals(NodeEffect.WORLD_WRITE, info.effect());
        IPort trigger = findPort(node, "input_trigger");
        assertNotNull(trigger);
        assertEquals(NodeDataType.EXEC, trigger.getDataType());
        assertFalse(trigger.isRequired());
        assertEquals(NodeDataType.STRING, findPort(node, "input_task_id").getDataType());
        assertNotNull(findPort(node, "output_valid"));
        assertNotNull(findPort(node, "output_error"));
        assertNotNull(findPort(new CancelBakeNode(), "output_accepted"));
    }

    @Test
    void mergeTypedListFailsClosedAndHonorsBudgetConstant() throws Exception {
        MergeBlockPlacementsNode node = new MergeBlockPlacementsNode();
        connectInput(node, "input_placements_0", NodeDataType.BLOCK_PLACEMENT_LIST);
        node.setInput("input_placements_0", List.of("not-a-placement"));
        node.processNode(null);
        assertEquals(Boolean.FALSE, node.getOutput("output_valid"));
        assertEquals(0, node.getOutput("output_count"));
        assertTrue(String.valueOf(node.getOutput("output_error")).toLowerCase(Locale.ROOT).contains("placement"));

        MergeBlockPlacementsNode ok = new MergeBlockPlacementsNode();
        connectInput(ok, "input_placements_0", NodeDataType.BLOCK_PLACEMENT_LIST);
        ok.setInput("input_placements_0", List.of(new BlockPlacementData(new BlockPos(0, 64, 0), "minecraft:stone")));
        ok.processNode(null);
        assertEquals(Boolean.TRUE, ok.getOutput("output_valid"));
        assertEquals(1, ok.getOutput("output_count"));
        assertEquals(1, ok.getOutput("output_input_count"));

        String src = Files.readString(Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/output/execute/MergeBlockPlacementsNode.java"));
        assertTrue(src.contains("MAX_BLOCK_PLACEMENTS"));
        assertTrue(src.contains("output_valid"));
    }

    @Test
    void applyChangesUsesStrictNotifyDriveAndSetterCaps() throws Exception {
        String src = Files.readString(Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/output/execute/ApplyChangesNode.java"));
        assertFalse(src.contains("coerceBoolean"));
        assertTrue(src.contains("OptionalPortDrive.resolveOptionalBoolean"));
        assertTrue(src.contains("MAX_BLOCKS_PER_TICK"));
        assertTrue(src.contains("MAX_TICK_BUDGET_MS"));
        assertTrue(src.contains("MAX_EXECUTION_TIMEOUT_SECONDS"));

        ApplyChangesNode node = new ApplyChangesNode();
        node.setBlocksPerTick(1_000_000);
        assertEquals(GenerationLimits.MAX_BLOCKS_PER_TICK, node.getBlocksPerTick());
        node.setTickBudgetMillis(10_000);
        assertEquals(GenerationLimits.MAX_TICK_BUDGET_MS, node.getTickBudgetMillis());
        node.setExecutionTimeout(10_000);
        assertEquals(GenerationLimits.MAX_EXECUTION_TIMEOUT_SECONDS, node.getExecutionTimeout());
    }

    @Test
    void taskSnapshotCarriesActorAndWorldKey() {
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        BakePlacementService.TaskSnapshot snapshot = new BakePlacementService.TaskSnapshot(
            taskId,
            actorId,
            "minecraft:overworld",
            0,
            0,
            10,
            10,
            0.0d,
            BakeTaskState.QUEUED,
            0,
            0,
            0
        );
        assertEquals(actorId, snapshot.actorId());
        assertEquals("minecraft:overworld", snapshot.worldKey());
        assertEquals("Queued", snapshot.resolveState());
    }

    @Test
    void undoRedoDisplayNamesAreLastChange() {
        INode undo = registry.createNodeInstance("output.execute.undo_last_bake");
        INode redo = registry.createNodeInstance("output.execute.redo_last_bake");
        assertEquals("Undo Last Change", undo.getClass().getAnnotation(NodeInfo.class).displayName());
        assertEquals("Redo Last Change", redo.getClass().getAnnotation(NodeInfo.class).displayName());
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
