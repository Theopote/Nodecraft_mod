package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.bake.BakeHistory;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "output.execute.undo_last_bake",
    displayName = "Undo Last Bake",
    description = "Reverts the most recent recorded bake or apply-changes operation",
    category = "output.execute",
    order = 2
)
public class UndoLastBakeNode extends BaseCustomUINode {

    @NodeProperty(displayName = "Use Async", category = "Execution", order = 1)
    private boolean useAsync = true;

    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_RESTORED_COUNT_ID = "output_restored_count";
    private static final String OUTPUT_REMAINING_HISTORY_ID = "output_remaining_history";
    private static final String OUTPUT_STATUS_ID = "output_status";
    private static final String OUTPUT_TASK_ID = "output_task_id";

    public UndoLastBakeNode() {
        super(UUID.randomUUID(), "output.execute.undo_last_bake");
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Any non-null value triggers undo", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether an undo record was restored or queued", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESTORED_COUNT_ID, "Restored Count", "Number of blocks to restore (for sync mode) or queued (for async mode)", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMAINING_HISTORY_ID, "Remaining History", "Number of remaining undo records", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Undo status message", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_TASK_ID, "Task ID", "Task UUID for async operations (empty for sync)", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean success = false;
        int restoredCount = 0;
        UUID actorId = context != null ? BakePlacementService.resolveActorId(context.getPlayer()) : BakePlacementService.SERVER_ACTOR_ID;
        BakePlacementService service = BakePlacementService.getInstance();
        int remainingHistory = service.getHistory(actorId).size();
        String status = "No undo executed";
        String taskId = "";

        if (inputValues.get(INPUT_TRIGGER_ID) != null) {
            if (context == null || context.getWorld() == null) {
                status = "Missing execution context";
            } else {
                BakeHistory history = service.getHistory(actorId);
                BakeHistory.UndoRecord record = history.peek();

                if (record == null) {
                    status = "No recorded bake history";
                } else {
                    restoredCount = record.size();

                    if (useAsync) {
                        // Use async undo - prevents server lag on large builds
                        UUID undoTaskId = service.undoLastAsync(actorId, context.getWorld());
                        success = undoTaskId != null;
                        remainingHistory = history.size();

                        if (success) {
                            taskId = undoTaskId.toString();
                            status = "Queued async undo of " + restoredCount + " blocks (Task: " + taskId + ")";
                        } else {
                            status = "Async undo failed to queue";
                            restoredCount = 0;
                        }
                    } else {
                        // Use sync undo - WARNING: can cause lag on large builds
                        success = service.undoLast(actorId, context.getWorld());
                        remainingHistory = history.size();
                        status = success
                            ? "Restored " + restoredCount + " blocks (sync)"
                            : "Undo failed";
                        if (!success) {
                            restoredCount = 0;
                        }
                    }
                }
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_RESTORED_COUNT_ID, restoredCount);
        outputValues.put(OUTPUT_REMAINING_HISTORY_ID, remainingHistory);
        outputValues.put(OUTPUT_STATUS_ID, status);
        outputValues.put(OUTPUT_TASK_ID, taskId);
    }

    public boolean isUseAsync() {
        return useAsync;
    }

    public void setUseAsync(boolean value) {
        if (useAsync != value) {
            useAsync = value;
            markDirty();
        }
    }

    @Override
    protected float calculateUIHeight() {
        return 0.0f;
    }

    @Override
    protected float calculateMinUIWidth() {
        return 0.0f;
    }

    @Override
    protected boolean renderCustomUIScaled(float width, float height, float zoom) {
        return false;
    }
}
