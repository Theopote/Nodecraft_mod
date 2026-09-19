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
    id = "variable.remove",
    displayName = "Remove Variable",
    description = "Removes a user variable from the execution scope.",
    category = "variable",
    order = 4
)
public class RemoveVariableNode extends BaseNode {

    @NodeProperty(displayName = "Default Name", category = "Variable", order = 1)
    private String defaultName = "";

    private static final String INPUT_NAME_ID = "input_name";

    private static final String OUTPUT_PREVIOUS_ID = "output_previous";
    private static final String OUTPUT_NAME_ID = "output_name";
    private static final String OUTPUT_REMOVED_ID = "output_removed";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public RemoveVariableNode() {
        super(UUID.randomUUID(), "variable.remove");

        addInputPort(new BasePort(INPUT_NAME_ID, "Name", "Variable name to remove", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_PREVIOUS_ID, "Previous", "Removed value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Resolved variable name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_REMOVED_ID, "Removed", "Whether the variable existed and was removed", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the requested variable name is usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when remove is invalid", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Remove Variable";
    }

    @Override
    public String getDescription() {
        return "Removes a user variable from the execution scope.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String name = VariableScopeBridge.resolveName(inputValues.get(INPUT_NAME_ID), defaultName);
        String error = VariableScopeBridge.validationError(name);

        if (error != null) {
            outputValues.put(OUTPUT_PREVIOUS_ID, null);
            outputValues.put(OUTPUT_NAME_ID, name == null ? "" : name);
            outputValues.put(OUTPUT_REMOVED_ID, false);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, error);
            return;
        }

        boolean existed = VariableScopeBridge.containsKey(context, name);
        Object previous = existed ? VariableScopeBridge.remove(context, name) : null;

        outputValues.put(OUTPUT_PREVIOUS_ID, previous);
        outputValues.put(OUTPUT_NAME_ID, name);
        outputValues.put(OUTPUT_REMOVED_ID, existed);
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("defaultName", defaultName);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object nameObj = map.get("defaultName");
        if (nameObj instanceof String name) {
            defaultName = name;
        }
    }
}
