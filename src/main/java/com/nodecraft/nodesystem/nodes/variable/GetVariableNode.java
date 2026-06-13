package com.nodecraft.nodesystem.nodes.variable;

import com.nodecraft.nodesystem.api.NodeDataType;
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
    id = "variable.get",
    displayName = "Get Variable",
    description = "Reads a value by user variable name from the execution scope. Exists means the name exists, even when its stored value is null.",
    category = "variable",
    order = 1
)
public class GetVariableNode extends BaseNode {

    @NodeProperty(displayName = "Default Name", category = "Variable", order = 1)
    private String defaultName = "";

    private static final String INPUT_NAME_ID = "input_name";
    private static final String INPUT_DEFAULT_VALUE_ID = "input_default_value";

    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_EXISTS_ID = "output_exists";
    private static final String OUTPUT_NAME_ID = "output_name";
    private static final String OUTPUT_IS_NULL_ID = "output_is_null";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GetVariableNode() {
        super(UUID.randomUUID(), "variable.get");

        addInputPort(new BasePort(INPUT_NAME_ID, "Name", "Variable name", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_DEFAULT_VALUE_ID, "Default", "Fallback value when variable is missing", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "Resolved variable value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_EXISTS_ID, "Exists", "Whether the variable exists", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Resolved variable name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_IS_NULL_ID, "Is Null", "Whether the stored variable value is null", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether the requested variable name is usable", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Error message when read is invalid", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Get Variable";
    }

    @Override
    public String getDescription() {
        return "Reads a value by user variable name from the execution scope. Exists means the name exists, even when its stored value is null.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String name = VariableScopeBridge.resolveName(inputValues.get(INPUT_NAME_ID), defaultName);
        Object fallback = inputValues.get(INPUT_DEFAULT_VALUE_ID);
        String error = VariableScopeBridge.validationError(name);

        if (error != null) {
            outputValues.put(OUTPUT_VALUE_ID, fallback);
            outputValues.put(OUTPUT_EXISTS_ID, false);
            outputValues.put(OUTPUT_NAME_ID, name == null ? "" : name);
            outputValues.put(OUTPUT_IS_NULL_ID, false);
            outputValues.put(OUTPUT_VALID_ID, false);
            outputValues.put(OUTPUT_ERROR_ID, error);
            return;
        }

        boolean exists = VariableScopeBridge.containsKey(context, name);
        Object value = exists ? VariableScopeBridge.get(context, name) : fallback;

        outputValues.put(OUTPUT_VALUE_ID, value);
        outputValues.put(OUTPUT_EXISTS_ID, exists);
        outputValues.put(OUTPUT_NAME_ID, name);
        outputValues.put(OUTPUT_IS_NULL_ID, exists && value == null);
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
