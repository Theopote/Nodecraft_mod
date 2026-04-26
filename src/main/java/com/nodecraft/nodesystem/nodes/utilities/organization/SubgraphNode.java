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
import java.util.concurrent.ConcurrentHashMap;

@NodeInfo(
    id = "utilities.organization.subgraph",
    displayName = "Subgraph",
    description = "Subgraph call skeleton with named input/output mapping and execution metadata.",
    category = "utilities.organization",
    order = 5
)
public class SubgraphNode extends BaseNode {

    private static final String SUBGRAPH_CALLS_KEY = "__nodecraft.subgraph.calls";
    private static final Map<String, Object> FALLBACK_CALLS = new ConcurrentHashMap<>();

    @NodeProperty(displayName = "Subgraph Ref", category = "Subgraph", order = 1)
    private String subgraphRef = "subgraph";

    @NodeProperty(displayName = "Input Key", category = "Subgraph", order = 2)
    private String inputKey = "in";

    @NodeProperty(displayName = "Output Key", category = "Subgraph", order = 3)
    private String outputKey = "out";

    @NodeProperty(displayName = "Strict Mode", category = "Subgraph", order = 4)
    private boolean strictMode;

    private static final String INPUT_SUBGRAPH_REF_ID = "input_subgraph_ref";
    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_INPUTS_ID = "input_inputs";
    private static final String INPUT_OUTPUTS_ID = "input_outputs";
    private static final String INPUT_ENABLED_ID = "input_enabled";

    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_INPUTS_ID = "output_inputs";
    private static final String OUTPUT_OUTPUTS_ID = "output_outputs";
    private static final String OUTPUT_METADATA_ID = "output_metadata";
    private static final String OUTPUT_VALID_ID = "output_valid";

