package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
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
    id = "world.write.clear_region",
    displayName = "Clear Blocks",
    description = "Clears blocks at explicit coordinates by replacing them with air",
    category = "world.write",
    order = 5
)
public class RemoveBlocksNode extends BaseNode {

    private static final String INPUT_COORDINATES_ID = "input_coordinates";
    private static final String INPUT_NOTIFY_ID = "input_notify";
    private static final String INPUT_SPAWN_DROPS_ID = "input_spawn_drops";
    private static final String INPUT_MAX_BLOCKS_ID = "input_max_blocks";

    private static final String OUTPUT_REMOVED_BLOCKS_ID = "output_removed_blocks";
    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_PREVIOUS_BLOCKS_ID = "output_previous_blocks";

    private boolean notifyUpdate = true;
    private boolean spawnDrops = true;
    private int maxBlocks = 32768;

    public RemoveBlocksNode() {
        super(UUID.randomUUID(), "world.write.clear_region");

        addInputPort(new BasePort(INPUT_COORDINATES_ID, "Coordinates", "Block coordinates to clear", NodeDataType.BLOCK_LIST, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", "Whether neighbor and listener updates should fire", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", "Whether removed blocks should drop items", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_BLOCKS_ID, "Max Blocks", "Safety limit for the number of blocks to clear", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_REMOVED_BLOCKS_ID, "Removed Blocks", "Number of non-air blocks removed", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", "Number of successful operations", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", "Number of attempted operations", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_PREVIOUS_BLOCKS_ID, "Previous Blocks", "Block states that existed before clearing", NodeDataType.BLOCK_INFO_LIST, this));
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
        List<Object> previousBlocks = new ArrayList<>();

        Object coordinatesObj = inputValues.get(INPUT_COORDINATES_ID);
        boolean notify = inputValues.get(INPUT_NOTIFY_ID) instanceof Boolean value ? value : notifyUpdate;
        boolean dropItems = inputValues.get(INPUT_SPAWN_DROPS_ID) instanceof Boolean value ? value : spawnDrops;
        int blockLimit = inputValues.get(INPUT_MAX_BLOCKS_ID) instanceof Number value
            ? Math.max(1, value.intValue())
            : maxBlocks;

        if (context != null && context.getWorld() != null && coordinatesObj instanceof BlockPosList coordinates) {
            if (coordinates.size() > blockLimit) {
                outputValues.put(OUTPUT_REMOVED_BLOCKS_ID, removedBlocks);
                outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
                outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
                outputValues.put(OUTPUT_PREVIOUS_BLOCKS_ID, previousBlocks);
                return;
            }

            BlockState airState = Blocks.AIR.getDefaultState();
            int flags = notify ? Block.NOTIFY_ALL : Block.FORCE_STATE;

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
                    }
                } catch (Exception ignored) {
                    // Keep processing the remaining coordinates.
                }
            }
        }

        outputValues.put(OUTPUT_REMOVED_BLOCKS_ID, removedBlocks);
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_PREVIOUS_BLOCKS_ID, previousBlocks);
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
}
