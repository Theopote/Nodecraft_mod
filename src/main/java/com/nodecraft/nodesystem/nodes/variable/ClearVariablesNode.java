package com.nodecraft.nodesystem.nodes.variable;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.PURE,
    id = "variable.clear",
    displayName = "Clear Variables",
    description = "Clears user variables from the execution scope.",
    category = "variable",
    order = 5
)
public class ClearVariablesNode extends BaseNode {

    @NodeProperty(displayName = "Include Internal Variables", category = "Variable", order = 1)
    private boolean includeInternalVariables = false;

    private static final String INPUT_CLEAR_ID = "input_clear";

    private static final String OUTPUT_CLEARED_COUNT_ID = "output_cleared_count";
    private static final String OUTPUT_CLEARED_ID = "output_cleared";

    public ClearVariablesNode() {
        super(UUID.randomUUID(), "variable.clear");

        addInputPort(new BasePort(INPUT_CLEAR_ID, "Clear", "When true, clears variables", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_CLEARED_COUNT_ID, "Cleared Count", "Number of variables removed", NodeDataType.INTEGER, this));
        addOutputPort(new BasePort(OUTPUT_CLEARED_ID, "Cleared", "Whether a clear operation was requested", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Clear Variables";
    }

    @Override
    public String getDescription() {
        return "Clears user variables from the execution scope. Internal NodeCraft variables are preserved unless Include Internal Variables is enabled.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean clear = Boolean.TRUE.equals(inputValues.get(INPUT_CLEAR_ID));
        int clearedCount = clear ? VariableScopeBridge.clear(context, includeInternalVariables) : 0;

        outputValues.put(OUTPUT_CLEARED_COUNT_ID, clearedCount);
        outputValues.put(OUTPUT_CLEARED_ID, clear);
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("includeInternalVariables", includeInternalVariables);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object includeInternalVariablesValue = map.get("includeInternalVariables");
        if (includeInternalVariablesValue instanceof Boolean value) {
            includeInternalVariables = value;
        }
    }
}
