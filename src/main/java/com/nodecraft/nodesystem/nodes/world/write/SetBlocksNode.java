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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.set_blocks",
    displayName = "Set Blocks",
    description = "Sets blocks at explicit coordinates, with optional shared block-entity NBT. "
        + "Block Info List must match Coordinates size (no cyclic reuse).",
    category = "world.write",
    order = 1
)
public class SetBlocksNode extends BaseNode {

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_BLOCK_INFO_ID = "input_block_info";
    private static final String INPUT_BLOCK_INFO_LIST_ID = "input_block_info_list";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_NOTIFY_ID = "input_notify";
    private static final String INPUT_SPAWN_DROPS_ID = "input_spawn_drops";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";
    private static final String INPUT_NBT_ID = WorldWriteNbtUtils.INPUT_NBT_ID;
    private static final String INPUT_NBT_STRING_ID = WorldWriteNbtUtils.INPUT_NBT_STRING_ID;
    private static final String INPUT_MERGE_NBT_ID = WorldWriteNbtUtils.INPUT_MERGE_NBT_ID;

    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_FAILURE_COUNT_ID = "output_failure_count";
    private static final String OUTPUT_NBT_SUCCESS_COUNT_ID = "output_nbt_success_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_ALL_SUCCESS_ID = "output_all_success";
    private static final String OUTPUT_HIT_LIMIT_ID = WorldWriteUtils.OUTPUT_HIT_LIMIT_ID;
    private static final String OUTPUT_COMPLETE_ID = WorldWriteUtils.OUTPUT_COMPLETE_ID;
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    private boolean notifyUpdate = true;
    private boolean spawnDrops = false;
    private boolean mergeNbtProperty = false;
    private int maxBlocks = 32768;
    @NodeProperty(displayName = "Record Undo", category = "Execution", order = 1)
    private boolean recordUndo = true;

