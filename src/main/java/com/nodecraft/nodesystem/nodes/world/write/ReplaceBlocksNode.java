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
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_AFFECTED_COORDINATES_ID = "output_affected_coordinates";
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    private boolean notifyUpdate = true;
    private boolean spawnDrops = false;
    private boolean batchUpdates = true;
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
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "When connected, false prevents this write from running", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_EXACT_MATCH_ID, "Exact Match", "Require full block state equality", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", "Whether neighbor and listener updates should fire", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", "Whether replaced blocks should drop items first", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "Safety limit after merging region and coordinate inputs", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_REPLACED_BLOCKS_ID, "Replaced Blocks", "Number of matching blocks replaced", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_CHECKED_BLOCKS_ID, "Checked Blocks", "Number of unique positions checked", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", "Number of successful writes", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", "Number of unique positions checked", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_AFFECTED_COORDINATES_ID, "Affected Coordinates", "Coordinates that were changed", NodeDataType.BLOCK_LIST, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why replace did not run or the first write error", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Replaces matching blocks in a region or coordinate list";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        int replacedBlocks = 0;
        int checkedBlocks = 0;
        int successCount = 0;
        int totalCount = 0;
        String error = "";
        BlockPosList affectedCoordinates = new BlockPosList();

        boolean exact = inputValues.get(INPUT_EXACT_MATCH_ID) instanceof Boolean value ? value : exactMatch;
        boolean notify = inputValues.get(INPUT_NOTIFY_ID) instanceof Boolean value ? value : notifyUpdate;
        boolean dropItems = inputValues.get(INPUT_SPAWN_DROPS_ID) instanceof Boolean value ? value : spawnDrops;
        int blockLimit = WorldWriteUtils.resolveLimit(inputValues.get(INPUT_MAX_BLOCKS_ID), maxBlocks);

        if (!WorldWriteUtils.shouldRun(inputValues)) {
            error = "Not triggered";
        } else if (context == null || context.getWorld() == null) {
            error = "Missing execution world";
        } else {
            BlockState targetState = WorldWriteUtils.resolveBlockState(inputValues.get(INPUT_TARGET_BLOCK_ID));
            BlockState replacementState = WorldWriteUtils.resolveBlockState(inputValues.get(INPUT_REPLACEMENT_BLOCK_ID));
            BlockPosList positionsToProcess = collectPositions(inputValues.get(INPUT_REGION_ID), inputValues.get(INPUT_COORDINATES_ID));
            positionsToProcess = WorldWriteUtils.dedupe(positionsToProcess);

            if (targetState == null) {
                error = "Invalid target block";
            } else if (replacementState == null) {
                error = "Invalid replacement block";
            } else if (positionsToProcess.isEmpty()) {
                error = "No positions to process";
            } else if (positionsToProcess.size() > blockLimit) {
                error = "Merged position count " + positionsToProcess.size() + " exceeds max blocks " + blockLimit;
            } else {
                int flags = WorldWriteUtils.flags(notify);
                WorldWriteHistoryService.UndoRecord undoRecord = recordUndo ? new WorldWriteHistoryService.UndoRecord() : null;

                if (batchUpdates) {
                    // Reserved for future world-level batch APIs.
                }

                for (BlockPos pos : positionsToProcess) {
                    checkedBlocks++;
                    totalCount++;
                    try {
                        BlockState currentState = context.getWorld().getBlockState(pos);
                        if (!WorldWriteUtils.matches(currentState, targetState, exact)) {
                            continue;
                        }
                        if (dropItems && !currentState.isAir()) {
                            context.getWorld().breakBlock(pos, true);
                        }
                        boolean success = context.getWorld().setBlockState(pos, replacementState, flags);
                        if (success) {
                            successCount++;
                            replacedBlocks++;
                            affectedCoordinates.add(pos);
                            if (undoRecord != null) {
                                undoRecord.add(pos, currentState);
                            }
                        } else if (error.isEmpty()) {
                            error = "World rejected block placement at " + pos;
                        }
                    } catch (Exception e) {
                        if (error.isEmpty()) {
                            error = "Error replacing block at " + pos + ": " + e.getMessage();
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

        outputValues.put(OUTPUT_REPLACED_BLOCKS_ID, replacedBlocks);
        outputValues.put(OUTPUT_CHECKED_BLOCKS_ID, checkedBlocks);
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_AFFECTED_COORDINATES_ID, affectedCoordinates);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }

    private static BlockPosList collectPositions(Object regionObj, Object coordinatesObj) {
        BlockPosList positions = new BlockPosList();
        if (regionObj instanceof RegionData region && region.isComplete()) {
            for (BlockPos pos : BlockPos.iterate(region.getMinCorner(), region.getMaxCorner())) {
                positions.add(pos.toImmutable());
            }
        }
        if (coordinatesObj instanceof BlockPosList coordinates) {
            for (BlockPos pos : coordinates) {
                if (pos != null) {
                    positions.add(pos.toImmutable());
                }
            }
        }
        return positions;
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

    public boolean isExactMatch() {
        return exactMatch;
    }

    public void setExactMatch(boolean exactMatch) {
        this.exactMatch = exactMatch;
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
