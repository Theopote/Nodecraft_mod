package com.nodecraft.nodesystem.nodes.utilities.organization;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.subgraph.SubgraphCallFrame;
import com.nodecraft.nodesystem.execution.subgraph.SubgraphCallFrameBridge;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    effect = NodeEffect.CONTEXT_READ,
    id = "utilities.organization.graph_input",
    displayName = "Graph Input",
    description = "Defines a named graph-level input with typed default fallback.",
    category = "utilities.organization",
    order = 0
)
public class GraphInputNode extends BaseNode {

    @NodeProperty(displayName = "Input Name", category = "Graph IO", order = 1)
    private String inputName = "input";

    @NodeProperty(displayName = "Required", category = "Graph IO", order = 2)
    private boolean required;

    private String declaredType = "any";
    private String inferredType = "any";

    private static final String INPUT_DEFAULT_ID = "input_default";

    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_NAME_ID = "output_name";
    private static final String OUTPUT_WAS_PROVIDED_ID = "output_was_provided";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    public GraphInputNode() {
        super(UUID.randomUUID(), "utilities.organization.graph_input");

        BasePort defaultPort = new BasePort(
                INPUT_DEFAULT_ID,
                "Default",
                "Fallback value when caller did not supply this input key",
                NodeDataType.ANY,
                this
        );
        defaultPort.bindPassthroughType("T");
        addInputPort(defaultPort);

        BasePort valuePort = new BasePort(
                OUTPUT_VALUE_ID,
                "Value",
                "Resolved graph input value",
                NodeDataType.ANY,
                this
        );
        valuePort.bindPassthroughType("T");
        addOutputPort(valuePort);

        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Resolved input name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(
                OUTPUT_WAS_PROVIDED_ID,
                "Was Provided",
                "Whether caller supplied this input key (null values count as provided)",
                NodeDataType.BOOLEAN,
                this
        ));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether required input was resolved", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why input resolution failed", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Graph Input";
    }

    @Override
    public String getDescription() {
        return "Defines a named graph-level input with typed default fallback.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object fallback = inputValues.get(INPUT_DEFAULT_ID);
        String resolvedName = resolvedInputName();

        SubgraphCallFrame frame = SubgraphCallFrameBridge.current(context);
        boolean wasProvided = frame != null && frame.inputs().containsKey(resolvedName);
        Object resolvedValue;
        String error = "";

        if (wasProvided) {
            resolvedValue = frame.inputs().get(resolvedName);
        } else if (fallback != null || !required) {
            resolvedValue = fallback;
        } else {
            resolvedValue = null;
            error = "Required graph input '" + resolvedName + "' was not provided";
        }

        boolean valid = error.isEmpty() && (!required || wasProvided || fallback != null);

        outputValues.put(OUTPUT_VALUE_ID, resolvedValue);
        outputValues.put(OUTPUT_NAME_ID, resolvedName);
        outputValues.put(OUTPUT_WAS_PROVIDED_ID, wasProvided);
        outputValues.put(OUTPUT_VALID_ID, valid);
        outputValues.put(OUTPUT_ERROR_ID, error);
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
        state.put("declaredType", declaredType);
        state.put("inferredType", inferredType);
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
        Object declaredTypeObj = map.get("declaredType");
        if (declaredTypeObj instanceof String value && !value.isBlank()) {
            declaredType = value.trim();
        }
        Object inferredTypeObj = map.get("inferredType");
        if (inferredTypeObj instanceof String value && !value.isBlank()) {
            inferredType = value.trim();
        }
    }
}
