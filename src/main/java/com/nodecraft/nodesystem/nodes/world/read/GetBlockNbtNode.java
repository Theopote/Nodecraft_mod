package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.world.WorldQueryAccess;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.read.get_block_nbt",
    displayName = "Get Block NBT",
    description = "Reads full block-entity NBT data at a block position. Max String Length limits serialized output only, not source NBT size.",
    category = "world.read",
    order = 8
)
public class GetBlockNbtNode extends BaseNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetBlockNbtNode.class);

    private static final String INPUT_COORDINATE_ID = "input_coordinate";
    private static final String INPUT_MAX_STRING_LENGTH_ID = "input_max_string_length";

    private static final String OUTPUT_HAS_BLOCK_ENTITY_ID = "output_has_block_entity";
    private static final String OUTPUT_NBT_ID = "output_nbt";
    private static final String OUTPUT_NBT_STRING_ID = "output_nbt_string";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_NBT_SIZE_ID = "output_nbt_size";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetBlockNbtNode() {
        super(UUID.randomUUID(), "world.read.get_block_nbt");

        addInputPort(new BasePort(INPUT_COORDINATE_ID, "Coordinate", "Block position to inspect", NodeDataType.BLOCK_POS, this));
        addInputPort(new BasePort(INPUT_MAX_STRING_LENGTH_ID, "Max String Length", "Maximum SNBT string length (exact INTEGER 1..65536); unconnected uses 4096", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_HAS_BLOCK_ENTITY_ID, "Has Block Entity", "Whether this block has block-entity data", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NBT_ID, "NBT", "Block-entity NBT compound", NodeDataType.NBT_COMPOUND, this));
        addOutputPort(new BasePort(OUTPUT_NBT_STRING_ID, "NBT String", "SNBT string representation", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether NBT extraction succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NBT_SIZE_ID, "NBT Size", "Length of the full SNBT string before truncation", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when NBT read fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Reads full block-entity NBT data at a block position. Max String Length limits serialized output only, not source NBT size.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BlockPos pos = WorldReadUtils.requireBlockPos(inputValues.get(INPUT_COORDINATE_ID));
        if (pos == null) {
            writeInvalid("Coordinate input must be a block position.");
            return;
        }
        Integer maxStringLength = WorldReadUtils.resolveMaxStringLength(this, INPUT_MAX_STRING_LENGTH_ID);
        if (maxStringLength == null) {
            writeInvalid("Max String Length must be an exact INTEGER between 1 and "
                    + WorldReadUtils.MAX_NBT_STRING_LENGTH + ".");
            return;
        }
        if (context == null || context.getWorld() == null) {
            writeInvalid("Execution context or world is missing.");
            return;
        }

        WorldQueryAccess access = new WorldQueryAccess(context.getWorld());
        WorldQueryAccess.BlockEntityRead entityRead = access.getBlockEntity(pos);
        if (entityRead.status() != WorldQueryAccess.Status.OK) {
            writeInvalid(entityRead.status() == WorldQueryAccess.Status.BUDGET
                    ? "World read budget exceeded."
                    : "Target chunk is not loaded");
            return;
        }

        BlockEntity blockEntity = entityRead.entity();
        if (blockEntity == null) {
            outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, false);
            outputValues.put(OUTPUT_NBT_ID, null);
            outputValues.put(OUTPUT_NBT_STRING_ID, "");
            outputValues.put(OUTPUT_VALID_ID, true);
            outputValues.put(OUTPUT_NBT_SIZE_ID, 0);
            outputValues.put(OUTPUT_ERROR_ID, "");
            return;
        }

        NbtCompound nbt;
        try {
            nbt = blockEntity.createNbtWithIdentifyingData(context.getWorld().getRegistryManager());
        } catch (Exception e) {
            LOGGER.debug("Unable to extract block entity NBT", e);
            publishExtractionFailure(true);
            return;
        }
        if (nbt == null) {
            publishExtractionFailure(true);
            return;
        }

        String fullString = WorldReadUtils.serializeNbtOrCap(nbt);
        if (fullString == null) {
            outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, true);
            outputValues.put(OUTPUT_NBT_ID, null);
            outputValues.put(OUTPUT_NBT_STRING_ID, "");
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_NBT_SIZE_ID, 0);
            outputValues.put(OUTPUT_ERROR_ID, "NBT exceeds serialization size cap of "
                    + GenerationLimits.MAX_NBT_SERIALIZED_CHARS + ".");
            return;
        }

        outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, true);
        outputValues.put(OUTPUT_NBT_ID, nbt);
        outputValues.put(OUTPUT_NBT_STRING_ID, WorldReadUtils.truncate(fullString, maxStringLength));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_NBT_SIZE_ID, fullString.length());
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishExtractionFailure(boolean hasBlockEntity) {
        outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, hasBlockEntity);
        outputValues.put(OUTPUT_NBT_ID, null);
        outputValues.put(OUTPUT_NBT_STRING_ID, "");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_NBT_SIZE_ID, 0);
        outputValues.put(OUTPUT_ERROR_ID, "Unable to extract block entity NBT.");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_HAS_BLOCK_ENTITY_ID, false);
        outputValues.put(OUTPUT_NBT_ID, null);
        outputValues.put(OUTPUT_NBT_STRING_ID, "");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_NBT_SIZE_ID, 0);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
