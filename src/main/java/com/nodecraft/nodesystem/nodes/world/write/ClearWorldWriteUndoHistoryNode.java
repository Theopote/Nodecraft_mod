package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    id = "world.write.clear_undo_history",
    displayName = "Clear World Write Undo History",
    description = "Clears all recorded world.write undo history entries",
    category = "world.write",
    order = 101
)
public class ClearWorldWriteUndoHistoryNode extends BaseNode {
    private static final String INPUT_TRIGGER_ID = "input_trigger";
    private static final String OUTPUT_CLEARED_COUNT_ID = "output_cleared_count";
    private static final String OUTPUT_REMAINING_HISTORY_ID = "output_remaining_history";
    private static final String OUTPUT_STATUS_ID = "output_status";

    public ClearWorldWriteUndoHistoryNode() {
        super(UUID.randomUUID(), "world.write.clear_undo_history");
        addInputPort(new BasePort(INPUT_TRIGGER_ID, "Trigger", "Any non-null value clears history", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_CLEARED_COUNT_ID, "Cleared Count", "Number of records removed from history", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_REMAINING_HISTORY_ID, "Remaining History", "Remaining undo records after clear", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_STATUS_ID, "Status", "Clear history status message", NodeDataType.STRING, this));
    }

    @Override
    public String getDescription() {
        return "Clears all recorded world.write undo history entries";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        WorldWriteHistoryService history = WorldWriteHistoryService.getInstance();
        int clearedCount = 0;
        int remainingHistory = history.size();
        String status = "No clear executed";

        if (inputValues.get(INPUT_TRIGGER_ID) != null) {
            clearedCount = history.size();
            history.clear();
            remainingHistory = history.size();
            status = "Cleared " + clearedCount + " world.write undo records";
        }

        outputValues.put(OUTPUT_CLEARED_COUNT_ID, clearedCount);
        outputValues.put(OUTPUT_REMAINING_HISTORY_ID, remainingHistory);
        outputValues.put(OUTPUT_STATUS_ID, status);
    }
}