    public SetBlocksNode() {
        super(UUID.randomUUID(), "world.write.set_blocks");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Target block coordinates", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_ID, "Block Info", "Shared block when Block Info List is empty", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_LIST_ID, "Block Info List",
            "Per-position block states; size must equal Coordinates size", NodeDataType.BLOCK_INFO_LIST, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", "Whether neighbor and listener updates should fire", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", "Whether replacing blocks should drop items first", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "User budget hard-capped by MAX_SYNC_WORLD_WRITE_BLOCKS", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_NBT_ID, "NBT", "Optional shared block-entity NBT", NodeDataType.NBT_COMPOUND, this));
        addInputPort(new BasePort(INPUT_NBT_STRING_ID, "NBT String", "Optional shared SNBT string", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_MERGE_NBT_ID, "Merge NBT", "Merge incoming NBT with each block entity NBT", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", "Number of successful writes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FAILURE_COUNT_ID, "Failure Count", "Number of failed writes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_NBT_SUCCESS_COUNT_ID, "NBT Success Count", "Block entities that received NBT", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", "Number of attempted writes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ALL_SUCCESS_ID, "All Success", "Whether every attempted write succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "True when Max Blocks budget stopped early", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete", "False when Hit Limit or per-cell failures", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why the batch did not run or first write error", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(0, 0, 0, 0, false, false, false, false, "Trigger is connected but null or invalid.");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(0, 0, 0, 0, true, false, true, true, "Not triggered");
            return;
        }

        Boolean notify = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_NOTIFY_ID, notifyUpdate);
        Boolean dropItems = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_SPAWN_DROPS_ID, spawnDrops);
        Boolean mergeNbt = WorldWriteNbtUtils.resolveMergeNbt(this, mergeNbtProperty);
        Integer blockLimit = WorldWriteUtils.resolveUserBudgetExactInteger(
            this, INPUT_MAX_BLOCKS_ID, maxBlocks, GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS);
        if (notify == null || dropItems == null || mergeNbt == null) {
            publish(0, 0, 0, 0, false, false, false, false, "Boolean drive is connected but null or invalid.");
            return;
        }
        if (blockLimit == null) {
            publish(0, 0, 0, 0, false, false, false, false,
                "Max Blocks must be an exact INTEGER between 1 and " + GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS + ".");
            return;
        }

        List<BlockPos> coordinates = WorldWriteUtils.requireBlockList(inputValues.get(INPUT_COORDINATES_ID));
        if (coordinates == null) {
            publish(0, 0, 0, 0, false, false, false, false, "Invalid coordinates (strict BLOCK_LIST required).");
            return;
        }
        if (coordinates.size() > blockLimit) {
            publish(0, 0, 0, 0, false, false, false, false,
                "Coordinate count " + coordinates.size() + " exceeds Max Blocks " + blockLimit + ".");
            return;
        }

        Object listRaw = inputValues.get(INPUT_BLOCK_INFO_LIST_ID);
        List<BlockState> perPosStates = null;
        if (listRaw != null) {
            if (!(listRaw instanceof Collection<?> listCollection)) {
                publish(0, 0, 0, 0, false, false, false, false, "Invalid Block Info List.");
                return;
            }
            if (!listCollection.isEmpty() && listCollection.size() != coordinates.size()) {
                publish(0, 0, 0, 0, false, false, false, false,
                    "Block Info List size (" + listCollection.size() + ") must equal Coordinates size ("
                        + coordinates.size() + ").");
                return;
            }
            perPosStates = BlockInfoListUtils.resolveStrictOrderedBlockInfoList(listRaw);
            if (perPosStates == null) {
                publish(0, 0, 0, 0, false, false, false, false, "Invalid Block Info List.");
                return;
            }
        }

        BlockState sharedState = null;
        if (perPosStates == null || perPosStates.isEmpty()) {
            sharedState = WorldWriteUtils.resolveBlockState(inputValues.get(INPUT_BLOCK_INFO_ID));
            if (sharedState == null) {
                publish(0, 0, 0, 0, false, false, false, false, "Invalid block info");
                return;
            }
        }

        WorldWriteNbtUtils.NbtResolveResult nbtResult = WorldWriteNbtUtils.resolveIncomingNbt(this);
        if (nbtResult.failed()) {
            publish(0, 0, 0, 0, false, false, false, false, nbtResult.error());
            return;
        }

        if (context == null || context.getWorld() == null) {
            publish(0, 0, 0, 0, false, false, false, false, "Missing execution world");
            return;
        }

        List<BakeTask.Placement> placements = new ArrayList<>();
        int skippedUnloaded = 0;
        NbtCompound incomingNbt = nbtResult.nbt();
        for (int i = 0; i < coordinates.size(); i++) {
            BlockPos pos = coordinates.get(i);
            BlockState targetState = sharedState != null ? sharedState : perPosStates.get(i);
            if (!WorldWriteUtils.isChunkLoaded(context, pos)) {
                skippedUnloaded++;
                continue;
            }
            placements.add(WorldWriteBakeBridge.placement(pos, targetState, incomingNbt, mergeNbt));
        }

        WorldWriteBakeBridge.Outcome outcome = WorldWriteBakeBridge.enqueueAndAwait(
            context, placements, recordUndo, skippedUnloaded);
        int nbtSuccessCount = incomingNbt != null && outcome.failureCount() == 0 ? outcome.successCount() : 0;
        publish(
            outcome.successCount(),
            outcome.failureCount(),
            nbtSuccessCount,
            outcome.totalCount(),
            true,
            false,
            outcome.complete(),
            outcome.failureCount() == 0,
            outcome.error()
        );
    }

    private void publish(
        int successCount,
        int failureCount,
        int nbtSuccessCount,
        int totalCount,
        boolean valid,
        boolean hitLimit,
        boolean complete,
        boolean allSuccess,
        String error
    ) {
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_FAILURE_COUNT_ID, failureCount);
        outputValues.put(OUTPUT_NBT_SUCCESS_COUNT_ID, nbtSuccessCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_ALL_SUCCESS_ID, allSuccess);
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
