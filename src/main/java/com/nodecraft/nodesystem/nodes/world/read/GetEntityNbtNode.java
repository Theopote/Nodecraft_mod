package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    id = "world.read.get_entity_nbt",
    displayName = "Get Entity NBT",
    description = "Reads full entity NBT data from an entity object, UUID, or nearest type query.",
    category = "world.read",
    order = 11
)
public class GetEntityNbtNode extends BaseNode {

    private static final String INPUT_ENTITY_ID = "input_entity";
    private static final String INPUT_UUID_ID = "input_uuid";
    private static final String INPUT_ENTITY_TYPE_ID = "input_entity_type";
    private static final String INPUT_FIND_NEAREST_ID = "input_find_nearest";
    private static final String INPUT_MAX_DISTANCE_ID = "input_max_distance";
    private static final String INPUT_MAX_STRING_LENGTH_ID = "input_max_string_length";

    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_ENTITY_ID = "output_entity";
    private static final String OUTPUT_NBT_ID = "output_nbt";
    private static final String OUTPUT_NBT_STRING_ID = "output_nbt_string";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_NBT_SIZE_ID = "output_nbt_size";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetEntityNbtNode() {
        super(UUID.randomUUID(), "world.read.get_entity_nbt");

        addInputPort(new BasePort(INPUT_ENTITY_ID, "Entity", "Entity object input", NodeDataType.MINECRAFT_ENTITY, this));
        addInputPort(new BasePort(INPUT_UUID_ID, "UUID", "Entity UUID string", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_ENTITY_TYPE_ID, "Entity Type", "Entity type id for query", NodeDataType.ENTITY_TYPE, this));
        addInputPort(new BasePort(INPUT_FIND_NEAREST_ID, "Find Nearest", "Choose nearest matching entity", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_DISTANCE_ID, "Max Distance", "Query radius for type lookup", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_MAX_STRING_LENGTH_ID, "Max String Length", "Maximum SNBT string length; 0 means unlimited", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether entity was found", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_ID, "Entity", "Resolved entity", NodeDataType.MINECRAFT_ENTITY, this));
        addOutputPort(new BasePort(OUTPUT_NBT_ID, "NBT", "Entity NBT compound", NodeDataType.NBT_COMPOUND, this));
        addOutputPort(new BasePort(OUTPUT_NBT_STRING_ID, "NBT String", "SNBT string representation", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance", "Distance from player to resolved entity", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_NBT_SIZE_ID, "NBT Size", "Length of the full SNBT string before truncation", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when NBT read fails", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Reads full entity NBT data from an entity object, UUID, or nearest type query.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        if (context == null || context.getWorld() == null) {
            writeInvalid("Execution context or world is missing.");
            return;
        }

        boolean findNearest = !(inputValues.get(INPUT_FIND_NEAREST_ID) instanceof Boolean b) || b;
        double maxDistance = inputValues.get(INPUT_MAX_DISTANCE_ID) instanceof Number n ? Math.max(1.0d, n.doubleValue()) : 64.0d;
        int maxStringLength = inputValues.get(INPUT_MAX_STRING_LENGTH_ID) instanceof Number n
            ? Math.max(0, n.intValue())
            : WorldReadUtils.DEFAULT_MAX_NBT_STRING_LENGTH;

        Entity entity = null;
        if (inputValues.get(INPUT_ENTITY_ID) instanceof Entity inputEntity) {
            entity = inputEntity;
        } else if (inputValues.get(INPUT_UUID_ID) instanceof String uuidText && !uuidText.isBlank()) {
            entity = findByUuid(context, uuidText);
        } else if (inputValues.get(INPUT_ENTITY_TYPE_ID) instanceof String typeId && !typeId.isBlank()) {
            entity = findByType(context, typeId, findNearest, maxDistance);
        }

        if (entity == null) {
            writeInvalid("Entity was not found.");
            return;
        }

        NbtCompound nbt = extractEntityNbt(entity);
        double distance = context.getPlayer() != null ? Math.sqrt(context.getPlayer().squaredDistanceTo(entity)) : -1.0d;
        String fullString = nbt != null ? nbt.toString() : "";

        outputValues.put(OUTPUT_FOUND_ID, true);
        outputValues.put(OUTPUT_ENTITY_ID, entity);
        outputValues.put(OUTPUT_NBT_ID, nbt);
        outputValues.put(OUTPUT_NBT_STRING_ID, WorldReadUtils.truncate(fullString, maxStringLength));
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
        outputValues.put(OUTPUT_NBT_SIZE_ID, fullString.length());
        outputValues.put(OUTPUT_ERROR_ID, nbt != null ? "" : "Unable to extract entity NBT.");
    }

    private @Nullable Entity findByUuid(ExecutionContext context, String uuidText) {
        try {
            UUID uuid = UUID.fromString(uuidText);
            if (context.getWorld() instanceof ServerWorld serverWorld) {
                return serverWorld.getEntity(uuid);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private @Nullable Entity findByType(ExecutionContext context, String typeId, boolean findNearest, double maxDistance) {
        if (context.getPlayer() == null) {
            return null;
        }
        Box box = context.getPlayer().getBoundingBox().expand(maxDistance);
        List<Entity> nearby = context.getWorld().getOtherEntities(context.getPlayer(), box);
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : nearby) {
            String id = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            if (!typeId.equals(id)) {
                continue;
            }
            if (!findNearest) {
                return entity;
            }
            double d = context.getPlayer().squaredDistanceTo(entity);
            if (d < bestDistance) {
                bestDistance = d;
                best = entity;
            }
        }
        return best;
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
        outputValues.put(OUTPUT_DISTANCE_ID, -1.0d);
        outputValues.put(OUTPUT_NBT_SIZE_ID, 0);
        outputValues.put(OUTPUT_ERROR_ID, error);
    }
}
