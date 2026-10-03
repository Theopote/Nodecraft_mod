package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.bake.BakeTask;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.remove_blocks",
    displayName = "Clear Blocks",
    description = "Clears blocks at explicit coordinates by replacing them with air",
    category = "world.write",
    order = 5
)
public class RemoveBlocksNode extends BaseNode {

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_NOTIFY_ID = "input_notify";
    private static final String INPUT_SPAWN_DROPS_ID = "input_spawn_drops";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";

    private static final String OUTPUT_REMOVED_BLOCKS_ID = "output_removed_blocks";
    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_FAILURE_COUNT_ID = "output_failure_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_PREVIOUS_BLOCKS_ID = "output_previous_blocks";
    private static final String OUTPUT_HIT_LIMIT_ID = WorldWriteUtils.OUTPUT_HIT_LIMIT_ID;
    private static final String OUTPUT_COMPLETE_ID = WorldWriteUtils.OUTPUT_COMPLETE_ID;
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    private boolean notifyUpdate = true;
    private boolean spawnDrops = true;
    private int maxBlocks = 32768;
    @NodeProperty(displayName = "Record Undo", category = "Execution", order = 1)
    private boolean recordUndo = true;

    public RemoveBlocksNode() {
        super(UUID.randomUUID(), "world.write.remove_blocks");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinates to clear", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", "Whether neighbor and listener updates should fire", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", "Whether removed blocks should drop items", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "User budget hard-capped by MAX_SYNC_WORLD_WRITE_BLOCKS", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_REMOVED_BLOCKS_ID, "Removed Blocks", "Number of non-air blocks removed", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", "Number of successful operations", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FAILURE_COUNT_ID, "Failure Count", "Number of failed operations", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", "Number of attempted operations", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PREVIOUS_BLOCKS_ID, "Previous Blocks", "Block states that existed before clearing", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "True when Max Blocks budget stopped early", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete", "False when Hit Limit or per-cell failures", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why clearing did not run or the first write error", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Clears blocks at explicit coordinates by replacing them with air";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(0, 0, 0, 0, List.of(), false, false, false, "Trigger is connected but null or invalid.");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(0, 0, 0, 0, List.of(), true, false, true, "Not triggered");
            return;
        }

        Boolean notify = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_NOTIFY_ID, notifyUpdate);
        Boolean dropItems = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_SPAWN_DROPS_ID, spawnDrops);
        Integer blockLimit = WorldWriteUtils.resolveUserBudgetExactInteger(
            this, INPUT_MAX_BLOCKS_ID, maxBlocks, GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS);
        if (notify == null || dropItems == null) {
            publish(0, 0, 0, 0, List.of(), false, false, false, "Boolean drive is connected but null or invalid.");
            return;
        }
        if (blockLimit == null) {
            publish(0, 0, 0, 0, List.of(), false, false, false,
                "Max Blocks must be an exact INTEGER between 1 and " + GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS + ".");
            return;
        }

        List<BlockPos> coordinates = WorldWriteUtils.requireBlockList(inputValues.get(INPUT_COORDINATES_ID));
        if (coordinates == null) {
            publish(0, 0, 0, 0, List.of(), false, false, false, "Invalid coordinates (strict BLOCK_LIST required).");
            return;
        }
        if (coordinates.size() > blockLimit) {
            publish(0, 0, 0, 0, List.of(), false, false, false,
                "Coordinate count " + coordinates.size() + " exceeds Max Blocks " + blockLimit + ".");
            return;
        }
        if (context == null || context.getWorld() == null) {
            publish(0, 0, 0, 0, List.of(), false, false, false, "Missing execution world");
            return;
        }

        List<BakeTask.Placement> placements = new ArrayList<>();
        List<Object> previousBlocks = new ArrayList<>();
        int skippedUnloaded = 0;
        int totalCount = coordinates.size();
        BlockState airState = Blocks.AIR.getDefaultState();

        for (BlockPos pos : coordinates) {
            if (!WorldWriteUtils.isChunkLoaded(context, pos)) {
                skippedUnloaded++;
                continue;
            }
            BlockState current = context.getWorld().getBlockState(pos);
            previousBlocks.add(current);
            if (current.isAir()) {
                continue;
            }
            placements.add(WorldWriteBakeBridge.placement(pos, airState));
        }

        WorldWriteBakeBridge.Outcome outcome = WorldWriteBakeBridge.enqueueAndAwait(
            context, placements, recordUndo, skippedUnloaded);
        publish(
            outcome.successCount(),
            outcome.successCount() + (totalCount - skippedUnloaded - placements.size()),
            outcome.failureCount(),
            totalCount,
            previousBlocks,
            true,
            false,
            outcome.complete(),
            outcome.error()
        );
    }

    private void publish(
        int removedBlocks,
        int successCount,
        int failureCount,
        int totalCount,
        List<Object> previousBlocks,
        boolean valid,
        boolean hitLimit,
        boolean complete,
        String error
    ) {
        outputValues.put(OUTPUT_REMOVED_BLOCKS_ID, removedBlocks);
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_FAILURE_COUNT_ID, failureCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_PREVIOUS_BLOCKS_ID, previousBlocks);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_COMPLETE_ID, complete);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isNotifyUpdate() { return notifyUpdate; }
    public void setNotifyUpdate(boolean notifyUpdate) { this.notifyUpdate = notifyUpdate; markDirty(); }
    public boolean isSpawnDrops() { return spawnDrops; }
    public void setSpawnDrops(boolean spawnDrops) { this.spawnDrops = spawnDrops; markDirty(); }
    public int getMaxBlocks() { return maxBlocks; }
    public void setMaxBlocks(int maxBlocks) {
        this.maxBlocks = Math.max(1, Math.min(maxBlocks, GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS));
        markDirty();
    }
    public boolean isRecordUndo() { return recordUndo; }
    public void setRecordUndo(boolean recordUndo) { this.recordUndo = recordUndo; markDirty(); }
    public boolean isTrigger() { return trigger; }
    public void setTrigger(boolean trigger) { this.trigger = trigger; markDirty(); }
}
