package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.set_block_nbt",
    displayName = "Set Block NBT",
    description = "Writes or merges NBT data to a block entity at a target position.",
    category = "world.write",
    order = 6
)
public class SetBlockNbtNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_NBT_ID = WorldWriteNbtUtils.INPUT_NBT_ID;
    private static final String INPUT_NBT_STRING_ID = WorldWriteNbtUtils.INPUT_NBT_STRING_ID;
    private static final String INPUT_MERGE_ID = "input_merge";
    private static final String INPUT_NOTIFY_ID = "input_notify";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_HAS_BLOCK_ENTITY_ID = "output_has_block_entity";
    private static final String OUTPUT_APPLIED_NBT_ID = "output_applied_nbt";
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    private boolean mergeNbtProperty = false;
    private boolean notifyUpdate = true;
    @NodeProperty(displayName = "Record Undo", category = "Execution", order = 1)
    private boolean recordUndo = true;

    public SetBlockNbtNode() {
        super(UUID.randomUUID(), "world.write.set_block_nbt");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Target block position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NBT_ID, "NBT", "NBT compound to write", NodeDataType.NBT_COMPOUND, this));
        addInputPort(new BasePort(INPUT_NBT_STRING_ID, "NBT String", "SNBT string to parse and write", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_MERGE_ID, "Merge", "Merge with existing NBT instead of replacing", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify", "Whether to notify block updates", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether NBT write succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HAS_BLOCK_ENTITY_ID, "Has Block Entity", "Whether target block has block entity", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_APPLIED_NBT_ID, "Applied NBT", "Final NBT written to block entity", NodeDataType.NBT_COMPOUND, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why NBT write did not run or failed", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Writes or merges NBT data to a block entity at a target position.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(false, false, null, false, "Trigger is connected but null or invalid.");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(false, false, null, true, "Not triggered");
            return;
        }

        Boolean merge = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_MERGE_ID, mergeNbtProperty);
        Boolean notify = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_NOTIFY_ID, notifyUpdate);
        if (merge == null || notify == null) {
            publish(false, false, null, false, "Boolean drive is connected but null or invalid.");
            return;
        }

        BlockPos pos = WorldWriteUtils.requireBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        WorldWriteNbtUtils.NbtResolveResult nbtResult = WorldWriteNbtUtils.resolveIncomingNbt(this);
        if (pos == null) {
            publish(false, false, null, false, "Invalid coordinate (BLOCK_POS required).");
            return;
        }
        if (nbtResult.failed()) {
            publish(false, false, null, false, nbtResult.error());
            return;
        }
        if (nbtResult.nbt() == null) {
            publish(false, false, null, false, "NBT Compound or NBT String is required.");
            return;
        }
        if (context == null || context.getWorld() == null) {
            publish(false, false, null, false, "Missing execution world");
            return;
        }

        BlockEntity blockEntity = context.getWorld().getBlockEntity(pos);
        if (blockEntity == null) {
            publish(false, false, null, true, "Target has no block entity");
            return;
        }

        BlockSnapshot before = WorldWriteTransaction.captureCurrent(context, pos);
        if (before == null) {
            publish(false, false, null, false, "Missing execution world");
            return;
        }
        WorldWriteTransaction tx = new WorldWriteTransaction(WorldWriteUtils.worldKey(context.getWorld()));

        NbtCompound incoming = nbtResult.nbt();
        NbtCompound current = WorldWriteNbtUtils.extractBlockEntityNbt(blockEntity, context);
        NbtCompound target = merge && current != null ? WorldWriteNbtUtils.mergeNbt(current.copy(), incoming) : incoming.copy();
        target.putInt("x", pos.getX());
        target.putInt("y", pos.getY());
        target.putInt("z", pos.getZ());

        boolean success = WorldWriteNbtUtils.applyBlockEntityNbt(blockEntity, target, context);
        if (success) {
            tx.recordSuccess(before);
            blockEntity.markDirty();
            if (notify) {
                context.getWorld().updateListeners(pos, before.state(), context.getWorld().getBlockState(pos), 3);
            }
            tx.pushIfNeeded(context, recordUndo);
            publish(true, true, target, true, "");
        } else {
            tx.recordFailure();
            publish(false, true, null, true, "Failed to apply block entity NBT");
        }
    }

    private void publish(boolean success, boolean hasBlockEntity, @Nullable NbtCompound applied, boolean valid, String error) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, hasBlockEntity);
        outputValues.put(OUTPUT_APPLIED_NBT_ID, applied);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isMergeNbtProperty() { return mergeNbtProperty; }
    public void setMergeNbtProperty(boolean mergeNbtProperty) { this.mergeNbtProperty = mergeNbtProperty; markDirty(); }
    public boolean isNotifyUpdate() { return notifyUpdate; }
    public void setNotifyUpdate(boolean notifyUpdate) { this.notifyUpdate = notifyUpdate; markDirty(); }
    public boolean isRecordUndo() { return recordUndo; }
    public void setRecordUndo(boolean recordUndo) { this.recordUndo = recordUndo; markDirty(); }
    public boolean isTrigger() { return trigger; }
    public void setTrigger(boolean trigger) { this.trigger = trigger; markDirty(); }
}
