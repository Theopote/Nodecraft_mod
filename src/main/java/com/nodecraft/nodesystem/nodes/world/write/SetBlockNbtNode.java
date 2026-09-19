package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
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
    order = 16
)
public class SetBlockNbtNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String INPUT_NBT_ID = WorldWriteNbtUtils.INPUT_NBT_ID;
    private static final String INPUT_NBT_STRING_ID = WorldWriteNbtUtils.INPUT_NBT_STRING_ID;
    private static final String INPUT_MERGE_ID = "input_merge";
    private static final String INPUT_NOTIFY_ID = "input_notify";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_HAS_BLOCK_ENTITY_ID = "output_has_block_entity";
    private static final String OUTPUT_APPLIED_NBT_ID = "output_applied_nbt";

    public SetBlockNbtNode() {
        super(UUID.randomUUID(), "world.write.set_block_nbt");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Target block position", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_NBT_ID, "NBT", "NBT compound to write", NodeDataType.NBT_COMPOUND, this));
        addInputPort(new BasePort(INPUT_NBT_STRING_ID, "NBT String", "SNBT string to parse and write", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_MERGE_ID, "Merge", "Merge with existing NBT instead of replacing", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NOTIFY_ID, "Notify", "Whether to notify block updates", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether NBT write succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HAS_BLOCK_ENTITY_ID, "Has Block Entity", "Whether target block has block entity", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_APPLIED_NBT_ID, "Applied NBT", "Final NBT written to block entity", NodeDataType.NBT_COMPOUND, this));
    }

    @Override
    public String getDescription() {
        return "Writes or merges NBT data to a block entity at a target position.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null || context.getWorld() == null || !(inputValues.get(INPUT_COORDINATE_ID) instanceof BlockPos pos)) {
            writeInvalid();
            return;
        }

        BlockEntity blockEntity = context.getWorld().getBlockEntity(pos);
        if (blockEntity == null) {
            outputValues.put(OUTPUT_SUCCESS_ID, false);
            outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, false);
            outputValues.put(OUTPUT_APPLIED_NBT_ID, null);
            return;
        }

        NbtCompound incoming = WorldWriteNbtUtils.resolveIncomingNbt(inputValues);
        if (incoming == null) {
            outputValues.put(OUTPUT_SUCCESS_ID, false);
            outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, true);
            outputValues.put(OUTPUT_APPLIED_NBT_ID, null);
            return;
        }

        boolean merge = inputValues.get(INPUT_MERGE_ID) instanceof Boolean b && b;
        boolean notify = !(inputValues.get(INPUT_NOTIFY_ID) instanceof Boolean b) || b;

        NbtCompound current = WorldWriteNbtUtils.extractBlockEntityNbt(blockEntity, context);
        NbtCompound target = merge && current != null ? WorldWriteNbtUtils.mergeNbt(current.copy(), incoming) : incoming.copy();
        target.putInt("x", pos.getX());
        target.putInt("y", pos.getY());
        target.putInt("z", pos.getZ());

        boolean success = WorldWriteNbtUtils.applyBlockEntityNbt(blockEntity, target, context);
        if (success) {
            blockEntity.markDirty();
            if (notify) {
                context.getWorld().updateListeners(pos, context.getWorld().getBlockState(pos), context.getWorld().getBlockState(pos), 3);
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, true);
        outputValues.put(OUTPUT_APPLIED_NBT_ID, success ? target : null);
    }

    private void writeInvalid() {
        outputValues.put(OUTPUT_SUCCESS_ID, false);
        outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, false);
        outputValues.put(OUTPUT_APPLIED_NBT_ID, null);
    }
}
