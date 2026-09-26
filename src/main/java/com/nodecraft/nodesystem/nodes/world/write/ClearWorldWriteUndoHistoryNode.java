package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.CONTEXT_WRITE,
    id = "world.write.clear_undo_history",
    displayName = "Clear World Write Undo History",
    description = "Clears recorded world.write undo history for the current actor and world",
    category = "world.write",
    order = 17
)
public class ClearWorldWriteUndoHistoryNode extends BaseNode {
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String OUTPUT_CLEARED_COUNT_ID = "output_cleared_count";
    private static final String OUTPUT_REMAINING_HISTORY_ID = "output_remaining_history";
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;
    private static final String OUTPUT_STATUS_ID = "output_status";

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    public ClearWorldWriteUndoHistoryNode() {
        super(UUID.randomUUID(), "world.write.clear_undo_history");
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_CLEARED_COUNT_ID, "Cleared Count", "Number of records removed from history", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMAINING_HISTORY_ID, "Remaining History", "Remaining undo records after clear", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether clear preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why clear did not run or failed", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Clear history status message", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(0, 0, false, "Trigger is connected but null or invalid.", "");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(0, 0, true, "", "Not triggered");
            return;
        }
        if (context == null || context.getWorld() == null) {
            publish(0, 0, false, "Missing execution world", "");
            return;
        }

        WorldWriteHistoryService service = WorldWriteHistoryService.getInstance();
        UUID actorId = WorldWriteHistoryService.resolveActorId(context.getPlayer());
        String worldKey = WorldWriteUtils.worldKey(context.getWorld());
        int clearedCount = service.size(actorId, worldKey);
        service.clear(actorId, worldKey);
        publish(clearedCount, service.size(actorId, worldKey), true, "",
            "Cleared " + clearedCount + " world.write undo records");
    }

    private void publish(int clearedCount, int remaining, boolean valid, String error, String status) {
        outputValues.put(OUTPUT_CLEARED_COUNT_ID, clearedCount);
        outputValues.put(OUTPUT_REMAINING_HISTORY_ID, remaining);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_STATUS_ID, status == null ? "" : status);
    }

    public boolean isTrigger() { return trigger; }
    public void setTrigger(boolean trigger) { this.trigger = trigger; markDirty(); }
}
