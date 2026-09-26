package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
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
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether inputs were valid for the query", NodeDataType.BOOLEAN, this));
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

        NbtCompound nbt = extractEntityNbt(entity);
        String fullString = nbt != null ? nbt.toString() : "";

        outputValues.put(OUTPUT_FOUND_ID, true);
        outputValues.put(OUTPUT_ENTITY_ID, entity);
        outputValues.put(OUTPUT_NBT_ID, nbt);
        outputValues.put(OUTPUT_NBT_STRING_ID, WorldReadUtils.truncate(fullString, maxStringLength));
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_NBT_SIZE_ID, fullString.length());
        outputValues.put(OUTPUT_ERROR_ID, nbt != null ? "" : "Unable to extract entity NBT.");
    }

    private @Nullable NbtCompound extractEntityNbt(Entity entity) {
        Method[] methods = entity.getClass().getMethods();
        for (Method method : methods) {
            String name = method.getName();
            if (!"writeNbt".equals(name) && !"saveNbt".equals(name)) {
                continue;
            }
            try {
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == NbtCompound.class) {
                    NbtCompound out = new NbtCompound();
                    Object result = method.invoke(entity, out);
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
        outputValues.put(OUTPUT_FOUND_ID, false);
        outputValues.put(OUTPUT_ENTITY_ID, null);
        outputValues.put(OUTPUT_NBT_ID, null);
        outputValues.put(OUTPUT_NBT_STRING_ID, "");
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_NBT_SIZE_ID, 0);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
