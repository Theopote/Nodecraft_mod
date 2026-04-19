package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
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
    id = "world.write.spawn_entity",
    displayName = "Spawn Entity",
    description = "Spawns an entity into the world at a given position",
    category = "world.write"
)
public class SpawnEntityNode extends BaseNode {

    private static final String INPUT_POSITION_ID = "input_position";
    private static final String INPUT_ENTITY_TYPE_ID = "input_entity_type";
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

    private boolean applyMotion = false;
    private boolean setNoAI = false;
    private boolean setInvulnerable = false;
    private boolean setPersistent = true;

    public SpawnEntityNode() {
        super(UUID.randomUUID(), "world.write.spawn_entity");

        addInputPort(new BasePort(INPUT_POSITION_ID, "Position", "Spawn position", NodeDataType.POSITION, this));
        addInputPort(new BasePort(INPUT_ENTITY_TYPE_ID, "Entity Type", "Entity registry id", NodeDataType.ENTITY_TYPE, this));
        addInputPort(new BasePort(INPUT_NBT_DATA_ID, "NBT Data", "Optional entity NBT", NodeDataType.NBT, this));
        addInputPort(new BasePort(INPUT_MOTION_ID, "Motion", "Optional initial motion vector", NodeDataType.VECTOR, this));
        addInputPort(new BasePort(INPUT_NO_AI_ID, "No AI", "Whether to disable AI for mob entities", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_INVULNERABLE_ID, "Invulnerable", "Whether the entity should be invulnerable", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_PERSISTENT_ID, "Persistent", "Whether the entity should be persistent", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_ROTATION_YAW_ID, "Yaw", "Spawn yaw", NodeDataType.FLOAT, this));
        addInputPort(new BasePort(INPUT_ROTATION_PITCH_ID, "Pitch", "Spawn pitch", NodeDataType.FLOAT, this));

        addOutputPort(new BasePort(OUTPUT_ENTITY_ID, "Entity", "Spawned entity instance", NodeDataType.MINECRAFT_ENTITY, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether the entity was spawned", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ENTITY_UUID_ID, "Entity UUID", "UUID of the spawned entity", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Spawns an entity into the world at a given position";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Entity entityObj = null;
        boolean success = false;
        String entityUUID = "";

        Object positionObj = inputValues.get(INPUT_POSITION_ID);
        Object entityTypeObj = inputValues.get(INPUT_ENTITY_TYPE_ID);
        Object nbtDataObj = inputValues.get(INPUT_NBT_DATA_ID);
        Object motionObj = inputValues.get(INPUT_MOTION_ID);

        boolean noAIValue = inputValues.get(INPUT_NO_AI_ID) instanceof Boolean value ? value : setNoAI;
        boolean invulnerableValue = inputValues.get(INPUT_INVULNERABLE_ID) instanceof Boolean value ? value : setInvulnerable;
        boolean persistentValue = inputValues.get(INPUT_PERSISTENT_ID) instanceof Boolean value ? value : setPersistent;
        float yaw = inputValues.get(INPUT_ROTATION_YAW_ID) instanceof Number value ? value.floatValue() : 0.0f;
        float pitch = inputValues.get(INPUT_ROTATION_PITCH_ID) instanceof Number value ? value.floatValue() : 0.0f;

        if (context != null
            && context.getWorld() instanceof ServerWorld world
            && positionObj != null
            && entityTypeObj instanceof String entityTypeId
            && !entityTypeId.isBlank()) {
            try {
                Identifier entityId = Identifier.of(entityTypeId);
                EntityType<?> entityType = Registries.ENTITY_TYPE.get(entityId);
                if (entityType == null) {
                    publish(entityObj, success, entityUUID);
                    return;
                }

                double x;
                double y;
                double z;
                BlockPos blockPos;
                if (positionObj instanceof Vector3d pos) {
                    x = pos.x;
                    y = pos.y;
                    z = pos.z;
                    blockPos = BlockPos.ofFloored(x, y, z);
                } else if (positionObj instanceof BlockPos pos) {
                    x = pos.getX() + 0.5;
                    y = pos.getY();
                    z = pos.getZ() + 0.5;
                    blockPos = pos;
                } else {
                    publish(entityObj, success, entityUUID);
                    return;
                }

                Entity entity;
                if (nbtDataObj instanceof NbtCompound nbt) {
                    entity = EntityType.loadEntityWithPassengers(entityType, nbt.copy(), world, SpawnReason.COMMAND, loaded -> {
                        loaded.refreshPositionAndAngles(x, y, z, yaw, pitch);
                        return loaded;
                    });
                } else {
                    entity = entityType.create(world, SpawnReason.COMMAND);
                    if (entity != null) {
                        entity.refreshPositionAndAngles(x, y, z, yaw, pitch);
                    }
                }

                if (entity != null) {
                    if (applyMotion && motionObj instanceof Vector3d motion) {
                        entity.setVelocity(motion.x, motion.y, motion.z);
                    }
                    entity.setInvulnerable(invulnerableValue);
                    if (noAIValue) {
                        entity.addCommandTag("nodecraft:no_ai");
                    }
                    if (persistentValue) {
                        entity.addCommandTag("nodecraft:persistent");
                    }

                    success = world.spawnEntity(entity);
                    if (success) {
                        entityObj = entity;
                        entityUUID = entity.getUuidAsString();
                    }
                }
            } catch (Exception e) {
                System.err.println("Error spawning entity: " + e.getMessage());
            }
        }

        publish(entityObj, success, entityUUID);
    }

    private void publish(Entity entityObj, boolean success, String entityUUID) {
        outputValues.put(OUTPUT_ENTITY_ID, entityObj);
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_ENTITY_UUID_ID, entityUUID);
    }

    public boolean isApplyMotion() {
        return applyMotion;
    }

    public void setApplyMotion(boolean applyMotion) {
        this.applyMotion = applyMotion;
        markDirty();
    }

    public boolean isSetNoAI() {
        return setNoAI;
    }

    public void setSetNoAI(boolean setNoAI) {
        this.setNoAI = setNoAI;
        markDirty();
    }

    public boolean isSetInvulnerable() {
        return setInvulnerable;
    }

    public void setSetInvulnerable(boolean setInvulnerable) {
        this.setInvulnerable = setInvulnerable;
        markDirty();
    }

    public boolean isSetPersistent() {
        return setPersistent;
    }

    public void setSetPersistent(boolean setPersistent) {
        this.setPersistent = setPersistent;
        markDirty();
    }
}
