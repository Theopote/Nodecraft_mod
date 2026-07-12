package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
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
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_COORDINATES_ID = "output_coordinates";
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    private boolean notifyUpdate = true;
    private boolean spawnDrops = false;
    private boolean batchUpdates = true;
    private boolean excludeAir = false;
    private boolean hollow = false;
    private int maxBlocks = 32768;
    @NodeProperty(displayName = "Record Undo", category = "Execution", order = 1)
    private boolean recordUndo = true;

    public FillRegionNode() {
        super(UUID.randomUUID(), "world.write.fill_region");

        addInputPort(new BasePort(INPUT_REGION_ID, "Region", "Region to fill", NodeDataType.REGION, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_ID, "Block Info", "Block state or block id to place", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "When connected, false prevents this write from running", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_HOLLOW_ID, "Hollow", "Only fill the region shell", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_EXCLUDE_AIR_ID, "Exclude Air", "Only replace non-air blocks", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", "Whether neighbor and listener updates should fire", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", "Whether replaced blocks should drop items first", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "Safety limit for region volume", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_FILLED_BLOCKS_ID, "Filled Blocks", "Number of successful writes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_AFFECTED_BLOCKS_ID, "Affected Blocks", "Number of positions that reached write attempts", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", "Number of successful writes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", "Number of scanned positions", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_COORDINATES_ID, "Coordinates", "Coordinates that were changed", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why fill did not run or the first write error", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Fills a region with a block";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        int filledBlocks = 0;
        int affectedBlocks = 0;
        int successCount = 0;
        int totalCount = 0;
        String error = "";
        BlockPosList coordinates = new BlockPosList();

        Object regionObj = inputValues.get(INPUT_REGION_ID);
        Object blockInfoObj = inputValues.get(INPUT_BLOCK_INFO_ID);
        boolean hollowValue = inputValues.get(INPUT_HOLLOW_ID) instanceof Boolean value ? value : hollow;
        boolean excludeAirValue = inputValues.get(INPUT_EXCLUDE_AIR_ID) instanceof Boolean value ? value : excludeAir;
        boolean notify = inputValues.get(INPUT_NOTIFY_ID) instanceof Boolean value ? value : notifyUpdate;
        boolean dropItems = inputValues.get(INPUT_SPAWN_DROPS_ID) instanceof Boolean value ? value : spawnDrops;
        int blockLimit = WorldWriteUtils.resolveLimit(inputValues.get(INPUT_MAX_BLOCKS_ID), maxBlocks);

        if (!WorldWriteUtils.shouldRun(inputValues)) {
            error = "Not triggered";
        } else if (context == null || context.getWorld() == null) {
            error = "Missing execution world";
        } else if (!(regionObj instanceof RegionData region) || !region.isComplete()) {
            error = "Invalid region";
        } else {
            long volume = WorldWriteUtils.volume(region);
            if (volume > blockLimit) {
                error = "Region volume " + volume + " exceeds max blocks " + blockLimit;
            } else {
                BlockState targetState = WorldWriteUtils.resolveBlockState(blockInfoObj);
                if (targetState == null) {
                    error = "Invalid block info";
                } else {
                    BlockPos minCorner = region.getMinCorner();
                    BlockPos maxCorner = region.getMaxCorner();
                    int flags = WorldWriteUtils.flags(notify);
                    WorldWriteHistoryService.UndoRecord undoRecord = recordUndo ? new WorldWriteHistoryService.UndoRecord() : null;

                    if (batchUpdates) {
                        // Reserved for future world-level batch APIs.
                    }

                    for (BlockPos mutablePos : BlockPos.iterate(minCorner, maxCorner)) {
                        totalCount++;
                        BlockPos pos = mutablePos.toImmutable();

                        if (hollowValue && !isShell(pos, minCorner, maxCorner)) {
                            continue;
                        }
                        if (excludeAirValue && context.getWorld().isAir(pos)) {
                            continue;
                        }

                        affectedBlocks++;
                        try {
                            BlockState previousState = context.getWorld().getBlockState(pos);
                            if (dropItems && !previousState.isAir()) {
                                context.getWorld().breakBlock(pos, true);
                            }
                            boolean success = context.getWorld().setBlockState(pos, targetState, flags);
                            if (success) {
                                successCount++;
                                filledBlocks++;
                                coordinates.add(pos);
                                if (undoRecord != null) {
                                    undoRecord.add(pos, previousState);
                                }
                            } else if (error.isEmpty()) {
                                error = "World rejected block placement at " + pos;
                            }
                        } catch (Exception e) {
                            if (error.isEmpty()) {
                                error = "Error filling block at " + pos + ": " + e.getMessage();
                            }
                        }
                    }
                    if (undoRecord != null) {
                        WorldWriteHistoryService.getInstance().push(
                            WorldWriteHistoryService.resolveActorId(context.getPlayer()),
                            undoRecord
                        );
                    }
                }
            }
        }

        outputValues.put(OUTPUT_FILLED_BLOCKS_ID, filledBlocks);
        outputValues.put(OUTPUT_AFFECTED_BLOCKS_ID, affectedBlocks);
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_COORDINATES_ID, coordinates);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private static boolean isShell(BlockPos pos, BlockPos minCorner, BlockPos maxCorner) {
        return pos.getX() == minCorner.getX() || pos.getX() == maxCorner.getX()
            || pos.getY() == minCorner.getY() || pos.getY() == maxCorner.getY()
            || pos.getZ() == minCorner.getZ() || pos.getZ() == maxCorner.getZ();
    }

    public boolean isNotifyUpdate() {
        return notifyUpdate;
    }

    public void setNotifyUpdate(boolean notifyUpdate) {
        this.notifyUpdate = notifyUpdate;
        markDirty();
    }

    public boolean isSpawnDrops() {
        return spawnDrops;
    }

    public void setSpawnDrops(boolean spawnDrops) {
        this.spawnDrops = spawnDrops;
        markDirty();
    }

    public boolean isBatchUpdates() {
        return batchUpdates;
    }

    public void setBatchUpdates(boolean batchUpdates) {
        this.batchUpdates = batchUpdates;
        markDirty();
    }

    public boolean isExcludeAir() {
        return excludeAir;
    }

    public void setExcludeAir(boolean excludeAir) {
        this.excludeAir = excludeAir;
        markDirty();
    }

    public boolean isHollow() {
        return hollow;
    }

    public void setHollow(boolean hollow) {
        this.hollow = hollow;
        markDirty();
    }

    public int getMaxBlocks() {
        return maxBlocks;
    }

    public void setMaxBlocks(int maxBlocks) {
        int resolved = Math.max(1, maxBlocks);
        if (this.maxBlocks != resolved) {
            this.maxBlocks = resolved;
            markDirty();
        }
    }

    public boolean isRecordUndo() {
        return recordUndo;
    }

    public void setRecordUndo(boolean recordUndo) {
        if (this.recordUndo != recordUndo) {
            this.recordUndo = recordUndo;
            markDirty();
        }
    }
}
