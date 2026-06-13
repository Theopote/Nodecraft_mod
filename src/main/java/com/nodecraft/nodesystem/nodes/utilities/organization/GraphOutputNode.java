package com.nodecraft.nodesystem.nodes.utilities.organization;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@NodeInfo(
    id = "utilities.organization.graph_output",
    displayName = "Graph Output",
    description = "Defines a named graph-level output and publishes it into execution context.",
    category = "utilities.organization",
    order = 1
)
public class GraphOutputNode extends BaseNode {

    @NodeProperty(displayName = "Output Name", category = "Graph IO", order = 1)
    private String outputName = "output";

    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_NAME_OVERRIDE_ID = "input_name_override";

    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_NAME_ID = "output_name";
    private static final String OUTPUT_OUTPUTS_ID = "output_outputs";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public GraphOutputNode() {
        super(UUID.randomUUID(), "utilities.organization.graph_output");

        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Value to expose as graph output", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_NAME_OVERRIDE_ID, "Name Override", "Optional runtime output name override", NodeDataType.STRING, this));

        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "Output value passthrough", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Resolved output name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_OUTPUTS_ID, "Outputs", "Current graph output map snapshot", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why graph output was not published", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether output name is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Graph Output";
    }

    @Override
    public String getDescription() {
        return "Defines a named graph-level output and publishes it into execution context.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object value = inputValues.get(INPUT_VALUE_ID);
        String name = resolveOutputName(inputValues.get(INPUT_NAME_OVERRIDE_ID));
        if (name == null || name.isBlank()) {
            writeResult(value, "", Map.of(), false, "Output name is empty");
            return;
        }

        if (context == null) {
            writeResult(value, name, Map.of(), false, "Missing execution context");
            return;
        }

        Map<String, Object> outputMap = getOrCreateOutputMap(context);
        outputMap.put(name, value);

        writeResult(value, name, new LinkedHashMap<>(outputMap), true, "");
    }

    private void writeResult(Object value, String name, Map<String, Object> outputs, boolean valid, String error) {
        outputValues.put(OUTPUT_VALUE_ID, value);
        outputValues.put(OUTPUT_NAME_ID, name);
        outputValues.put(OUTPUT_OUTPUTS_ID, outputs);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private String resolveOutputName(Object nameOverrideObj) {
        if (nameOverrideObj instanceof String override && !override.isBlank()) {
            return override.trim();
        }
        if (outputName == null || outputName.isBlank()) {
            return null;
        }
        return outputName.trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateOutputMap(@Nullable ExecutionContext context) {
        Object existing = context.getVariable(GraphIOKeys.GRAPH_OUTPUTS_KEY);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        context.setVariable(GraphIOKeys.GRAPH_OUTPUTS_KEY, created);
        return created;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("outputName", outputName);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object outputNameObj = map.get("outputName");
        if (outputNameObj instanceof String name) {
            outputName = name;
        }
    }
}
