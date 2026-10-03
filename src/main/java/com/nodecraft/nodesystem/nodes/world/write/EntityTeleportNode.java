package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.entity_teleport",
    displayName = "Teleport Entity",
    description = "Teleports entities to a POINT destination in the current world (same-dimension only)",
    category = "world.write",
    order = 8
)
public class EntityTeleportNode extends BaseNode {

    private static final String INPUT_ENTITY_ID = "input_entity";
    private static final String INPUT_ENTITY_LIST_ID = "input_entity_list";
    private static final String INPUT_DESTINATION_ID = "input_destination";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_PRESERVE_ROTATION_ID = "input_preserve_rotation";
    private static final String INPUT_ROTATION_YAW_ID = "input_rotation_yaw";
    private static final String INPUT_ROTATION_PITCH_ID = "input_rotation_pitch";
    private static final String INPUT_RESET_FALL_DISTANCE_ID = "input_reset_fall_distance";
    private static final String INPUT_MAX_COUNT_ID = "input_max_count";

    private static final String OUTPUT_SUCCESS_COUNT_ID = "output_success_count";
    private static final String OUTPUT_FAILURE_COUNT_ID = "output_failure_count";
    private static final String OUTPUT_TOTAL_COUNT_ID = "output_total_count";
    private static final String OUTPUT_ALL_SUCCESS_ID = "output_all_success";
    private static final String OUTPUT_TELEPORTED_ENTITIES_ID = "output_teleported_entities";
    private static final String OUTPUT_COMPLETE_ID = WorldWriteUtils.OUTPUT_COMPLETE_ID;
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    private boolean preserveRotation = true;
    private boolean resetFallDistance = true;
    private int maxCount = GenerationLimits.MAX_WORLD_WRITE_ENTITIES;

    public EntityTeleportNode() {
        super(UUID.randomUUID(), "world.write.entity_teleport");

        addInputPort(new BasePort(INPUT_ENTITY_ID, "Entity", "Single entity to teleport", NodeDataType.MINECRAFT_ENTITY, this));
        addInputPort(new BasePort(INPUT_ENTITY_LIST_ID, "Entities", "Entities to teleport", NodeDataType.MINECRAFT_ENTITY_LIST, this));
        addInputPort(new BasePort(INPUT_DESTINATION_ID, "Destination", "Target POINT (same world)", NodeDataType.POINT, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_PRESERVE_ROTATION_ID, "Preserve Rotation", "Keep entity yaw/pitch", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_ROTATION_YAW_ID, "Yaw", "Yaw in degrees when Preserve Rotation is false", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_ROTATION_PITCH_ID, "Pitch", "Pitch in degrees when Preserve Rotation is false", NodeDataType.DOUBLE, this));
        addInputPort(new BasePort(INPUT_RESET_FALL_DISTANCE_ID, "Reset Fall Distance", "Clear fall distance after teleport", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_COUNT_ID, "Max Count", "User budget hard-capped by MAX_WORLD_WRITE_ENTITIES", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_COUNT_ID, "Success Count", "Entities teleported", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FAILURE_COUNT_ID, "Failure Count", "Entities that failed", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_TOTAL_COUNT_ID, "Total Count", "Entities attempted", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_ALL_SUCCESS_ID, "All Success", "Whether every attempt succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_TELEPORTED_ENTITIES_ID, "Teleported Entities", "Successfully teleported entities", NodeDataType.MINECRAFT_ENTITY_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete", "False when Max Count truncated or failures", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why teleport did not run or failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(0, 0, 0, false, List.of(), false, false, "Trigger is connected but null or invalid.");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(0, 0, 0, true, List.of(), true, true, "Not triggered");
            return;
        }

        Boolean preserve = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_PRESERVE_ROTATION_ID, preserveRotation);
        Boolean resetFall = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_RESET_FALL_DISTANCE_ID, resetFallDistance);
        Integer budget = WorldWriteUtils.resolveUserBudgetExactInteger(
            this, INPUT_MAX_COUNT_ID, maxCount, GenerationLimits.MAX_WORLD_WRITE_ENTITIES);
        if (preserve == null || resetFall == null) {
            publish(0, 0, 0, false, List.of(), false, false, "Boolean drive is connected but null or invalid.");
            return;
        }
        if (budget == null) {
            publish(0, 0, 0, false, List.of(), false, false,
                "Max Count must be an exact INTEGER between 1 and " + GenerationLimits.MAX_WORLD_WRITE_ENTITIES + ".");
            return;
        }

