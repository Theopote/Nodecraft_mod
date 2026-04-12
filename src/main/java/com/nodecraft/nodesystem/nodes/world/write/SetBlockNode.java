package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Low-level world edit node that places one block at one position.
 */
@NodeInfo(
    id = "world.write.set_block",
    displayName = "Set Block",
    description = "Places one block at one block position",
    category = "world.write",
    order = 0
)
public class SetBlockNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String INPUT_BLOCK_INFO_ID = "input_block_info";
    private static final String INPUT_NOTIFY_ID = "input_notify";
    private static final String INPUT_SPAWN_DROPS_ID = "input_spawn_drops";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_PREVIOUS_BLOCK_ID = "output_previous_block";

    private boolean notifyUpdate = true;
    private boolean spawnDrops = false;

    public SetBlockNode() {
        super(UUID.randomUUID(), "world.write.set_block");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Target block position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_ID, "Block Info", "Block state or block id to place", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", "Whether neighbor and listener updates should fire", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", "Whether replacing a block should drop items first", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether block placement succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIOUS_BLOCK_ID, "Previous Block", "Block state that was replaced", NodeDataType.BLOCK_INFO, this));
    }

    @Override
    public String getDescription() {
        return "Places one block at one block position";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        Object previousBlock = null;

        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        Object blockInfoObj = inputValues.get(INPUT_BLOCK_INFO_ID);

        boolean notify = inputValues.get(INPUT_NOTIFY_ID) instanceof Boolean value ? value : notifyUpdate;
        boolean dropItems = inputValues.get(INPUT_SPAWN_DROPS_ID) instanceof Boolean value ? value : spawnDrops;

        if (context != null && context.getWorld() != null
                && coordinateObj instanceof BlockPos pos
                && blockInfoObj != null) {
            try {
                previousBlock = context.getWorld().getBlockState(pos);
                BlockState targetState = resolveBlockState(blockInfoObj);
                if (targetState != null) {
                    int flags = notify ? Block.NOTIFY_ALL : Block.FORCE_STATE;
                    if (dropItems && !context.getWorld().isAir(pos)) {
                        context.getWorld().breakBlock(pos, true);
                    }
                    success = context.getWorld().setBlockState(pos, targetState, flags);
                }
            } catch (Exception e) {
                success = false;
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_PREVIOUS_BLOCK_ID, previousBlock);
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

    private BlockState resolveBlockState(Object blockInfoObj) {
        if (blockInfoObj instanceof BlockState blockState) {
            return blockState;
        }
        if (blockInfoObj instanceof String blockId && !blockId.isBlank()) {
            try {
                Identifier id = Identifier.of(blockId);
                Block block = Registries.BLOCK.get(id);
                return block.getDefaultState();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
