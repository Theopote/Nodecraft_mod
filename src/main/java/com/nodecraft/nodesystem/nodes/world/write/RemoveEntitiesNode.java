package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.remove_entities",
    displayName = "Remove Entities",
    description = "Removes entities from the world. Lookup by UUID/type belongs in world.query.",
    category = "world.write",
    order = 9
)
public class RemoveEntitiesNode extends BaseNode {

    private static final String INPUT_ENTITY_ID = "input_entity";
    private static final String INPUT_ENTITY_LIST_ID = "input_entity_list";
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String INPUT_DROP_ITEMS_ID = "input_drop_items";
    private static final String INPUT_MAX_COUNT_ID = "input_max_count";

    private static final String OUTPUT_REMOVED_COUNT_ID = "output_removed_count";
    private static final String OUTPUT_FAILURE_COUNT_ID = "output_failure_count";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_FAILED_ENTITIES_ID = "output_failed_entities";
    private static final String OUTPUT_COMPLETE_ID = WorldWriteUtils.OUTPUT_COMPLETE_ID;
    private static final String OUTPUT_HIT_LIMIT_ID = WorldWriteUtils.OUTPUT_HIT_LIMIT_ID;
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    private boolean dropItems = false;
    private int maxCount = GenerationLimits.MAX_WORLD_WRITE_ENTITIES;

    public RemoveEntitiesNode() {
        super(UUID.randomUUID(), "world.write.remove_entities");

        addInputPort(new BasePort(INPUT_ENTITY_ID, "Entity", "Single entity to remove", NodeDataType.MINECRAFT_ENTITY, this));
        addInputPort(new BasePort(INPUT_ENTITY_LIST_ID, "Entities", "Entities to remove", NodeDataType.MINECRAFT_ENTITY_LIST, this));
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_DROP_ITEMS_ID, "Drop Items", "Reserved; preferred path is discard without drops", NodeDataType.BOOLEAN, this));
        addInputPort(new BasePort(INPUT_MAX_COUNT_ID, "Max Count", "User budget hard-capped by MAX_WORLD_WRITE_ENTITIES", NodeDataType.INTEGER, this));

        addOutputPort(new BasePort(OUTPUT_REMOVED_COUNT_ID, "Removed Count", "Entities successfully removed", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_FAILURE_COUNT_ID, "Failure Count", "Entities that failed to remove", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether every attempted remove succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_FAILED_ENTITIES_ID, "Failed Entities", "Entities that could not be removed", NodeDataType.MINECRAFT_ENTITY_LIST, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete", "False when Max Count truncated or failures", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_HIT_LIMIT_ID, "Hit Limit", "True when Max Count truncated the input list", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why remove did not run or failed", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(0, 0, false, List.of(), false, false, false, "Trigger is connected but null or invalid.");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(0, 0, true, List.of(), true, false, true, "Not triggered");
            return;
        }

        Boolean drop = WorldWriteUtils.resolveOptionalBoolean(this, INPUT_DROP_ITEMS_ID, dropItems);
        Integer budget = WorldWriteUtils.resolveUserBudgetExactInteger(
            this, INPUT_MAX_COUNT_ID, maxCount, GenerationLimits.MAX_WORLD_WRITE_ENTITIES);
        if (drop == null) {
            publish(0, 0, false, List.of(), false, false, false, "Drop Items is connected but null or invalid.");
            return;
        }
        if (budget == null) {
            publish(0, 0, false, List.of(), false, false, false,
                "Max Count must be an exact INTEGER between 1 and " + GenerationLimits.MAX_WORLD_WRITE_ENTITIES + ".");
            return;
        }
        if (context == null || !(context.getWorld() instanceof ServerWorld)) {
            publish(0, 0, false, List.of(), false, false, false, "Missing execution world");
            return;
        }

        List<Entity> entities = collectEntities(inputValues.get(INPUT_ENTITY_ID), inputValues.get(INPUT_ENTITY_LIST_ID));
        if (entities == null) {
            publish(0, 0, false, List.of(), false, false, false, "Invalid Entity / Entities payload.");
            return;
        }
        boolean hitLimit = entities.size() > budget;
        if (hitLimit) {
            entities = new ArrayList<>(entities.subList(0, budget));
        }

        int removed = 0;
        List<Entity> failed = new ArrayList<>();
        for (Entity entity : entities) {
            try {
                // Prefer discard; drop-items path is not fully modeled for arbitrary entity types.
                entity.discard();
                removed++;
            } catch (Exception e) {
                failed.add(entity);
            }
        }

        boolean success = failed.isEmpty();
        boolean complete = success && !hitLimit;
        String error = !failed.isEmpty()
            ? "Partial remove: " + failed.size() + " failure(s)"
            : (hitLimit ? "Hit Max Count" : "");
        publish(removed, failed.size(), success, failed, true, hitLimit, complete, error);
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
        int removedCount,
        int failureCount,
        boolean success,
        List<Entity> failed,
        boolean valid,
        boolean hitLimit,
        boolean complete,
        String error
    ) {
        outputValues.put(OUTPUT_REMOVED_COUNT_ID, removedCount);
        outputValues.put(OUTPUT_FAILURE_COUNT_ID, failureCount);
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_FAILED_ENTITIES_ID, failed);
        outputValues.put(OUTPUT_HIT_LIMIT_ID, hitLimit);
        outputValues.put(OUTPUT_COMPLETE_ID, complete);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    public boolean isDropItems() { return dropItems; }
    public void setDropItems(boolean dropItems) { this.dropItems = dropItems; markDirty(); }
    public boolean isTrigger() { return trigger; }
    public void setTrigger(boolean trigger) { this.trigger = trigger; markDirty(); }
}
