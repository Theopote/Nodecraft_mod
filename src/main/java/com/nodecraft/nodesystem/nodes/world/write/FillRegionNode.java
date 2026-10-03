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

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.fill_region",
    displayName = "Fill Region",
    description = "Fills a region with a block",
    category = "world.write",
    order = 2
)
public class FillRegionNode extends BaseNode {

    private static final String INPUT_REGION_ID = "input_region";
    private static final String INPUT_BLOCK_INFO_ID = "input_block_info";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_HOLLOW_ID = "input_hollow";
    private static final String INPUT_EXCLUDE_AIR_ID = "input_exclude_air";
    private static final String INPUT_NOTIFY_ID = "input_notify";
    private static final String INPUT_SPAWN_DROPS_ID = "input_spawn_drops";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";

    private static final String OUTPUT_FILLED_BLOCKS_ID = "output_filled_blocks";
    private static final String OUTPUT_AFFECTED_BLOCKS_ID = "output_affected_blocks";
    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_FAILURE_COUNT_ID = "output_failure_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";
    private static final String OUTPUT_HIT_LIMIT_ID = WorldWriteUtils.OUTPUT_HIT_LIMIT_ID;
    private static final String OUTPUT_COMPLETE_ID = WorldWriteUtils.OUTPUT_COMPLETE_ID;
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0,
        description = "When true (or Trigger port true), this write may run. Default false.")
    private boolean trigger = false;

    private boolean notifyUpdate = true;
    private boolean spawnDrops = false;
    private boolean excludeAir = false;
    private boolean hollow = false;
    private int maxBlocks = 32768;
    @NodeProperty(displayName = "Record Undo", category = "Execution", order = 1)
    private boolean recordUndo = true;

    public FillRegionNode() {
        super(UUID.randomUUID(), "world.write.fill_region");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region to fill", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_ID, "Block Info", "Block state or block id to place", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger",
            "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_HOLLOW_ID, "Hollow", "Only fill the region shell", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_EXCLUDE_AIR_ID, "Exclude Air", "Only replace non-air blocks", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", "Whether neighbor and listener updates should fire", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", "Whether replaced blocks should drop items first", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "User budget hard-capped by MAX_SYNC_WORLD_WRITE_BLOCKS", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_FILLED_BLOCKS_ID, "Filled Blocks", "Number of successful writes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_AFFECTED_BLOCKS_ID, "Affected Blocks", "Number of positions that reached write attempts", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", "Number of successful writes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FAILURE_COUNT_ID, "Failure Count", "Number of failed writes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", "Number of scanned positions", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", "Coordinates that were changed", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "True when Max Blocks budget stopped early", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete", "False when Hit Limit or per-cell failures", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why fill did not run or the first write error", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Fills a region with a block";
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

        Boolean hollowValue = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_HOLLOW_ID, hollow);
        Boolean excludeAirValue = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_EXCLUDE_AIR_ID, excludeAir);
        Boolean notify = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_NOTIFY_ID, notifyUpdate);
        Boolean dropItems = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_SPAWN_DROPS_ID, spawnDrops);
        Integer blockLimit = WorldWriteUtils.resolveUserBudgetExactInteger(
            this, INPUT_MAX_BLOCKS_ID, maxBlocks, GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS);
        if (hollowValue == null || excludeAirValue == null || notify == null || dropItems == null) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false, "Boolean drive is connected but null or invalid.");
            return;
        }
        if (blockLimit == null) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false,
                "Max Blocks must be an exact INTEGER between 1 and " + GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS + ".");
            return;
        }

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        if (!(regionObj instanceof RegionData region) || !region.isComplete()) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false, "Invalid region");
            return;
        }

        long volume = WorldWriteUtils.volume(region);
        if (volume < 0L) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false, "Region volume overflow");
            return;
        }
        if (volume > blockLimit) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false,
                "Region volume " + volume + " exceeds Max Blocks " + blockLimit + ".");
            return;
        }

        BlockState targetState = WorldWriteUtils.resolveBlockState(inputValues.get(INPUT_BLOCK_INFO_ID));
        if (targetState == null) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false, "Invalid block info");
            return;
        }
        if (context == null || context.getWorld() == null) {
            publish(0, 0, 0, 0, 0, new BlockPosList(), false, false, false, "Missing execution world");
            return;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        int flags = WorldWriteUtils.flags(notify);
        WorldWriteUndoJournal tx = new WorldWriteUndoJournal(WorldWriteUtils.worldKey(context.getWorld()));
        BlockPosList coordinates = new BlockPosList();
        int affectedBlocks = 0;
        int totalCount = 0;

        for (BlockPos mutablePos : BlockPos.iterate(minCorner, maxCorner)) {
            totalCount++;
            BlockPos pos = mutablePos.toImmutable();
            if (hollowValue && !isShell(pos, minCorner, maxCorner)) {
                continue;
            }
            if (!WorldWriteUtils.isChunkLoaded(context, pos)) {
                affectedBlocks++;
                tx.recordFailure();
                continue;
            }
            if (excludeAirValue && context.getWorld().isAir(pos)) {
                continue;
            }

            affectedBlocks++;
            try {
                BlockSnapshot before = WorldWriteUndoJournal.captureCurrent(context, pos);
                if (before == null) {
                    tx.recordFailure();
                    continue;
                }
                if (dropItems && !before.state().isAir()) {
                    context.getWorld().breakBlock(pos, true);
                }
                boolean success = context.getWorld().setBlockState(pos, targetState, flags);
                if (success) {
                    tx.recordSuccess(before);
                    coordinates.add(pos);
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
            affectedBlocks,
            tx.successCount(),
            tx.failureCount(),
            totalCount,
            coordinates,
            true,
            tx.hitLimit(),
            tx.isComplete(),
            error
        );
    }

    private void publish(
        int filledBlocks,
        int affectedBlocks,
        int successCount,
        int failureCount,
        int totalCount,
        BlockPosList coordinates,
        boolean valid,
        boolean hitLimit,
        boolean complete,
        String error
    ) {
        outputValues.put(OUTPUT_FILLED_BLOCKS_ID, filledBlocks);
        outputValues.put(OUTPUT_AFFECTED_BLOCKS_ID, affectedBlocks);
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_FAILURE_COUNT_ID, failureCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_COORDINATES_ID, coordinates);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_COMPLETE_ID, complete);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private static boolean isShell(BlockPos pos, BlockPos minCorner, BlockPos maxCorner) {
        return pos.getX() == minCorner.getX() || pos.getX() == maxCorner.getX()
            || pos.getY() == minCorner.getY() || pos.getY() == maxCorner.getY()
            || pos.getZ() == minCorner.getZ() || pos.getZ() == maxCorner.getZ();
    }

    public boolean isNotifyUpdate() { return notifyUpdate; }
    public void setNotifyUpdate(boolean notifyUpdate) { this.notifyUpdate = notifyUpdate; markDirty(); }
    public boolean isSpawnDrops() { return spawnDrops; }
    public void setSpawnDrops(boolean spawnDrops) { this.spawnDrops = spawnDrops; markDirty(); }
    public boolean isExcludeAir() { return excludeAir; }
    public void setExcludeAir(boolean excludeAir) { this.excludeAir = excludeAir; markDirty(); }
    public boolean isHollow() { return hollow; }
    public void setHollow(boolean hollow) { this.hollow = hollow; markDirty(); }
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
