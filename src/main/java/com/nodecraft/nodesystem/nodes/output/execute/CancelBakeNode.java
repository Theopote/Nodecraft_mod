package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.WORLD_WRITE,
    id = "output.execute.cancel_bake",
    displayName = "Cancel Bake",
    description = "Cancels an in-flight bake or apply-changes task owned by the current actor and rolls back its in-world progress",
    category = "output.execute",
    order = 4
)
public class CancelBakeNode extends BaseNode {

    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String INPUT_TASK_ID_ID = "input_task_id";

    private static final String OUTPUT_ACCEPTED_ID = "output_accepted";
    private static final String OUTPUT_PREVIOUS_STATE_ID = "output_previous_state";
    private static final String OUTPUT_STATE_ID = "output_state";
    private static final String OUTPUT_TASK_ID = "output_task_id";
    private static final String OUTPUT_STATUS_ID = "output_status";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public CancelBakeNode() {
        super(UUID.randomUUID(), "output.execute.cancel_bake");
        addInputPort(new BasePort(
            INPUT_TRIGGER_ID,
            "Trigger",
            "EXEC pulse to cancel the bake task",
            NodeDataType.EXEC,
            this,
            false,
            false
        ));
        addInputPort(new BasePort(INPUT_TASK_ID_ID, "Task ID", "Bake task UUID to cancel", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_ACCEPTED_ID, "Accepted", "Whether cancel was accepted", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_PREVIOUS_STATE_ID, "Previous State", "Task state before cancel", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_STATE_ID, "State", "Task state after cancel attempt", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_TASK_ID, "Task ID", "Task UUID that was targeted", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Cancel status message", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether preflight succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why cancel did not run", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean accepted = false;
        String previousState = "";
        String state = "";
        String taskIdText = "";
        String status = "No cancel executed";
        boolean valid = true;
        String error = "";

        if (!Boolean.TRUE.equals(inputValues.get(INPUT_TRIGGER_ID))) {
            publish(accepted, previousState, state, taskIdText, status, valid, error);
            return;
        }

        Object taskIdObj = inputValues.get(INPUT_TASK_ID_ID);
        taskIdText = taskIdObj == null ? "" : taskIdObj.toString().trim();
        if (taskIdText.isEmpty()) {
            publish(false, "", "", "", "Missing Task ID", false, "Missing Task ID");
            return;
        }

        UUID taskId;
        try {
            taskId = UUID.fromString(taskIdText);
        } catch (IllegalArgumentException e) {
            publish(false, "", "", taskIdText, "Invalid Task ID", false, "Invalid Task ID");
            return;
        }

        BakePlacementService service = BakePlacementService.getInstance();
        UUID actorId = BakePlacementService.resolveActorId(context != null ? context.getPlayer() : null);
        BakePlacementService.TaskSnapshot snapshot = service.getTaskSnapshot(taskId);
        if (snapshot == null) {
            publish(false, "Not Found", "Not Found", taskId.toString(), "Task not found", true, "");
            return;
        }
        if (!service.canAccessTask(actorId, taskId)) {
            publish(false, snapshot.resolveState(), snapshot.resolveState(), taskId.toString(),
                "Task is not owned by the current actor", false, "Task is not owned by the current actor");
            return;
        }

        previousState = snapshot.resolveState();
        accepted = service.cancelTask(taskId);
        BakePlacementService.TaskSnapshot after = service.getTaskSnapshot(taskId);
        state = after != null ? after.resolveState() : previousState;
        status = accepted ? "Cancel accepted" : "Cancel was not accepted";
        publish(accepted, previousState, state, taskId.toString(), status, true, accepted ? "" : status);
    }

    private void publish(
        boolean accepted,
        String previousState,
        String state,
        String taskId,
        String status,
        boolean valid,
        String error
    ) {
        outputValues.put(OUTPUT_ACCEPTED_ID, accepted);
        outputValues.put(OUTPUT_PREVIOUS_STATE_ID, previousState);
        outputValues.put(OUTPUT_STATE_ID, state);
        outputValues.put(OUTPUT_TASK_ID, taskId);
        outputValues.put(OUTPUT_STATUS_ID, status);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }
}
