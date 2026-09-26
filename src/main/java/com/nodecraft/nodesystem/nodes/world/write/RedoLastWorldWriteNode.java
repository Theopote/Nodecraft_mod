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
    effect = NodeEffect.WORLD_WRITE,
    id = "world.write.redo_last_write",
    displayName = "Redo Last World Write",
    description = "Reapplies the most recently undone world.write block operation",
    category = "world.write",
    order = 15
)
public class RedoLastWorldWriteNode extends BaseNode {
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_REMAINING_REDO_ID = "output_remaining_redo";
    private static final String OUTPUT_REMAINING_HISTORY_ID = "output_remaining_history";
    private static final String OUTPUT_COMPLETE_ID = WorldWriteUtils.OUTPUT_COMPLETE_ID;
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;
    private static final String OUTPUT_STATUS_ID = "output_status";

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    public RedoLastWorldWriteNode() {
        super(UUID.randomUUID(), "world.write.redo_last_write");
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether a redo record was applied", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_REMAINING_REDO_ID, "Remaining Redo", "Remaining redo records", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMAINING_HISTORY_ID, "Remaining History", "Undo records after redo", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete", "False when redo was partial", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why redo did not run or failed", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Redo status message", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteUtils.TriggerResult triggerResult = WorldWriteUtils.resolveWriteTrigger(this, trigger);
        if (triggerResult == WorldWriteUtils.TriggerResult.FAIL) {
            publish(false, 0, 0, false, false, "Trigger is connected but null or invalid.", "");
            return;
        }
        if (triggerResult == WorldWriteUtils.TriggerResult.SKIP) {
            publish(false, 0, 0, true, true, "", "Not triggered");
            return;
        }
        if (context == null || context.getWorld() == null) {
            publish(false, 0, 0, false, false, "Missing execution context", "");
            return;
        }

        WorldWriteHistoryService service = WorldWriteHistoryService.getInstance();
        UUID actorId = WorldWriteHistoryService.resolveActorId(context.getPlayer());
        if (service.redoSize(actorId, context.getWorld()) == 0) {
            publish(false, 0, service.size(actorId, context.getWorld()), true, true, "", "No recorded world.write redo history");
            return;
        }

        WorldWriteHistoryService.UndoApplyResult result = service.redoLast(actorId, context);
        publish(
            result.success(),
            service.redoSize(actorId, context.getWorld()),
            service.size(actorId, context.getWorld()),
            true,
            result.complete(),
            result.error(),
            result.success() ? "Redid last world.write operation" : "Redo failed"
        );
    }

    private void publish(
        boolean success,
        int remainingRedo,
        int remainingHistory,
        boolean valid,
        boolean complete,
        String error,
        String status
    ) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_REMAINING_REDO_ID, remainingRedo);
        outputValues.put(OUTPUT_REMAINING_HISTORY_ID, remainingHistory);
        outputValues.put(OUTPUT_COMPLETE_ID, complete);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_STATUS_ID, status == null ? "" : status);
    }

    public boolean isTrigger() { return trigger; }
    public void setTrigger(boolean trigger) { this.trigger = trigger; markDirty(); }
}
