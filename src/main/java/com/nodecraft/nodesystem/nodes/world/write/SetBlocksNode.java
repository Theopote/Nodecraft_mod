package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "world.write.set_blocks",
    displayName = "Set Blocks",
    description = "Sets blocks at explicit coordinates, with optional shared block-entity NBT",
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
    private static final String INPUT_BATCH_UPDATES_ID = "input_batch_updates";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";
    private static final String INPUT_NBT_ID = WorldWriteNbtUtils.INPUT_NBT_ID;
    private static final String INPUT_NBT_STRING_ID = WorldWriteNbtUtils.INPUT_NBT_STRING_ID;
    private static final String INPUT_MERGE_NBT_ID = WorldWriteNbtUtils.INPUT_MERGE_NBT_ID;

    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_NBT_SUCCESS_COUNT_ID = "output_nbt_success_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_ALL_SUCCESS_ID = "output_all_success";
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    private boolean notifyUpdate = true;
    private boolean spawnDrops = false;
    private boolean batchUpdates = true;
    private int maxBlocks = 32768;
    @NodeProperty(displayName = "Record Undo", category = "Execution", order = 1)
    private boolean recordUndo = true;

    public SetBlocksNode() {
        super(UUID.randomUUID(), "world.write.set_blocks");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Target block coordinates", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_ID, "Block Info", "Block state or block id to place", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_LIST_ID, "Block Info List", "Optional per-position block states or ids", NodeDataType.BLOCK_INFO_LIST, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "When connected, false prevents this write from running", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", "Whether neighbor and listener updates should fire", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", "Whether replacing blocks should drop items first", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_BATCH_UPDATES_ID, "Batch Updates", "Reserved batch-update toggle", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "Safety limit for the number of writes", NodeDataType.INTEGER, this));
        addInputPort(new BasePort(INPUT_NBT_ID, "NBT", "Optional shared block-entity NBT to apply after placement", NodeDataType.NBT_COMPOUND, this));
        addInputPort(new BasePort(INPUT_NBT_STRING_ID, "NBT String", "Optional shared SNBT string to apply after placement", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_MERGE_NBT_ID, "Merge NBT", "Merge incoming NBT with each block entity NBT instead of replacing", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", "Number of successful writes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_NBT_SUCCESS_COUNT_ID, "NBT Success Count", "Number of block entities that received optional NBT", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", "Number of attempted writes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ALL_SUCCESS_ID, "All Success", "Whether every attempted write succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why the batch did not run or the first write error", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Sets blocks at explicit coordinates, with optional shared block-entity NBT";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        int successCount = 0;
        int nbtSuccessCount = 0;
        int totalCount = 0;
        boolean allSuccess = true;
        String error = "";

        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        Object blockInfoObj = inputValues.get(INPUT_BLOCK_INFO_ID);
        Object blockInfoListObj = inputValues.get(INPUT_BLOCK_INFO_LIST_ID);
        boolean notify = inputValues.get(INPUT_NOTIFY_ID) instanceof Boolean value ? value : notifyUpdate;
        boolean dropItems = inputValues.get(INPUT_SPAWN_DROPS_ID) instanceof Boolean value ? value : spawnDrops;
        boolean batch = inputValues.get(INPUT_BATCH_UPDATES_ID) instanceof Boolean value ? value : batchUpdates;
        int blockLimit = WorldWriteUtils.resolveLimit(inputValues.get(INPUT_MAX_BLOCKS_ID), maxBlocks);

        if (!WorldWriteUtils.shouldRun(inputValues)) {
            allSuccess = false;
            error = "Not triggered";
        } else if (context == null || context.getWorld() == null) {
            allSuccess = false;
            error = "Missing execution world";
        } else if (!(coordinatesObj instanceof BlockPosList coordinates)) {
            allSuccess = false;
            error = "Invalid coordinates";
        } else if (coordinates.size() > blockLimit) {
            allSuccess = false;
            error = "Coordinate count " + coordinates.size() + " exceeds max blocks " + blockLimit;
        } else {
            List<?> blockInfoList = blockInfoListObj instanceof List<?> list ? list : null;
            BlockState singleTargetState = blockInfoList == null || blockInfoList.isEmpty()
                ? WorldWriteUtils.resolveBlockState(blockInfoObj)
                : null;

            if ((blockInfoList == null || blockInfoList.isEmpty()) && singleTargetState == null) {
                allSuccess = false;
                error = "Invalid block info";
            } else {
                int flags = WorldWriteUtils.flags(notify);
                WorldWriteHistoryService.UndoRecord undoRecord = recordUndo ? new WorldWriteHistoryService.UndoRecord() : null;
                boolean hasNbtInput = WorldWriteNbtUtils.hasNbtInput(inputValues);
                NbtCompound incomingNbt = WorldWriteNbtUtils.resolveIncomingNbt(inputValues);
                boolean mergeNbt = WorldWriteNbtUtils.mergeRequested(inputValues);
                if (hasNbtInput && incomingNbt == null) {
                    allSuccess = false;
                    error = "Invalid NBT input";
                }

                if (batch) {
                    // Reserved for future world-level batch APIs.
                }

                for (BlockPos pos : coordinates) {
                    totalCount++;
                    try {
                        BlockState targetState = singleTargetState;
                        if (targetState == null && blockInfoList != null && !blockInfoList.isEmpty()) {
                            int index = totalCount - 1;
                            Object currentInfo = blockInfoList.get(index % blockInfoList.size());
                            targetState = WorldWriteUtils.resolveBlockState(currentInfo);
                        }
                        if (targetState == null) {
                            allSuccess = false;
                            if (error.isEmpty()) {
                                error = "Invalid block info at index " + (totalCount - 1);
                            }
                            continue;
                        }

                        BlockState previousState = context.getWorld().getBlockState(pos);
                        if (dropItems && !context.getWorld().isAir(pos)) {
                            context.getWorld().breakBlock(pos, true);
                        }
                        boolean success = context.getWorld().setBlockState(pos, targetState, flags);
                        if (success) {
                            successCount++;
                            if (incomingNbt != null) {
                                boolean nbtSuccess = WorldWriteNbtUtils.applyToBlockEntity(context, pos, incomingNbt, mergeNbt, notify);
                                if (nbtSuccess) {
                                    nbtSuccessCount++;
                                } else {
                                    allSuccess = false;
                                    if (error.isEmpty()) {
                                        error = "NBT was not applied at " + pos;
                                    }
                                }
                            }
                            if (undoRecord != null) {
                                undoRecord.add(pos, previousState);
                            }
                        } else {
                            allSuccess = false;
                            if (error.isEmpty()) {
                                error = "World rejected block placement at " + pos;
                            }
                        }
                    } catch (Exception e) {
                        allSuccess = false;
                        if (error.isEmpty()) {
                            error = "Error setting block at " + pos + ": " + e.getMessage();
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

        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_NBT_SUCCESS_COUNT_ID, nbtSuccessCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_ALL_SUCCESS_ID, allSuccess);
        outputValues.put(OUTPUT_ERROR_ID, error);
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