        Double yaw = null;
        Double pitch = null;
        if (!preserve) {
            yaw = WorldWriteUtils.resolveOptionalFiniteDouble(this, INPUT_ROTATION_YAW_ID, 0.0d);
            pitch = WorldWriteUtils.resolveOptionalFiniteDouble(this, INPUT_ROTATION_PITCH_ID, 0.0d);
            if (yaw == null || pitch == null) {
                publish(0, 0, 0, false, List.of(), false, false, "Yaw/Pitch must be finite DOUBLE when Preserve Rotation is false.");
                return;
            }
        }

        if (!(inputValues.get(INPUT_DESTINATION_ID) instanceof PointData destination)) {
            publish(0, 0, 0, false, List.of(), false, false, "Destination must be a POINT.");
            return;
        }
        if (context == null || !(context.getWorld() instanceof ServerWorld world)) {
            publish(0, 0, 0, false, List.of(), false, false, "Missing execution world");
            return;
        }
        BlockPos destPos = BlockPos.ofFloored(destination.getX(), destination.getY(), destination.getZ());
        if (!WorldWriteUtils.isChunkLoaded(context, destPos)) {
            publish(0, 0, 0, false, List.of(), false, false, WorldWriteUtils.UNLOADED_CHUNK_ERROR);
            return;
        }

        List<Entity> entities = collectEntities(inputValues.get(INPUT_ENTITY_ID), inputValues.get(INPUT_ENTITY_LIST_ID));
        if (entities == null) {
            publish(0, 0, 0, false, List.of(), false, false, "Invalid Entity / Entities payload.");
            return;
        }
        boolean hitLimit = entities.size() > budget;
        if (hitLimit) {
            entities = entities.subList(0, budget);
        }

        double x = destination.getX();
        double y = destination.getY();
        double z = destination.getZ();
        List<Entity> teleported = new ArrayList<>();
        int failureCount = 0;

        for (Entity entity : entities) {
            try {
                if (entity.getEntityWorld() != world) {
                    failureCount++;
                    continue;
                }
                if (!WorldWriteUtils.isChunkLoaded(context, entity.getBlockPos())) {
                    failureCount++;
                    continue;
                }
                float useYaw = preserve ? entity.getYaw() : yaw.floatValue();
                float usePitch = preserve ? entity.getPitch() : pitch.floatValue();
                if (entity instanceof ServerPlayerEntity player) {
                    player.refreshPositionAndAngles(x, y, z, useYaw, usePitch);
                    player.requestTeleport(x, y, z);
                } else {
                    entity.refreshPositionAndAngles(x, y, z, useYaw, usePitch);
                    entity.requestTeleport(x, y, z);
                }
                if (resetFall) {
                    entity.fallDistance = 0.0f;
                }
                teleported.add(entity);
            } catch (Exception e) {
                failureCount++;
            }
        }

        int successCount = teleported.size();
        boolean complete = !hitLimit && failureCount == 0;
        String error = failureCount > 0 ? "Partial teleport: " + failureCount + " failure(s)" : (hitLimit ? "Hit Max Count" : "");
        publish(successCount, failureCount, entities.size(), failureCount == 0, teleported, true, complete, error);
    }

    private static @Nullable List<Entity> collectEntities(@Nullable Object single, @Nullable Object listObj) {
        List<Entity> out = new ArrayList<>();
        if (single != null) {
            if (!(single instanceof Entity entity)) {
                return null;
            }
            out.add(entity);
        }
        if (listObj != null) {
            if (!(listObj instanceof List<?> list)) {
                return null;
            }
            for (Object entry : list) {
                if (!(entry instanceof Entity entity)) {
                    return null;
                }
                out.add(entity);
            }
        }
        return out;
    }

    private void publish(
        int successCount,
        int failureCount,
        int totalCount,
        boolean allSuccess,
        List<Entity> teleported,
        boolean valid,
        boolean complete,
        String error
    ) {
        outputValues.put(OUTPUT_SUCCESS_COUNT_ID, successCount);
        outputValues.put(OUTPUT_FAILURE_COUNT_ID, failureCount);
        outputValues.put(OUTPUT_TOTAL_COUNT_ID, totalCount);
        outputValues.put(OUTPUT_ALL_SUCCESS_ID, allSuccess);
        outputValues.put(OUTPUT_TELEPORTED_ENTITIES_ID, teleported);
        outputValues.put(OUTPUT_COMPLETE_ID, complete);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isTrigger() { return trigger; }
    public void setTrigger(boolean trigger) { this.trigger = trigger; markDirty(); }
    public boolean isPreserveRotation() { return preserveRotation; }
    public void setPreserveRotation(boolean preserveRotation) { this.preserveRotation = preserveRotation; markDirty(); }
    public boolean isResetFallDistance() { return resetFallDistance; }
    public void setResetFallDistance(boolean resetFallDistance) { this.resetFallDistance = resetFallDistance; markDirty(); }
}
