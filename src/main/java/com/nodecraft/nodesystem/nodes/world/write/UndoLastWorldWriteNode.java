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
    id = "world.write.undo_last_write",
    displayName = "Undo Last World Write",
    description = "Reverts the most recent recorded world.write block placement operation",
    category = "world.write",
    order = 14
)
public class UndoLastWorldWriteNode extends BaseNode {
    private static final String INPUT_TRIGGER_ID = WorldWriteUtils.INPUT_TRIGGER_ID;
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_RESTORED_COUNT_ID = "output_restored_count";
    private static final String OUTPUT_REMAINING_HISTORY_ID = "output_remaining_history";
    private static final String OUTPUT_COMPLETE_ID = WorldWriteUtils.OUTPUT_COMPLETE_ID;
    private static final String OUTPUT_VALID_ID = WorldWriteUtils.OUTPUT_VALID_ID;
    private static final String OUTPUT_ERROR_ID = WorldWriteUtils.OUTPUT_ERROR_ID;
    private static final String OUTPUT_STATUS_ID = "output_status";

    @NodeProperty(displayName = "Trigger", category = "Execution", order = 0)
    private boolean trigger = false;

    public UndoLastWorldWriteNode() {
        super(UUID.randomUUID(), "world.write.undo_last_write");
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Optional arming gate; connected invalid fails closed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether an undo record was restored", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESTORED_COUNT_ID, "Restored Count", "Number of blocks restored", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMAINING_HISTORY_ID, "Remaining History", "Remaining world.write undo records", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_COMPLETE_ID, "Complete", "False when undo was partial", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why undo did not run or failed", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Undo status message", NodeDataType.STRING, this));
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
        WorldWriteHistoryService.UndoRecord peek = service.peek(actorId, context.getWorld());
        if (peek == null || peek.size() == 0) {
            publish(false, 0, service.size(actorId, context.getWorld()), true, true, "", "No recorded world.write history");
            return;
        }

        WorldWriteHistoryService.UndoApplyResult result = service.undoLast(actorId, context);
        publish(
            result.success(),
            result.successCount(),
            service.size(actorId, context.getWorld()),
            true,
            result.complete(),
            result.error(),
            result.success()
                ? (result.complete() ? "Restored " + result.successCount() + " blocks" : "Partial restore")
                : "Undo failed"
        );
    }

    private void publish(
        boolean success,
        int restoredCount,
        int remaining,
        boolean valid,
        boolean complete,
        String error,
        String status
    ) {
        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_RESTORED_COUNT_ID, restoredCount);
        outputValues.put(OUTPUT_REMAINING_HISTORY_ID, remaining);
        outputValues.put(OUTPUT_COMPLETE_ID, complete);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_STATUS_ID, status == null ? "" : status);
    }

    public boolean isTrigger() { return trigger; }
    public void setTrigger(boolean trigger) { this.trigger = trigger; markDirty(); }
}
