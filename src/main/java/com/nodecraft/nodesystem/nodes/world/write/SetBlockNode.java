package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Low-level world edit node that places one block at one position.
 */
@NodeInfo(
    id = "world.write.set_block",
    displayName = "Set Block",
    description = "Places one block at one block position, with optional block-entity NBT",
    category = "world.write",
    order = 0
)
public class SetBlockNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String INPUT_BLOCK_INFO_ID = "input_block_info";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_NOTIFY_ID = "input_notify";
    private static final String INPUT_SPAWN_DROPS_ID = "input_spawn_drops";
    private static final String INPUT_NBT_ID = WorldWriteNbtUtils.INPUT_NBT_ID;
    private static final String INPUT_NBT_STRING_ID = WorldWriteNbtUtils.INPUT_NBT_STRING_ID;
    private static final String INPUT_MERGE_NBT_ID = WorldWriteNbtUtils.INPUT_MERGE_NBT_ID;

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_NBT_SUCCESS_ID = "output_nbt_success";
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;
    private static final String OUTPUT_PREVIOUS_BLOCK_ID = "output_previous_block";

    private boolean notifyUpdate = true;
    private boolean spawnDrops = false;
    @NodeProperty(displayName = "Record Undo", category = "Execution", order = 1)
    private boolean recordUndo = true;

    public SetBlockNode() {
        super(UUID.randomUUID(), "world.write.set_block");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Target block position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_ID, "Block Info", "Block state or block id to place", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "When connected, false prevents this write from running", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", "Whether neighbor and listener updates should fire", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", "Whether replacing a block should drop items first", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NBT_ID, "NBT", "Optional block-entity NBT to apply after placement", NodeDataType.NBT_COMPOUND, this));
        addInputPort(new BasePort(INPUT_NBT_STRING_ID, "NBT String", "Optional SNBT string to apply after placement", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_MERGE_NBT_ID, "Merge NBT", "Merge incoming NBT with block entity NBT instead of replacing", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether block placement succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NBT_SUCCESS_ID, "NBT Success", "Whether optional block-entity NBT was applied", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why placement did not run or failed", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_PREVIOUS_BLOCK_ID, "Previous Block", "Block state that was replaced", NodeDataType.BLOCK_INFO, this));
    }

    @Override
    public String getDescription() {
        return "Places one block at one block position, with optional block-entity NBT";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        boolean nbtSuccess = false;
        String error = "";
        Object previousBlock = null;

        Object coordinateObj = inputValues.get(INPUT_COORDINATE_ID);
        Object blockInfoObj = inputValues.get(INPUT_BLOCK_INFO_ID);

        boolean notify = inputValues.get(INPUT_NOTIFY_ID) instanceof Boolean value ? value : notifyUpdate;
        boolean dropItems = inputValues.get(INPUT_SPAWN_DROPS_ID) instanceof Boolean value ? value : spawnDrops;

        BlockPos pos = WorldWriteUtils.resolveBlockPos(coordinateObj);
        BlockState targetState = WorldWriteUtils.resolveBlockState(blockInfoObj);

        if (!WorldWriteUtils.shouldRun(inputValues)) {
            error = "Not triggered";
        } else if (context == null || context.getWorld() == null) {
            error = "Missing execution world";
        } else if (pos == null) {
            error = "Invalid coordinate";
        } else if (targetState == null) {
            error = "Invalid block info";
        } else {
            try {
                previousBlock = context.getWorld().getBlockState(pos);
                int flags = WorldWriteUtils.flags(notify);
                if (dropItems && !context.getWorld().isAir(pos)) {
                    context.getWorld().breakBlock(pos, true);
                }
                success = context.getWorld().setBlockState(pos, targetState, flags);
                if (success && recordUndo && previousBlock instanceof BlockState previousState) {
                    WorldWriteHistoryService.UndoRecord record = new WorldWriteHistoryService.UndoRecord();
                    record.add(pos, previousState);
                    WorldWriteHistoryService.getInstance().push(
                        WorldWriteHistoryService.resolveActorId(context.getPlayer()),
                        record
                    );
                }
                if (success) {
                    NbtCompound incomingNbt = WorldWriteNbtUtils.resolveIncomingNbt(inputValues);
                    if (incomingNbt != null) {
                        nbtSuccess = WorldWriteNbtUtils.applyToBlockEntity(
                            context,
                            pos,
                            incomingNbt,
                            WorldWriteNbtUtils.mergeRequested(inputValues),
                            notify
                        );
                        if (!nbtSuccess && error.isEmpty()) {
                            error = "Block placed, but NBT was not applied";
                        }
                    } else if (WorldWriteNbtUtils.hasNbtInput(inputValues)) {
                        error = "Invalid NBT input";
                    }
                } else {
                    error = "World rejected block placement";
                }
            } catch (Exception e) {
                success = false;
                error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_NBT_SUCCESS_ID, nbtSuccess);
        outputValues.put(OUTPUT_ERROR_ID, error);
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
