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
    description = "Reads a value by variable name from the execution scope.",
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

    public GetVariableNode() {
        super(UUID.randomUUID(), "variable.get");

        addInputPort(new BasePort(INPUT_NAME_ID, "Name", "Variable name", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_DEFAULT_VALUE_ID, "Default", "Fallback value when variable is missing", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "Resolved variable value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_EXISTS_ID, "Exists", "Whether the variable exists", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Resolved variable name", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Get Variable";
    }

    @Override
    public String getDescription() {
        return "Reads a value by variable name from the execution scope.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String name = resolveName(inputValues.get(INPUT_NAME_ID));
        Object fallback = inputValues.get(INPUT_DEFAULT_VALUE_ID);

        if (name == null || name.isBlank()) {
            outputValues.put(OUTPUT_VALUE_ID, fallback);
            outputValues.put(OUTPUT_EXISTS_ID, false);
            outputValues.put(OUTPUT_NAME_ID, "");
            return;
        }

        boolean exists = VariableScopeBridge.containsKey(context, name);
        Object value = exists ? VariableScopeBridge.get(context, name) : fallback;

        outputValues.put(OUTPUT_VALUE_ID, value);
        outputValues.put(OUTPUT_EXISTS_ID, exists);
        outputValues.put(OUTPUT_NAME_ID, name);
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

