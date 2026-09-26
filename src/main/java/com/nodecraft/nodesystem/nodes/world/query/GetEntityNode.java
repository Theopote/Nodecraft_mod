package com.nodecraft.nodesystem.nodes.world.query;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_READ,
    id = "world.query.get_entity",
    displayName = "Get Entity",
    description = "Finds an entity by UUID or by type near the current player.",
    category = "world.query",
    order = 10
)
public class GetEntityNode extends BaseNode {

    private static final String INPUT_UUID_ID = "input_uuid";
    private static final String INPUT_ENTITY_TYPE_ID = "input_entity_type";
    private static final String INPUT_FIND_NEAREST_ID = "input_find_nearest";
    private static final String INPUT_MAX_DISTANCE_ID = "input_max_distance";

    private static final String OUTPUT_ENTITY_ID = "output_entity";
    private static final String OUTPUT_ENTITY_TYPE_ID = "output_entity_type";
    private static final String OUTPUT_ENTITY_POS_ID = "output_entity_pos";
    private static final String OUTPUT_FOUND_ID = "output_found";
    private static final String OUTPUT_DISTANCE_ID = "output_distance";
    private static final String OUTPUT_UUID_ID = "output_uuid";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetEntityNode() {
        super(UUID.randomUUID(), "world.query.get_entity");

        addInputPort(new BasePort(INPUT_UUID_ID, "UUID", "Entity UUID string", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_ENTITY_TYPE_ID, "Entity Type", "Entity type id used when UUID is unconnected", NodeDataType.ENTITY_TYPE, this));
        addInputPort(new BasePort(INPUT_FIND_NEAREST_ID, "Find Nearest", "Choose nearest matching entity to the current player", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_DISTANCE_ID, "Max Distance", "Search radius for type lookup around the current player", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ENTITY_ID, "Entity", "Resolved entity object", NodeDataType.MINECRAFT_ENTITY, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_TYPE_ID, "Entity Type", "Entity registry id", NodeDataType.ENTITY_TYPE, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_POS_ID, "Entity Position", "Entity world position", NodeDataType.POINT, this));
        addOutputPort(new BasePort(OUTPUT_FOUND_ID, "Found", "Whether an entity was found", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_DISTANCE_ID, "Distance", "Distance from the current player to the entity", NodeDataType.DOUBLE, this));
        addOutputPort(new BasePort(OUTPUT_UUID_ID, "UUID", "Resolved entity UUID", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the query inputs and execution context were usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when the query is invalid", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Finds an entity by UUID or by type near the current player.";
    }

    @Override
    public String getDisplayName() {
        return "Get Entity";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Boolean findNearest = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_FIND_NEAREST_ID, true);
        if (findNearest == null) {
            publish(null, false, false, "Find Nearest is connected but null or invalid.", context);
            return;
        }

        Double maxDistance = resolveMaxDistance();
        if (maxDistance == null || maxDistance <= 0.0d) {
            publish(null, false, false, "Max Distance must be a finite number greater than zero.", context);
            return;
        }
        if (maxDistance > GenerationLimits.MAX_WORLD_QUERY_DISTANCE) {
            publish(null, false, false,
                    "Max Distance exceeds hard cap of " + GenerationLimits.MAX_WORLD_QUERY_DISTANCE + ".",
                    context);
            return;
        }

        if (context == null || context.getWorld() == null) {
            publish(null, false, false, "Execution context or world is missing.", context);
            return;
        }

        Entity entity;
        if (OptionalPortDrive.isConnected(this, INPUT_UUID_ID)) {
            String uuidText = OptionalPortDrive.resolveOptionalString(this, INPUT_UUID_ID, null);
            if (uuidText == null) {
                publish(null, false, false, "UUID is connected but null or invalid.", context);
                return;
            }
            try {
                entity = findByUuid(context, uuidText);
            } catch (IllegalArgumentException e) {
                publish(null, false, false, "Invalid UUID: " + uuidText, context);
                return;
            } catch (Exception e) {
                NodeCraft.LOGGER.warn("GetEntityNode UUID query failed: {}", e.getMessage());
                publish(null, false, false, "Entity UUID query failed: " + e.getMessage(), context);
                return;
            }
        } else {
            Object entityTypeObj = inputValues.get(INPUT_ENTITY_TYPE_ID);
            if (!(entityTypeObj instanceof String typeId) || typeId.isBlank()) {
                publish(null, false, false, "Entity Type is required when UUID is unconnected.", context);
                return;
            }
            if (context.getPlayer() == null) {
                publish(null, false, false, "Current player is required for entity type lookup.", context);
                return;
            }
            entity = findByType(context, typeId.trim(), findNearest, maxDistance);
        }

        publish(entity, true, entity != null, "", context);
    }

    private @Nullable Double resolveMaxDistance() {
        if (OptionalPortDrive.isConnected(this, INPUT_MAX_DISTANCE_ID)) {
            Object value = inputValues.get(INPUT_MAX_DISTANCE_ID);
            if (!(value instanceof Number number)) {
                return null;
            }
            double resolved = number.doubleValue();
            return Double.isFinite(resolved) ? resolved : null;
        }
        Object value = inputValues.get(INPUT_MAX_DISTANCE_ID);
        if (value instanceof Number number) {
            double resolved = number.doubleValue();
            return Double.isFinite(resolved) ? resolved : null;
        }
        return 64.0d;
    }

    private @Nullable Entity findByUuid(ExecutionContext context, String uuidText) {
        UUID uuid = UUID.fromString(uuidText);
        if (context.getWorld() instanceof ServerWorld serverWorld) {
            return serverWorld.getEntity(uuid);
        }
        return null;
    }

    private @Nullable Entity findByType(ExecutionContext context, String typeId, boolean findNearest, double maxDistance) {
        Box box = context.getPlayer().getBoundingBox().expand(maxDistance);
        List<Entity> nearby = context.getWorld().getOtherEntities(context.getPlayer(), box);
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : nearby) {
            if (!isQueryable(entity)) {
                continue;
            }
            String entityTypeId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            if (!typeId.equals(entityTypeId)) {
                continue;
            }
            if (!findNearest) {
                return entity;
            }
            double distance = context.getPlayer().squaredDistanceTo(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private boolean isQueryable(Entity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    private void publish(@Nullable Entity entity, boolean valid, boolean found, String error, @Nullable ExecutionContext context) {
        String entityType = "";
        PointData entityPos = new PointData(0, 0, 0);
        String uuid = "";
        double distance = -1.0d;

        if (entity != null) {
            entityType = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            entityPos = new PointData(entity.getX(), entity.getY(), entity.getZ());
            uuid = entity.getUuidAsString();
            if (context != null && context.getPlayer() != null) {
                distance = Math.sqrt(context.getPlayer().squaredDistanceTo(entity));
            }
        }

        outputValues.put(OUTPUT_ENTITY_ID, entity);
        outputValues.put(OUTPUT_ENTITY_TYPE_ID, entityType);
        outputValues.put(OUTPUT_ENTITY_POS_ID, entityPos);
        outputValues.put(OUTPUT_FOUND_ID, found);
        outputValues.put(OUTPUT_DISTANCE_ID, distance);
        outputValues.put(OUTPUT_UUID_ID, uuid);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
