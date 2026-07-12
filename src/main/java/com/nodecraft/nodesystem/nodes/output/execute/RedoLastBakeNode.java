package com.nodecraft.nodesystem.nodes.output.execute;

import com.nodecraft.gui.editor.impl.BaseCustomUINode;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "output.execute.redo_last_bake",
    displayName = "Redo Last Bake",
    description = "Reapplies the most recently undone bake or apply-changes operation",
    category = "output.execute",
    order = 3
)
public class RedoLastBakeNode extends BaseCustomUINode {

    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_REMAINING_REDO_ID = "output_remaining_redo";
    private static final String OUTPUT_REMAINING_HISTORY_ID = "output_remaining_history";
    private static final String OUTPUT_STATUS_ID = "output_status";

    public RedoLastBakeNode() {
        super(UUID.randomUUID(), "output.execute.redo_last_bake");
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Any non-null value triggers redo", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether a redo record was applied", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_REMAINING_REDO_ID, "Remaining Redo", "Number of remaining redo records", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMAINING_HISTORY_ID, "Remaining History", "Number of undo records after redo", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Redo status message", NodeDataType.STRING, this));
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        BakePlacementService service = BakePlacementService.getInstance();
        UUID actorId = context != null ? BakePlacementService.resolveActorId(context.getPlayer()) : BakePlacementService.SERVER_ACTOR_ID;
        boolean success = false;
        String status = "No redo executed";

        if (inputValues.get(INPUT_TRIGGER_ID) != null) {
            if (context == null || context.getWorld() == null) {
                status = "Missing execution context";
            } else if (service.getHistory(actorId).redoSize() == 0) {
                status = "No recorded bake redo history";
            } else {
                success = service.redoLast(actorId, context.getWorld());
                status = success ? "Redid last bake operation" : "Redo failed";
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_REMAINING_REDO_ID, service.getHistory(actorId).redoSize());
        outputValues.put(OUTPUT_REMAINING_HISTORY_ID, service.getHistory(actorId).size());
        outputValues.put(OUTPUT_STATUS_ID, status);
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
