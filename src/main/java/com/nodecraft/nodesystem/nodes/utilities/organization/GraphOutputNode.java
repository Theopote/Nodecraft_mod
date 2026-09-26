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
    effect = NodeEffect.CONTEXT_WRITE,
    id = "utilities.organization.graph_output",
    displayName = "Graph Output",
    description = "Defines a named graph-level output and publishes it into the active call frame.",
    category = "utilities.organization",
    order = 1
)
public class GraphOutputNode extends BaseNode {

    @NodeProperty(displayName = "Output Name", category = "Graph IO", order = 1)
    private String outputName = "output";

    private String declaredType = "any";
    private String inferredType = "any";

    private static final String INPUT_VALUE_ID = "input_value";

    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_NAME_ID = "output_name";
    private static final String OUTPUT_ERROR_ID = "output_error";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public GraphOutputNode() {
        super(UUID.randomUUID(), "utilities.organization.graph_output");

        BasePort valueIn = new BasePort(
                INPUT_VALUE_ID,
                "Value",
                "Value to expose as graph output",
                NodeDataType.ANY,
                this
        );
        valueIn.bindPassthroughType("T");
        addInputPort(valueIn);

        BasePort valueOut = new BasePort(
                OUTPUT_VALUE_ID,
                "Value",
                "Output value passthrough",
                NodeDataType.ANY,
                this
        );
        valueOut.bindPassthroughType("T");
        addOutputPort(valueOut);

        addOutputPort(new BasePort(OUTPUT_NAME_ID, "Name", "Resolved output name", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why graph output was not published", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether output name is valid", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Graph Output";
    }

    @Override
    public String getDescription() {
        return "Defines a named graph-level output and publishes it into the active call frame.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        Object value = inputValues.get(INPUT_VALUE_ID);
        String name = resolvedOutputName();
        if (name == null || name.isBlank()) {
            writeResult(value, "", false, "Output name is empty");
            return;
        }

        SubgraphCallFrame frame = SubgraphCallFrameBridge.current(context);
        if (frame == null) {
            writeResult(value, name, false, "Missing subgraph call frame");
            return;
        }

        frame.outputs().put(name, value);
        writeResult(value, name, true, "");
    }

    private void writeResult(Object value, String name, boolean valid, String error) {
        outputValues.put(OUTPUT_VALUE_ID, value);
        outputValues.put(OUTPUT_NAME_ID, name);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private String resolvedOutputName() {
        if (outputName == null || outputName.isBlank()) {
            return null;
        }
        return outputName.trim();
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("outputName", outputName);
        state.put("declaredType", declaredType);
        state.put("inferredType", inferredType);
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
