package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
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
    effect = NodeEffect.WORLD_WRITE,
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
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;
    private static final String OUTPUT_PREVIOUS_BLOCK_ID = "output_previous_block";

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0,
        description = "When true (or Trigger port true), this write may run. Default false; V63 graphs migrate to true.")
    private boolean trigger = false;

    private boolean notifyUpdate = true;
    private boolean spawnDrops = false;
    private boolean mergeNbtProperty = false;
    @NodeProperty(displayName = "Record Undo", category = "Execution", order = 1)
    private boolean recordUndo = true;

    public SetBlockNode() {
        super(UUID.randomUUID(), "world.write.set_block");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Target block position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_BLOCK_INFO_ID, "Block Info", "Block state or block id to place", NodeDataType.BLOCK_INFO, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger",
            "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify Update", "Whether neighbor and listener updates should fire", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_SPAWN_DROPS_ID, "Spawn Drops", "Whether replacing a block should drop items first", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NBT_ID, "NBT", "Optional block-entity NBT to apply after placement", NodeDataType.NBT_COMPOUND, this));
        addInputPort(new BasePort(INPUT_NBT_STRING_ID, "NBT String", "Optional SNBT string to apply after placement", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_MERGE_NBT_ID, "Merge NBT", "Merge incoming NBT with block entity NBT instead of replacing", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether block placement succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NBT_SUCCESS_ID, "NBT Success", "Whether optional block-entity NBT was applied", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why placement did not run or failed", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_PREVIOUS_BLOCK_ID, "Previous Block", "Block state that was replaced", NodeDataType.BLOCK_INFO, this));
    }

    @Override
    public String getDescription() {
        return "Places one block at one block position, with optional block-entity NBT";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(false, false, false, "Trigger is connected but null or invalid.", null);
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(false, false, true, "Not triggered", null);
            return;
        }

        Boolean notify = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_NOTIFY_ID, notifyUpdate);
        Boolean dropItems = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_SPAWN_DROPS_ID, spawnDrops);
        Boolean mergeNbt = WorldWriteNbtUtils.resolveMergeNbt(this, mergeNbtProperty);
        if (notify == null || dropItems == null || mergeNbt == null) {
            publish(false, false, false, "Boolean drive is connected but null or invalid.", null);
            return;
        }

        BlockPos pos = WorldWriteUtils.requireBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        if (pos == null) {
            publish(false, false, false, "Invalid coordinate (BLOCK_POS required).", null);
            return;
        }

        BlockState targetState = WorldWriteUtils.resolveBlockState(inputValues.get(INPUT_BLOCK_INFO_ID));
        if (targetState == null) {
            publish(false, false, false, "Invalid block info", null);
            return;
        }

        WorldWriteNbtUtils.NbtResolveResult nbtResult = WorldWriteNbtUtils.resolveIncomingNbt(this);
        if (nbtResult.failed()) {
            publish(false, false, false, nbtResult.error(), null);
            return;
        }

        if (context == null || context.getWorld() == null) {
            publish(false, false, false, "Missing execution world", null);
            return;
        }

        boolean success = false;
        boolean nbtSuccess = false;
        String error = "";
        Object previousBlock = null;

        try {
            BlockState previousState = context.getWorld().getBlockState(pos);
            previousBlock = previousState;
            WorldWriteTransaction tx = new WorldWriteTransaction(WorldWriteUtils.worldKey(context.getWorld()));
            int flags = WorldWriteUtils.flags(notify);
            if (dropItems && !context.getWorld().isAir(pos)) {
                context.getWorld().breakBlock(pos, true);
            }
            success = context.getWorld().setBlockState(pos, targetState, flags);
            if (success) {
                tx.recordSuccess(context, pos, previousState);
                NbtCompound incomingNbt = nbtResult.nbt();
                if (incomingNbt != null) {
                    nbtSuccess = WorldWriteNbtUtils.applyToBlockEntity(
                        context, pos, incomingNbt, mergeNbt, notify);
                    if (!nbtSuccess) {
                        error = "Block placed, but NBT was not applied";
                        tx.markIncomplete();
                    }
                }
                tx.pushIfNeeded(context, recordUndo);
            } else {
                error = "World rejected block placement";
                tx.recordFailure();
            }
        } catch (Exception e) {
            success = false;
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }

        publish(success, nbtSuccess, true, error, previousBlock);
    }

    private void publish(boolean success, boolean nbtSuccess, boolean valid, String error, Object previousBlock) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_NBT_SUCCESS_ID, nbtSuccess);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
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

    public boolean isTrigger() {
        return trigger;
    }

    public void setTrigger(boolean trigger) {
        if (this.trigger != trigger) {
            this.trigger = trigger;
            markDirty();
        }
    }
}
