package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.BlockPosList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Low-level world edit node that clears blocks at explicit coordinates.
 */
@NodeInfo(
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
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_PREVIOUS_BLOCKS_ID = "output_previous_blocks";
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    private boolean notifyUpdate = true;
    private boolean spawnDrops = true;
    private int maxBlocks = 32768;
    @NodeProperty(displayName = "Record Undo", category = "Execution", order = 1)
    private boolean recordUndo = true;

    public RemoveBlocksNode() {
        super(UUID.randomUUID(), "world.write.remove_blocks");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinates to clear", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "When connected, false prevents this write from running", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", "Whether neighbor and listener updates should fire", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", "Whether removed blocks should drop items", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "Safety limit for the number of blocks to clear", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_REMOVED_BLOCKS_ID, "Removed Blocks", "Number of non-air blocks removed", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", "Number of successful operations", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", "Number of attempted operations", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PREVIOUS_BLOCKS_ID, "Previous Blocks", "Block states that existed before clearing", NodeDataType.BLOCK_INFO_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why clearing did not run or the first write error", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Clears blocks at explicit coordinates by replacing them with air";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        int removedBlocks = 0;
        int successCount = 0;
        int totalCount = 0;
        String error = "";
        List<Object> previousBlocks = new ArrayList<>();

        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        boolean notify = inputValues.get(INPUT_NOTIFY_ID) instanceof Boolean value ? value : notifyUpdate;
        boolean dropItems = inputValues.get(INPUT_SPAWN_DROPS_ID) instanceof Boolean value ? value : spawnDrops;
        int blockLimit = inputValues.get(INPUT_MAX_BLOCKS_ID) instanceof Number value
            ? Math.max(1, value.intValue())
            : maxBlocks;

        if (!WorldWriteUtils.shouldRun(inputValues)) {
            error = "Not triggered";
        } else if (context == null || context.getWorld() == null) {
            error = "Missing execution world";
        } else if (!(coordinatesObj instanceof BlockPosList coordinates)) {
            error = "Invalid coordinates";
        } else if (coordinates.size() > blockLimit) {
            error = "Coordinate count " + coordinates.size() + " exceeds max blocks " + blockLimit;
        } else {

            BlockState airState = Blocks.AIR.getDefaultState();
            int flags = WorldWriteUtils.flags(notify);
            WorldWriteHistoryService.UndoRecord undoRecord = recordUndo ? new WorldWriteHistoryService.UndoRecord() : null;

            for (BlockPos pos : coordinates) {
                totalCount++;
                try {
                    BlockState currentState = context.getWorld().getBlockState(pos);
                    previousBlocks.add(currentState);

                    if (currentState.isAir()) {
                        successCount++;
                        continue;
                    }

                    boolean success;
                    if (dropItems) {
                        success = context.getWorld().breakBlock(pos, true);
                    } else {
                        success = context.getWorld().setBlockState(pos, airState, flags);
                    }

                    if (success) {
                        successCount++;
                        removedBlocks++;
                        if (undoRecord != null) {
                            undoRecord.add(pos, currentState);
                        }
                    }
                } catch (Exception e) {
                    if (error.isEmpty()) {
                        error = "Error clearing block at " + pos + ": " + e.getMessage();
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

        outputValues.put(OUTPUT_REMOVED_BLOCKS_ID, removedBlocks);
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_PREVIOUS_BLOCKS_ID, previousBlocks);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    public boolean isNotifyUpdate() {
        return notifyUpdate;
    }

    public void setNotifyUpdate(boolean notifyUpdate) {
        if (this.notifyUpdate != notifyUpdate) {
            this.notifyUpdate = notifyUpdate;
            markDirty();
        }
    }

    public boolean isSpawnDrops() {
        return spawnDrops;
    }

    public void setSpawnDrops(boolean spawnDrops) {
        if (this.spawnDrops != spawnDrops) {
            this.spawnDrops = spawnDrops;
            markDirty();
        }
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
