package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.spawn_entity",
    displayName = "Spawn Entity",
    description = "Spawns an entity into the world at a POINT position",
    category = "world.write",
    order = 7
)
public class SpawnEntityNode extends BaseNode {

    private static final String INPUT_POSITION_ID = "input_position";
    private static final String INPUT_ENTITY_TYPE_ID = "input_entity_type";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_NBT_DATA_ID = "input_nbt_data";
    private static final String INPUT_MOTION_ID = "input_motion";
    private static final String INPUT_NO_AI_ID = "input_no_ai";
    private static final String INPUT_INVULNERABLE_ID = "input_invulnerable";
    private static final String INPUT_PERSISTENT_ID = "input_persistent";
    private static final String INPUT_ROTATION_YAW_ID = "input_rotation_yaw";
    private static final String INPUT_ROTATION_PITCH_ID = "input_rotation_pitch";

    private static final String OUTPUT_ENTITY_ID = "output_entity";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_ENTITY_UUID_ID = "output_entity_uuid";
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    private boolean applyMotion = false;
    private boolean setNoAI = false;
    private boolean setInvulnerable = false;
    private boolean setPersistent = true;

    public SpawnEntityNode() {
        super(UUID.randomUUID(), "world.write.spawn_entity");

        addInputPort(new BasePort(INPUT_POSITION_ID, "Position", "Spawn POINT", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_ENTITY_TYPE_ID, "Entity Type", "Entity registry id", NodeDataType.ENTITY_TYPE, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_NBT_DATA_ID, "NBT Data", "Optional entity NBT", NodeDataType.NBT, this));
        addInputPort(new BasePort(INPUT_MOTION_ID, "Motion", "Optional initial motion vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_NO_AI_ID, "No AI", "Whether to disable AI for mob entities", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_INVULNERABLE_ID, "Invulnerable", "Whether the entity should be invulnerable", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_PERSISTENT_ID, "Persistent", "Whether the entity should be persistent", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_ROTATION_YAW_ID, "Yaw", "Spawn yaw in degrees", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROTATION_PITCH_ID, "Pitch", "Spawn pitch in degrees", NodeDataType.DOUBLE, this));

        addOutputPort(new BasePort(OUTPUT_ENTITY_ID, "Entity", "Spawned entity instance", NodeDataType.MINECRAFT_ENTITY, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the entity was spawned", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_UUID_ID, "Entity UUID", "UUID of the spawned entity", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why spawn did not run or failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(null, false, "", false, "Trigger is connected but null or invalid.");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(null, false, "", true, "Not triggered");
            return;
        }

        Boolean noAI = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_NO_AI_ID, setNoAI);
        Boolean invulnerable = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_INVULNERABLE_ID, setInvulnerable);
        Boolean persistent = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_PERSISTENT_ID, setPersistent);
        Double yaw = WorldWriteUtils.resolveOptionalFiniteDouble(this, INPUT_ROTATION_YAW_ID, 0.0d);
        Double pitch = WorldWriteUtils.resolveOptionalFiniteDouble(this, INPUT_ROTATION_PITCH_ID, 0.0d);
        if (noAI == null || invulnerable == null || persistent == null) {
            publish(null, false, "", false, "Boolean drive is connected but null or invalid.");
            return;
        }
        if (yaw == null || pitch == null) {
            publish(null, false, "", false, "Yaw/Pitch must be finite DOUBLE.");
            return;
        }

        if (!(inputValues.get(INPUT_POSITION_ID) instanceof PointData position)) {
            publish(null, false, "", false, "Position must be a POINT.");
            return;
        }
        if (!(inputValues.get(INPUT_ENTITY_TYPE_ID) instanceof String entityTypeId) || entityTypeId.isBlank()) {
            publish(null, false, "", false, "Entity Type is required.");
            return;
        }
        if (context == null || !(context.getWorld() instanceof ServerWorld world)) {
            publish(null, false, "", false, "Missing execution world");
            return;
        }
        BlockPos spawnPos = BlockPos.ofFloored(position.getX(), position.getY(), position.getZ());
        if (!WorldWriteUtils.isChunkLoaded(context, spawnPos)) {
            publish(null, false, "", false, WorldWriteUtils.UNLOADED_CHUNK_ERROR);
            return;
        }

        Identifier entityId;
        try {
            entityId = Identifier.of(entityTypeId);
        } catch (Exception e) {
            publish(null, false, "", false, "Invalid entity type id.");
            return;
        }
        if (!Registries.ENTITY_TYPE.containsId(entityId)) {
            publish(null, false, "", false, "Unknown entity type: " + entityTypeId);
            return;
        }
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(entityId);

        double x = position.getX();
        double y = position.getY();
        double z = position.getZ();
        float yawF = yaw.floatValue();
        float pitchF = pitch.floatValue();

        try {
            Object nbtDataObj = inputValues.get(INPUT_NBT_DATA_ID);
            Entity entity;
            if (nbtDataObj instanceof NbtCompound nbt) {
                entity = EntityType.loadEntityWithPassengers(entityType, nbt.copy(), world, SpawnReason.COMMAND, loaded -> {
                    loaded.refreshPositionAndAngles(x, y, z, yawF, pitchF);
                    return loaded;
                });
            } else {
                entity = entityType.create(world, SpawnReason.COMMAND);
                if (entity != null) {
                    entity.refreshPositionAndAngles(x, y, z, yawF, pitchF);
                }
            }

            if (entity == null) {
                publish(null, false, "", true, "Entity factory returned null.");
                return;
            }

            Object motionObj = inputValues.get(INPUT_MOTION_ID);
            if (applyMotion && motionObj instanceof Vector3d motion) {
                entity.setVelocity(motion.x, motion.y, motion.z);
            }
            entity.setInvulnerable(invulnerable);
            if (noAI) {
                entity.addCommandTag("nodecraft:no_ai");
            }
            if (persistent) {
                entity.addCommandTag("nodecraft:persistent");
            }

            boolean success = world.spawnEntity(entity);
            if (success) {
                publish(entity, true, entity.getUuidAsString(), true, "");
            } else {
                publish(null, false, "", true, "World rejected entity spawn.");
            }
        } catch (Exception e) {
            publish(null, false, "", true, "World write failed");
        }
    }

    private void publish(@Nullable Entity entity, boolean success, String uuid, boolean valid, String error) {
        outputValues.put(OUTPUT_ENTITY_ID, entity);
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_ENTITY_UUID_ID, uuid == null ? "" : uuid);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isApplyMotion() { return applyMotion; }
    public void setApplyMotion(boolean applyMotion) { this.applyMotion = applyMotion; markDirty(); }
    public boolean isSetNoAI() { return setNoAI; }
    public void setSetNoAI(boolean setNoAI) { this.setNoAI = setNoAI; markDirty(); }
    public boolean isSetInvulnerable() { return setInvulnerable; }
    public void setSetInvulnerable(boolean setInvulnerable) { this.setInvulnerable = setInvulnerable; markDirty(); }
    public boolean isSetPersistent() { return setPersistent; }
    public void setSetPersistent(boolean setPersistent) { this.setPersistent = setPersistent; markDirty(); }
    public boolean isTrigger() { return trigger; }
    public void setTrigger(boolean trigger) { this.trigger = trigger; markDirty(); }
}
