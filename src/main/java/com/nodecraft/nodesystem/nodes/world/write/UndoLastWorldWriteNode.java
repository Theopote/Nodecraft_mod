package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "world.write.undo_last_write",
    displayName = "Undo Last World Write",
    description = "Reverts the most recent recorded world.write block placement operation",
    category = "world.write",
    order = 99
)
public class UndoLastWorldWriteNode extends BaseNode {
    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_RESTORED_COUNT_ID = "output_restored_count";
    private static final String OUTPUT_REMAINING_HISTORY_ID = "output_remaining_history";
    private static final String OUTPUT_STATUS_ID = "output_status";

    public UndoLastWorldWriteNode() {
        super(UUID.randomUUID(), "world.write.undo_last_write");
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Any non-null value triggers undo", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether an undo record was restored", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_RESTORED_COUNT_ID, "Restored Count", "Number of blocks restored", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMAINING_HISTORY_ID, "Remaining History", "Remaining world.write undo records", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Undo status message", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Reverts the most recent recorded world.write block placement operation";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteHistoryService history = WorldWriteHistoryService.getInstance();
        boolean success = false;
        int restoredCount = 0;
        int remaining = history.size();
        String status = "No undo executed";

        if (inputValues.get(INPUT_TRIGGER_ID) != null) {
            if (context == null || context.getWorld() == null) {
                status = "Missing execution context";
            } else {
                WorldWriteHistoryService.UndoRecord record = history.pop();
                if (record == null) {
                    status = "No recorded world.write history";
                } else {
                    restoredCount = record.size();
                    success = history.undoLast(context.getWorld());
                    remaining = history.size();
                    status = success ? "Restored " + restoredCount + " blocks" : "Undo failed";
                    if (!success) {
                        restoredCount = 0;
                    }
                }
            }
        }

        outputValues.put(OUTPUT_SUCCESS_ID, success);
        outputValues.put(OUTPUT_RESTORED_COUNT_ID, restoredCount);
        outputValues.put(OUTPUT_REMAINING_HISTORY_ID, remaining);
        outputValues.put(OUTPUT_STATUS_ID, status);
    }
}
