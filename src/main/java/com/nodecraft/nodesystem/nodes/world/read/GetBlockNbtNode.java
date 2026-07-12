package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

@NodeInfo(
    id = "world.read.get_block_nbt",
    displayName = "Get Block NBT",
    description = "Reads full block-entity NBT data at a block position.",
    category = "world.read",
    order = 10
)
public class GetBlockNbtNode extends BaseNode {

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String INPUT_MAX_STRING_LENGTH_ID = "input_max_string_length";

    private static final String OUTPUT_HAS_BLOCK_ENTITY_ID = "output_has_block_entity";
    private static final String OUTPUT_NBT_ID = "output_nbt";
    private static final String OUTPUT_NBT_STRING_ID = "output_nbt_string";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_NBT_SIZE_ID = "output_nbt_size";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetBlockNbtNode() {
        super(UUID.randomUUID(), "world.read.get_block_nbt");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Block position to inspect", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_MAX_STRING_LENGTH_ID, "Max String Length", "Maximum SNBT string length; 0 uses the default cap", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_HAS_BLOCK_ENTITY_ID, "Has Block Entity", "Whether this block has block-entity data", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NBT_ID, "NBT", "Block-entity NBT compound", NodeDataType.NBT_COMPOUND, this));
        addOutputPort(new BasePort(OUTPUT_NBT_STRING_ID, "NBT String", "SNBT string representation", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether NBT read succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NBT_SIZE_ID, "NBT Size", "Length of the full SNBT string before truncation", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when NBT read fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Reads full block-entity NBT data at a block position.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPos pos = WorldReadUtils.resolveBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        int maxStringLength = WorldReadUtils.resolveMaxStringLength(inputValues.get(INPUT_MAX_STRING_LENGTH_ID));

        if (context == null || context.getWorld() == null) {
            writeInvalid("Execution context or world is missing.");
            return;
        }
        if (pos == null) {
            writeInvalid("Coordinate input must resolve to a block position.");
            return;
        }

        BlockEntity blockEntity = context.getWorld().getBlockEntity(pos);
        if (blockEntity == null) {
            outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, false);
            outputValues.put(OUTPUT_NBT_ID, null);
            outputValues.put(OUTPUT_NBT_STRING_ID, "");
            outputValues.put(OUTPUT_SUCCESS_ID, false);
            outputValues.put(OUTPUT_NBT_SIZE_ID, 0);
            outputValues.put(OUTPUT_ERROR_ID, "");
            return;
        }

        NbtCompound nbt = extractBlockEntityNbt(blockEntity, context);
        boolean success = nbt != null;
        String fullString = success ? nbt.toString() : "";
        outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, true);
        outputValues.put(OUTPUT_NBT_ID, success ? nbt : null);
        outputValues.put(OUTPUT_NBT_STRING_ID, WorldReadUtils.truncate(fullString, maxStringLength));
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_NBT_SIZE_ID, fullString.length());
        outputValues.put(OUTPUT_ERROR_ID, success ? "" : "Unable to extract block entity NBT.");
    }

    private @Nullable NbtCompound extractBlockEntityNbt(BlockEntity blockEntity, ExecutionContext context) {
        Object lookup = context.getWorld() != null ? context.getWorld().getRegistryManager() : null;
        Method[] methods = blockEntity.getClass().getMethods();
        for (Method method : methods) {
            if (!method.getName().startsWith("createNbt")) {
                continue;
            }
            try {
                if (method.getParameterCount() == 0) {
                    Object result = method.invoke(blockEntity);
                    if (result instanceof NbtCompound nbt) {
                        return nbt;
                    }
                } else if (method.getParameterCount() == 1 && lookup != null) {
                    Object result = method.invoke(blockEntity, lookup);
                    if (result instanceof NbtCompound nbt) {
                        return nbt;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        for (Method method : methods) {
            if (!"writeNbt".equals(method.getName())) {
                continue;
            }
            try {
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == NbtCompound.class) {
                    NbtCompound out = new NbtCompound();
                    Object result = method.invoke(blockEntity, out);
                    if (result instanceof NbtCompound nbt) {
                        return nbt;
                    }
                    return out;
                }
                if (method.getParameterCount() == 2
                    && method.getParameterTypes()[0] == NbtCompound.class
                    && lookup != null) {
                    NbtCompound out = new NbtCompound();
                    Object result = method.invoke(blockEntity, out, lookup);
                    if (result instanceof NbtCompound nbt) {
                        return nbt;
                    }
                    return out;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, false);
        outputValues.put(OUTPUT_NBT_ID, null);
        outputValues.put(OUTPUT_NBT_STRING_ID, "");
        outputValues.put(OUTPUT_SUCCESS_ID, false);
        outputValues.put(OUTPUT_NBT_SIZE_ID, 0);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }
}
