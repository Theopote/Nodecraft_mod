package com.nodecraft.nodesystem.nodes.utilities.organization;

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
    id = "utilities.organization.graph_input",
    displayName = "Graph Input",
    description = "Defines a named graph-level input with optional override and default fallback.",
    category = "utilities.organization",
    order = 0
)
public class GraphInputNode extends BaseNode {

    @NodeProperty(displayName = "Input Name", category = "Graph IO", order = 1)
    private String inputName = "input";

    @NodeProperty(displayName = "Required", category = "Graph IO", order = 2)
    private boolean required;

    private static final String INPUT_OVERRIDE_ID = "input_override";
    private static final String INPUT_DEFAULT_ID = "input_default";

    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_NAME_ID = "output_name";
    private static final String OUTPUT_WAS_OVERRIDDEN_ID = "output_was_overridden";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public GraphInputNode() {
        super(UUID.randomUUID(), "utilities.organization.graph_input");

        addInputPort(new BasePort(INPUT_OVERRIDE_ID, "Override", "Optional value from parent graph/subgraph caller", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_DEFAULT_ID, "Default", "Fallback value when override is not supplied", NodeDataType.ANY, this));

        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "Resolved graph input value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Resolved input name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_WAS_OVERRIDDEN_ID, "Was Overridden", "Whether override value was used", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether required input was resolved", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Graph Input";
    }

    @Override
    public String getDescription() {
        return "Defines a named graph-level input with optional override and default fallback.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object override = inputValues.get(INPUT_OVERRIDE_ID);
        Object fallback = inputValues.get(INPUT_DEFAULT_ID);
        String resolvedName = resolvedInputName();

        Object contextMapped = readContextInput(context, resolvedName);
        boolean hasContextMapped = contextMapped != null;

        boolean hasOverride = override != null;
        Object resolvedValue = hasContextMapped ? contextMapped : (hasOverride ? override : fallback);
        boolean valid = !required || resolvedValue != null;

        outputValues.put(OUTPUT_VALUE_ID, resolvedValue);
        outputValues.put(OUTPUT_NAME_ID, resolvedName);
        outputValues.put(OUTPUT_WAS_OVERRIDDEN_ID, hasContextMapped || hasOverride);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    @SuppressWarnings("unchecked")
    private Object readContextInput(@Nullable ExecutionContext context, String name) {
        if (context == null || name == null || name.isBlank()) {
            return null;
        }
        Object raw = context.getVariable(GraphIOKeys.GRAPH_INPUTS_KEY);
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        return ((Map<String, Object>) map).get(name);
    }

    private String resolvedInputName() {
        if (inputName == null || inputName.isBlank()) {
            return "input";
        }
        return inputName.trim();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("inputName", inputName);
        state.put("required", required);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object nameObj = map.get("inputName");
        if (nameObj instanceof String name) {
            inputName = name;
        }
        Object requiredObj = map.get("required");
        if (requiredObj instanceof Boolean value) {
            required = value;
        }
    }
}
