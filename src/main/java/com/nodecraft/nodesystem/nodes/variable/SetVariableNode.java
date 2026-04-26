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
    id = "variable.set",
    displayName = "Set Variable",
    description = "Stores a value under a variable name in the execution scope.",
    category = "variable",
    order = 0
)
public class SetVariableNode extends BaseNode {

    @NodeProperty(displayName = "Default Name", category = "Variable", order = 1)
    private String defaultName = "";

    private static final String INPUT_NAME_ID = "input_name";
    private static final String INPUT_VALUE_ID = "input_value";

    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_PREVIOUS_ID = "output_previous";
    private static final String OUTPUT_NAME_ID = "output_name";
    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_EXISTS_BEFORE_ID = "output_exists_before";

    public SetVariableNode() {
        super(UUID.randomUUID(), "variable.set");

        addInputPort(new BasePort(INPUT_NAME_ID, "Name", "Variable name", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Value to store", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "Stored value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_PREVIOUS_ID, "Previous", "Previous value at this name", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Resolved variable name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether write succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_EXISTS_BEFORE_ID, "Exists Before", "Whether variable existed before write", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Set Variable";
    }

    @Override
    public String getDescription() {
        return "Stores a value under a variable name in the execution scope.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String name = resolveName(inputValues.get(INPUT_NAME_ID));
        Object value = inputValues.get(INPUT_VALUE_ID);

        if (name == null || name.isBlank()) {
            outputValues.put(OUTPUT_VALUE_ID, value);
            outputValues.put(OUTPUT_PREVIOUS_ID, null);
            outputValues.put(OUTPUT_NAME_ID, "");
            outputValues.put(OUTPUT_SUCCESS_ID, false);
            outputValues.put(OUTPUT_EXISTS_BEFORE_ID, false);
            return;
        }

        boolean existsBefore = VariableScopeBridge.containsKey(context, name);
        Object previous = VariableScopeBridge.put(context, name, value);

        outputValues.put(OUTPUT_VALUE_ID, value);
        outputValues.put(OUTPUT_PREVIOUS_ID, previous);
        outputValues.put(OUTPUT_NAME_ID, name);
        outputValues.put(OUTPUT_SUCCESS_ID, true);
        outputValues.put(OUTPUT_EXISTS_BEFORE_ID, existsBefore);
    }

    private String resolveName(Object inputName) {
        if (inputName instanceof String name && !name.isBlank()) {
            return name.trim();
        }
        if (defaultName == null || defaultName.isBlank()) {
            return null;
        }
        return defaultName.trim();
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