    public SubgraphNode() {
        super(UUID.randomUUID(), "utilities.organization.subgraph");

        addInputPort(new BasePort(INPUT_SUBGRAPH_REF_ID, "Subgraph Ref", "Optional runtime subgraph reference override", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Primary passthrough input", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_INPUTS_ID, "Inputs", "Optional input mapping object", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_OUTPUTS_ID, "Outputs", "Optional mapped output object from external executor", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_ENABLED_ID, "Enabled", "Disables call mapping when false", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "Resolved output value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_INPUTS_ID, "Inputs", "Resolved input map passed to subgraph", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_OUTPUTS_ID, "Outputs", "Resolved output map from subgraph", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_METADATA_ID, "Metadata", "Subgraph invocation metadata", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether mapping succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Subgraph";
    }

    @Override
    public String getDescription() {
        return "Subgraph call skeleton with named input/output mapping and execution metadata.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean enabled = !Boolean.FALSE.equals(inputValues.get(INPUT_ENABLED_ID));
        String resolvedSubgraphRef = resolveSubgraphRef(inputValues.get(INPUT_SUBGRAPH_REF_ID));
        Object primaryValue = inputValues.get(INPUT_VALUE_ID);

        if (!enabled) {
            outputValues.put(OUTPUT_VALUE_ID, primaryValue);
            outputValues.put(OUTPUT_INPUTS_ID, Map.of());
            outputValues.put(OUTPUT_OUTPUTS_ID, Map.of());
            outputValues.put(OUTPUT_METADATA_ID, Map.of(
                "subgraphRef", resolvedSubgraphRef,
                "mode", "disabled",
                "strictMode", strictMode
            ));
            outputValues.put(OUTPUT_VALID_ID, true);
            return;
        }

        if (resolvedSubgraphRef == null || resolvedSubgraphRef.isBlank()) {
            outputValues.put(OUTPUT_VALUE_ID, primaryValue);
            outputValues.put(OUTPUT_INPUTS_ID, Map.of());
            outputValues.put(OUTPUT_OUTPUTS_ID, Map.of());
            outputValues.put(OUTPUT_METADATA_ID, Map.of(
                "subgraphRef", "",
                "mode", "invalid_ref",
                "strictMode", strictMode
            ));
            outputValues.put(OUTPUT_VALID_ID, false);
            return;
        }

        Map<String, Object> inputMap = toStringObjectMap(inputValues.get(INPUT_INPUTS_ID));
        Map<String, Object> outputMap = toStringObjectMap(inputValues.get(INPUT_OUTPUTS_ID));

        String resolvedInputKey = resolveInputKey();
        String resolvedOutputKey = resolveOutputKey();

        Object mappedInputValue = inputMap.containsKey(resolvedInputKey)
            ? inputMap.get(resolvedInputKey)
            : primaryValue;

        Map<String, Object> resolvedInputs = new LinkedHashMap<>(inputMap);
        resolvedInputs.putIfAbsent(resolvedInputKey, mappedInputValue);

        boolean hadExternalOutput = outputMap.containsKey(resolvedOutputKey);

        Object resolvedOutputValue;
        if (hadExternalOutput) {
            resolvedOutputValue = outputMap.get(resolvedOutputKey);
        } else if (strictMode) {
            resolvedOutputValue = null;
        } else {
            resolvedOutputValue = mappedInputValue;
            outputMap.put(resolvedOutputKey, resolvedOutputValue);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("subgraphRef", resolvedSubgraphRef);
        metadata.put("inputKey", resolvedInputKey);
        metadata.put("outputKey", resolvedOutputKey);
        metadata.put("strictMode", strictMode);
        metadata.put("usedExternalOutput", hadExternalOutput);
        metadata.put("mode", "skeleton_mapping");

        recordCallMetadata(context, resolvedSubgraphRef, metadata);

        outputValues.put(OUTPUT_VALUE_ID, resolvedOutputValue);
        outputValues.put(OUTPUT_INPUTS_ID, resolvedInputs);
        outputValues.put(OUTPUT_OUTPUTS_ID, new LinkedHashMap<>(outputMap));
        outputValues.put(OUTPUT_METADATA_ID, metadata);
        outputValues.put(OUTPUT_VALID_ID, !strictMode || outputMap.containsKey(resolvedOutputKey));
    }

    private String resolveSubgraphRef(Object override) {
        if (override instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        if (subgraphRef == null || subgraphRef.isBlank()) {
            return null;
        }
        return subgraphRef.trim();
    }

    private String resolveInputKey() {
        if (inputKey == null || inputKey.isBlank()) {
            return "in";
        }
        return inputKey.trim();
    }

    private String resolveOutputKey() {
        if (outputKey == null || outputKey.isBlank()) {
            return "out";
        }
        return outputKey.trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private void recordCallMetadata(@Nullable ExecutionContext context, String subgraphRefName, Map<String, Object> metadata) {
        if (context != null) {
            Object existing = context.getVariable(SUBGRAPH_CALLS_KEY);
            Map<String, Object> calls;
            if (existing instanceof Map<?, ?> map) {
                calls = (Map<String, Object>) map;
            } else {
                calls = new LinkedHashMap<>();
                context.setVariable(SUBGRAPH_CALLS_KEY, calls);
            }
            calls.put(subgraphRefName + "::" + getId(), new LinkedHashMap<>(metadata));
            return;
        }
        FALLBACK_CALLS.put(subgraphRefName + "::" + getId(), new LinkedHashMap<>(metadata));
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("subgraphRef", subgraphRef);
        state.put("inputKey", inputKey);
        state.put("outputKey", outputKey);
        state.put("strictMode", strictMode);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object subgraphRefObj = map.get("subgraphRef");
        if (subgraphRefObj instanceof String value) {
            subgraphRef = value;
        }
        Object inputKeyObj = map.get("inputKey");
        if (inputKeyObj instanceof String value) {
            inputKey = value;
        }
        Object outputKeyObj = map.get("outputKey");
        if (outputKeyObj instanceof String value) {
            outputKey = value;
        }
        Object strictModeObj = map.get("strictMode");
        if (strictModeObj instanceof Boolean value) {
            strictMode = value;
        }
    }
}
