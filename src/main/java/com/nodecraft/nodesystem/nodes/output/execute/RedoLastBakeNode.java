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
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "output.execute.redo_last_bake",
    displayName = "Redo Last Change",
    description = "Reapplies the most recently undone world mutation from the unified history stack",
    category = "output.execute",
    order = 3
)
public class RedoLastBakeNode extends BaseCustomUINode {

    @NodeProperty(displayName = "Use Async", category = "Execution", order = 1)
    private boolean useAsync = true;

    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_REMAINING_REDO_ID = "output_remaining_redo";
    private static final String OUTPUT_REMAINING_HISTORY_ID = "output_remaining_history";
    private static final String OUTPUT_STATUS_ID = "output_status";
    private static final String OUTPUT_TASK_ID = "output_task_id";

    public RedoLastBakeNode() {
        super(UUID.randomUUID(), "output.execute.redo_last_bake");
        addInputPort(new BasePort(
            INPUT_TRIGGER_ID,
            "Trigger",
            "EXEC pulse to redo the last undone bake",
            NodeDataType.EXEC,
            this,
            false,
            false
        ));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether a redo record was applied or queued", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_REMAINING_REDO_ID, "Remaining Redo", "Number of remaining redo records", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMAINING_HISTORY_ID, "Remaining History", "Number of undo records after redo", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Redo status message", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_TASK_ID, "Task ID", "Task UUID for async operations (empty for sync)", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BakePlacementService service = BakePlacementService.getInstance();
        UUID actorId = context != null ? BakePlacementService.resolveActorId(context.getPlayer()) : BakePlacementService.SERVER_ACTOR_ID;
        BakeHistory history = context != null
            ? service.getHistory(actorId, context.getWorld())
            : service.getHistory(actorId);
        boolean success = false;
        String status = "No redo executed";
        String taskId = "";

        if (Boolean.TRUE.equals(inputValues.get(INPUT_TRIGGER_ID))) {
            if (context == null || context.getWorld() == null) {
                status = "Missing execution context";
            } else if (history.redoSize() == 0) {
                status = "No recorded mutation redo history";
            } else {
                BakeHistory.UndoRecord record = history.peekRedo();
                int redoCount = record == null ? 0 : record.size();
                if (!useAsync && redoCount > GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS) {
                    status = "Sync redo exceeds MAX_SYNC_WORLD_WRITE_BLOCKS ("
                        + GenerationLimits.MAX_SYNC_WORLD_WRITE_BLOCKS + "); use async";
                } else if (useAsync) {
                    UUID redoTaskId = service.redoLastAsync(actorId, context.getWorld());
                    success = redoTaskId != null;

                    if (success) {
                        taskId = redoTaskId.toString();
                        status = "Queued async redo (Task: " + taskId + ")";
                    } else {
                        status = "Async redo failed to queue";
                    }
                } else {
                    success = service.redoLast(actorId, context.getWorld());
                    status = success
                        ? "Redid last mutation (sync; waited for terminal bake state)"
                        : "Redo failed";
                }
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_REMAINING_REDO_ID, history.redoSize());
        outputValues.put(OUTPUT_REMAINING_HISTORY_ID, history.size());
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
