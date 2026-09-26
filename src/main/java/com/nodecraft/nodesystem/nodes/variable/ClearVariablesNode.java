package com.nodecraft.nodesystem.nodes.variable;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.CONTEXT_WRITE,
    id = "variable.clear",
    displayName = "Clear Variables",
    description = "Clears user variables from the execution scope. Internal NodeCraft variables are always preserved.",
    category = "variable",
    order = 5
)
public class ClearVariablesNode extends BaseNode {

    private static final String INPUT_CLEAR_ID = "input_clear";

    private static final String OUTPUT_CLEARED_COUNT_ID = "output_cleared_count";
    private static final String OUTPUT_CLEARED_ID = "output_cleared";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public ClearVariablesNode() {
        super(UUID.randomUUID(), "variable.clear");

        addInputPort(new BasePort(INPUT_CLEAR_ID, "Clear", "When true, clears user variables", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_CLEARED_COUNT_ID, "Cleared Count", "Number of variables removed", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_CLEARED_ID, "Cleared", "Whether a clear operation was requested", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether clear input resolved correctly", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when clear input is invalid", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Clear Variables";
    }

    @Override
    public String getDescription() {
        return "Clears user variables from the execution scope. Internal NodeCraft variables are always preserved.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Boolean clear = OptionalPortDrive.resolveOptionalBoolean(this, INPUT_CLEAR_ID, false);
        if (clear == null) {
            outputValues.put(OUTPUT_CLEARED_COUNT_ID, 0);
            outputValues.put(OUTPUT_CLEARED_ID, false);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, "Clear is connected but null or invalid.");
            return;
        }

        int clearedCount = clear ? VariableScopeBridge.clear(context) : 0;

        outputValues.put(OUTPUT_CLEARED_COUNT_ID, clearedCount);
        outputValues.put(OUTPUT_CLEARED_ID, clear);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }
}
