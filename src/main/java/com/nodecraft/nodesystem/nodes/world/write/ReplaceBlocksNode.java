package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.replace_blocks",
    displayName = "Replace Blocks",
    description = "Replaces matching blocks in a region or coordinate list",
    category = "world.write",
    order = 3
)
public class ReplaceBlocksNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_TARGET_BLOCK_ID = "input_target_block";
    private static final String INPUT_REPLACEMENT_BLOCK_ID = "input_replacement_block";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_EXACT_MATCH_ID = "input_exact_match";
    private static final String INPUT_NOTIFY_ID = "input_notify";
    private static final String INPUT_SPAWN_DROPS_ID = "input_spawn_drops";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";

    private static final String OUTPUT_REPLACED_BLOCKS_ID = "output_replaced_blocks";
    private static final String OUTPUT_CHECKED_BLOCKS_ID = "output_checked_blocks";
    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_FAILURE_COUNT_ID = "output_failure_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_AFFECTED_COORDINATES_ID = "output_affected_coordinates";
    private static final String OUTPUT_HIT_LIMIT_ID = WorldWriteUtils.OUTPUT_HIT_LIMIT_ID;
    private static final String OUTPUT_COMPLETE_ID = WorldWriteUtils.OUTPUT_COMPLETE_ID;
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    private boolean notifyUpdate = true;
    private boolean spawnDrops = false;
    private boolean exactMatch = false;
    private int maxBlocks = 32768;
    @NodeProperty(displayName = "Record Undo", category = "Execution", order = 1)
    private boolean recordUndo = true;

    public ReplaceBlocksNode() {
        super(UUID.randomUUID(), "world.write.replace_blocks");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Optional region to process", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Optional coordinate list to process", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_TARGET_BLOCK_ID, "Target Block", "Block state or block id to match", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_REPLACEMENT_BLOCK_ID, "Replacement Block", "Block state or block id to place", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_EXACT_MATCH_ID, "Exact Match", "Require full block state equality", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", "Whether neighbor and listener updates should fire", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", "Whether replaced blocks should drop items first", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "User budget hard-capped by MAX_SYNC_WORLD_WRITE_BLOCKS", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_REPLACED_BLOCKS_ID, "Replaced Blocks", "Number of matching blocks replaced", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_CHECKED_BLOCKS_ID, "Checked Blocks", "Number of unique positions checked", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", "Number of successful writes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FAILURE_COUNT_ID, "Failure Count", "Number of failed writes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", "Number of unique positions checked", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_AFFECTED_COORDINATES_ID, "Affected Coordinates", "Coordinates that were changed", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "True when Max Blocks budget stopped early", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete", "False when Hit Limit or per-cell failures", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why replace did not run or the first write error", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false, "Trigger is connected but null or invalid.");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), true, false, true, "Not triggered");
            return;
        }

        Boolean exact = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_EXACT_MATCH_ID, exactMatch);
        Boolean notify = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_NOTIFY_ID, notifyUpdate);
        Boolean dropItems = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_SPAWN_DROPS_ID, spawnDrops);
        Integer blockLimit = WorldWriteUtils.resolveUserBudgetExactInteger(
            this, INPUT_MAX_BLOCKS_ID, maxBlocks, GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS);
        if (exact == null || notify == null || dropItems == null) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false, "Boolean drive is connected but null or invalid.");
            return;
        }
        if (blockLimit == null) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false,
                "Max Blocks must be an exact INTEGER between 1 and " + GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS + ".");
            return;
        }

        BlockState targetState = WorldWriteUtils.resolveBlockState(inputValues.get(INPUT_TARGET_BLOCK_ID));
        BlockState replacementState = WorldWriteUtils.resolveBlockState(inputValues.get(INPUT_REPLACEMENT_BLOCK_ID));
        if (targetState == null) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false, "Invalid target block");
            return;
        }
        if (replacementState == null) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false, "Invalid replacement block");
            return;
        }

        BlockPosList positionsToProcess = collectPositions(inputValues.get(INPUT_REGION_ID), inputValues.get(INPUT_COORDINATES_ID));
        if (positionsToProcess == null) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false, "Invalid coordinates (strict BLOCK_LIST required).");
            return;
        }
        positionsToProcess = WorldWriteUtils.dedupe(positionsToProcess);
        if (positionsToProcess.isEmpty()) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false, "No positions to process");
            return;
        }
        if (positionsToProcess.size() > blockLimit) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false,
                "Merged position count " + positionsToProcess.size() + " exceeds Max Blocks " + blockLimit + ".");
            return;
        }
        if (context == null || context.getWorld() == null) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false, "Missing execution world");
            return;
        }

        int flags = WorldWriteUtils.flags(notify);
        WorldWriteUndoJournal tx = new WorldWriteUndoJournal(WorldWriteUtils.worldKey(context.getWorld()));
        BlockPosList affectedCoordinates = new BlockPosList();
        int checkedBlocks = 0;

        for (BlockPos pos : positionsToProcess) {
            checkedBlocks++;
            try {
                if (!WorldWriteUtils.isChunkLoaded(context, pos)) {
                    tx.recordFailure();
                    continue;
                }
                BlockSnapshot before = WorldWriteUndoJournal.captureCurrent(context, pos);
                if (before == null) {
                    tx.recordFailure();
                    continue;
                }
                if (!WorldWriteUtils.matches(before.state(), targetState, exact)) {
                    continue;
                }
                if (dropItems && !before.state().isAir()) {
                    context.getWorld().breakBlock(pos, true);
                }
                boolean success = context.getWorld().setBlockState(pos, replacementState, flags);
                if (success) {
                    tx.recordSuccess(before);
                    affectedCoordinates.add(pos);
                } else {
                    tx.recordFailure();
                }
            } catch (Exception e) {
                tx.recordFailure();
            }
        }

        tx.pushIfNeeded(context, recordUndo);
        String error = tx.failureCount() > 0 ? "Partial write: " + tx.failureCount() + " failure(s)" : "";
        publish(
            tx.successCount(),
            checkedBlocks,
            tx.successCount(),
            tx.failureCount(),
            checkedBlocks,
            affectedCoordinates,
            true,
            tx.hitLimit(),
            tx.isComplete(),
            error
        );
    }

    /**
     * @return null when Coordinates is present but not a strict BLOCK_LIST.
     */
    private static @Nullable BlockPosList collectPositions(Object regionObj, Object coordinatesObj) {
        BlockPosList positions = new BlockPosList();
        if (regionObj instanceof RegionData region && region.isComplete()) {
            long volume = WorldWriteUtils.volume(region);
            if (volume < 0L) {
                return null;
            }
            for (BlockPos pos : BlockPos.iterate(region.getMinCorner(), region.getMaxCorner())) {
                positions.add(pos.toImmutable());
            }
        }
        if (coordinatesObj != null) {
            List<BlockPos> list = WorldWriteUtils.requireBlockList(coordinatesObj);
            if (list == null) {
                return null;
            }
            for (BlockPos pos : list) {
                if (pos != null) {
                    positions.add(pos.toImmutable());
                }
            }
        }
        return positions;
    }

    private void publish(
        int replacedBlocks,
        int checkedBlocks,
        int successCount,
        int failureCount,
        int totalCount,
        BlockPosList affectedCoordinates,
        boolean valid,
        boolean hitLimit,
        boolean complete,
        String error
    ) {
        outputValues.put(OUTPUT_REPLACED_BLOCKS_ID, replacedBlocks);
        outputValues.put(OUTPUT_CHECKED_BLOCKS_ID, checkedBlocks);
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_FAILURE_COUNT_ID, failureCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_AFFECTED_COORDINATES_ID, affectedCoordinates);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_COMPLETE_ID, complete);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isNotifyUpdate() { return notifyUpdate; }
    public void setNotifyUpdate(boolean notifyUpdate) { this.notifyUpdate = notifyUpdate; markDirty(); }
    public boolean isSpawnDrops() { return spawnDrops; }
    public void setSpawnDrops(boolean spawnDrops) { this.spawnDrops = spawnDrops; markDirty(); }
    public boolean isExactMatch() { return exactMatch; }
    public void setExactMatch(boolean exactMatch) { this.exactMatch = exactMatch; markDirty(); }
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
