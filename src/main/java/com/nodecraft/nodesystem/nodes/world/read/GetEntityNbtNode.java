package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.read.get_entity_nbt",
    displayName = "Get Entity NBT",
    description = "Reads full entity NBT from a Minecraft entity object. Compose with world.query.Get Entity for lookup. Max String Length limits serialized output only.",
    category = "world.read",
    order = 9
)
public class GetEntityNbtNode extends BaseNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetEntityNbtNode.class);

    private static final String INPUT_ENTITY_ID = "input_entity";
    private static final String INPUT_MAX_STRING_LENGTH_ID = "input_max_string_length";

    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_ENTITY_ID = "output_entity";
    private static final String OUTPUT_NBT_ID = "output_nbt";
    private static final String OUTPUT_NBT_STRING_ID = "output_nbt_string";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_NBT_SIZE_ID = "output_nbt_size";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetEntityNbtNode() {
        super(UUID.randomUUID(), "world.read.get_entity_nbt");

        addInputPort(new BasePort(INPUT_ENTITY_ID, "Entity", "Entity object from Get Entity or another producer", NodeDataType.MINECRAFT_ENTITY, this));
        addInputPort(new BasePort(INPUT_MAX_STRING_LENGTH_ID, "Max String Length", "Maximum SNBT string length (exact INTEGER 1..65536); unconnected uses 4096", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether a usable entity was provided", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_ID, "Entity", "Passthrough entity", NodeDataType.MINECRAFT_ENTITY, this));
        addOutputPort(new BasePort(OUTPUT_NBT_ID, "NBT", "Entity NBT compound", NodeDataType.NBT_COMPOUND, this));
        addOutputPort(new BasePort(OUTPUT_NBT_STRING_ID, "NBT String", "SNBT string representation", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether NBT extraction succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NBT_SIZE_ID, "NBT Size", "Length of the full SNBT string before truncation", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when NBT read fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Reads full entity NBT from a Minecraft entity object. Compose with world.query.Get Entity for lookup. Max String Length limits serialized output only.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Integer maxStringLength = WorldReadUtils.resolveMaxStringLength(this, INPUT_MAX_STRING_LENGTH_ID);
        if (maxStringLength == null) {
            writeInvalid("Max String Length must be an exact INTEGER between 1 and "
                    + WorldReadUtils.MAX_NBT_STRING_LENGTH + ".");
            return;
        }

        if (!(inputValues.get(INPUT_ENTITY_ID) instanceof Entity entity)) {
            writeInvalid("Entity input must be a Minecraft entity.");
            return;
        }
        if (!entity.isAlive() || entity.isRemoved()) {
            outputValues.put(OUTPUT_FOUND_ID, false);
            outputValues.put(OUTPUT_ENTITY_ID, entity);
            outputValues.put(OUTPUT_NBT_ID, null);
            outputValues.put(OUTPUT_NBT_STRING_ID, "");
            outputValues.put(OUTPUT_VALID_ID, true);
            outputValues.put(OUTPUT_NBT_SIZE_ID, 0);
            outputValues.put(OUTPUT_ERROR_ID, "");
            return;
        }

        NbtCompound nbt;
        try {
            NbtWriteView view = NbtWriteView.create(ErrorReporter.EMPTY, entity.getRegistryManager());
            if (!entity.saveSelfData(view)) {
                publishExtractionFailure(entity);
                return;
            }
            nbt = view.getNbt();
        } catch (Exception e) {
            LOGGER.debug("Unable to extract entity NBT", e);
            publishExtractionFailure(entity);
            return;
        }
        if (nbt == null) {
            publishExtractionFailure(entity);
            return;
        }

        String fullString = WorldReadUtils.serializeNbtOrCap(nbt);
        if (fullString == null) {
            outputValues.put(OUTPUT_FOUND_ID, true);
            outputValues.put(OUTPUT_ENTITY_ID, entity);
            outputValues.put(OUTPUT_NBT_ID, null);
            outputValues.put(OUTPUT_NBT_STRING_ID, "");
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_NBT_SIZE_ID, 0);
            outputValues.put(OUTPUT_ERROR_ID, "NBT exceeds serialization size cap of "
                    + GenerationLimits.MAX_NBT_SERIALIZED_CHARS + ".");
            return;
        }

        outputValues.put(OUTPUT_FOUND_ID, true);
        outputValues.put(OUTPUT_ENTITY_ID, entity);
        outputValues.put(OUTPUT_NBT_ID, nbt);
        outputValues.put(OUTPUT_NBT_STRING_ID, WorldReadUtils.truncate(fullString, maxStringLength));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_NBT_SIZE_ID, fullString.length());
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void publishExtractionFailure(Entity entity) {
        outputValues.put(OUTPUT_FOUND_ID, true);
        outputValues.put(OUTPUT_ENTITY_ID, entity);
        outputValues.put(OUTPUT_NBT_ID, null);
        outputValues.put(OUTPUT_NBT_STRING_ID, "");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_NBT_SIZE_ID, 0);
        outputValues.put(OUTPUT_ERROR_ID, "Unable to extract entity NBT.");
    }

    private void writeInvalid(String error) {
        outputValues.put(OUTPUT_FOUND_ID, false);
        outputValues.put(OUTPUT_ENTITY_ID, null);
        outputValues.put(OUTPUT_NBT_ID, null);
        outputValues.put(OUTPUT_NBT_STRING_ID, "");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_NBT_SIZE_ID, 0);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
