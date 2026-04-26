package com.nodecraft.nodesystem.nodes.utilities.organization;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@NodeInfo(
    id = "utilities.organization.subgraph",
    displayName = "Subgraph",
    description = "Executes a referenced subgraph with named input/output mapping.",
    category = "utilities.organization",
    order = 5
)
public class SubgraphNode extends BaseNode {

    private static final Map<String, Object> FALLBACK_CALLS = new ConcurrentHashMap<>();

    @NodeProperty(displayName = "Subgraph Ref", category = "Subgraph", order = 1)
    private String subgraphRef = "subgraph";

    @NodeProperty(displayName = "Input Key", category = "Subgraph", order = 2)
    private String inputKey = "in";

    @NodeProperty(displayName = "Output Key", category = "Subgraph", order = 3)
    private String outputKey = "out";

    @NodeProperty(displayName = "Strict Mode", category = "Subgraph", order = 4)
    private boolean strictMode;

    @NodeProperty(displayName = "Max Call Depth", category = "Subgraph", order = 5)
    private int maxCallDepth = 8;

    private static final String INPUT_SUBGRAPH_REF_ID = "input_subgraph_ref";
    private static final String INPUT_SUBGRAPH_GRAPH_ID = "input_subgraph_graph";
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
        addInputPort(new BasePort(INPUT_SUBGRAPH_GRAPH_ID, "Subgraph Graph", "Optional graph object/json/string override", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Primary passthrough input", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_INPUTS_ID, "Inputs", "Optional input mapping object", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_OUTPUTS_ID, "Outputs", "Optional mapped output object from external executor", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_ENABLED_ID, "Enabled", "Disables subgraph execution when false", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "Resolved output value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_INPUTS_ID, "Inputs", "Resolved input map passed to subgraph", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_OUTPUTS_ID, "Outputs", "Resolved output map from subgraph", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_METADATA_ID, "Metadata", "Subgraph invocation metadata", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether subgraph mapping/execution succeeded", NodeDataType.BOOLEAN, this));
    }

    @Override
    public String getDisplayName() {
        return "Subgraph";
    }

    @Override
    public String getDescription() {
        return "Executes a referenced subgraph with named input/output mapping.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean enabled = !Boolean.FALSE.equals(inputValues.get(INPUT_ENABLED_ID));
        String resolvedSubgraphRef = resolveSubgraphRef(inputValues.get(INPUT_SUBGRAPH_REF_ID));
        Object primaryValue = inputValues.get(INPUT_VALUE_ID);

        if (!enabled) {
            writeResult(primaryValue, Map.of(), Map.of(), buildMetadata(resolvedSubgraphRef, false, false, "disabled", null), true);
            return;
        }

        if (resolvedSubgraphRef == null || resolvedSubgraphRef.isBlank()) {
            writeResult(primaryValue, Map.of(), Map.of(), buildMetadata("", false, false, "invalid_ref", null), false);
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

        NodeGraph subgraph = resolveSubgraphGraph(context, resolvedSubgraphRef, inputValues.get(INPUT_SUBGRAPH_GRAPH_ID));
        if (subgraph == null) {
            boolean hadExternalOutput = outputMap.containsKey(resolvedOutputKey);
            Object resolvedOutputValue = resolveOutputValueWithoutExecution(outputMap, resolvedOutputKey, mappedInputValue);
            Map<String, Object> metadata = buildMetadata(
                resolvedSubgraphRef,
                false,
                hadExternalOutput,
                "skeleton_mapping",
                null
            );
            metadata.put("inputKey", resolvedInputKey);
            metadata.put("outputKey", resolvedOutputKey);
            metadata.put("strictMode", strictMode);
            recordCallMetadata(context, resolvedSubgraphRef, metadata);
            writeResult(
                resolvedOutputValue,
                resolvedInputs,
                new LinkedHashMap<>(outputMap),
                metadata,
                !strictMode || outputMap.containsKey(resolvedOutputKey)
            );
            return;
        }

        int depth = currentCallDepth(context);
        if (depth >= Math.max(1, maxCallDepth)) {
            Map<String, Object> metadata = buildMetadata(
                resolvedSubgraphRef,
                false,
                false,
                "depth_limit",
                "Maximum subgraph call depth reached: " + maxCallDepth
            );
            metadata.put("depth", depth);
            recordCallMetadata(context, resolvedSubgraphRef, metadata);
            writeResult(null, resolvedInputs, Map.of(), metadata, false);
            return;
        }

        if (isRecursiveCall(context, resolvedSubgraphRef)) {
            Map<String, Object> metadata = buildMetadata(
                resolvedSubgraphRef,
                false,
                false,
                "recursive_call_blocked",
                "Detected recursive subgraph call for ref: " + resolvedSubgraphRef
            );
            metadata.put("depth", depth);
            recordCallMetadata(context, resolvedSubgraphRef, metadata);
            writeResult(null, resolvedInputs, Map.of(), metadata, false);
            return;
        }

        NestedExecutionResult nestedResult = executeSubgraph(context, subgraph, resolvedSubgraphRef, resolvedInputs);
        Map<String, Object> executedOutputs = nestedResult.outputs();

        boolean hadExternalOutput = executedOutputs.containsKey(resolvedOutputKey);
        Object resolvedOutputValue;
        if (hadExternalOutput) {
            resolvedOutputValue = executedOutputs.get(resolvedOutputKey);
        } else if (strictMode) {
            resolvedOutputValue = null;
        } else {
            resolvedOutputValue = mappedInputValue;
            executedOutputs.put(resolvedOutputKey, resolvedOutputValue);
        }

        Map<String, Object> metadata = buildMetadata(
            resolvedSubgraphRef,
            nestedResult.executed(),
            hadExternalOutput,
            nestedResult.executed() ? "executed" : "execution_failed",
            nestedResult.errorMessage()
        );
        metadata.put("inputKey", resolvedInputKey);
        metadata.put("outputKey", resolvedOutputKey);
        metadata.put("strictMode", strictMode);
        metadata.put("depth", depth);
        metadata.put("executorSuccess", nestedResult.success());
        metadata.put("nodeCount", subgraph.getNodes().size());

        recordCallMetadata(context, resolvedSubgraphRef, metadata);
        writeResult(
            resolvedOutputValue,
            resolvedInputs,
            new LinkedHashMap<>(executedOutputs),
            metadata,
            nestedResult.success() && (!strictMode || hadExternalOutput)
        );
    }

    private void writeResult(Object value, Map<String, Object> inputs, Map<String, Object> outputs, Map<String, Object> metadata, boolean valid) {
        outputValues.put(OUTPUT_VALUE_ID, value);
        outputValues.put(OUTPUT_INPUTS_ID, inputs);
        outputValues.put(OUTPUT_OUTPUTS_ID, outputs);
        outputValues.put(OUTPUT_METADATA_ID, metadata);
        outputValues.put(OUTPUT_VALID_ID, valid);
    }

    private Object resolveOutputValueWithoutExecution(Map<String, Object> outputMap, String outputKeyValue, Object fallback) {
        if (outputMap.containsKey(outputKeyValue)) {
            return outputMap.get(outputKeyValue);
        }
        if (strictMode) {
            return null;
        }
        outputMap.put(outputKeyValue, fallback);
        return fallback;
    }

    private Map<String, Object> buildMetadata(String ref, boolean executed, boolean usedExternalOutput, String mode, @Nullable String errorMessage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("subgraphRef", ref);
        metadata.put("executed", executed);
        metadata.put("usedExternalOutput", usedExternalOutput);
        metadata.put("mode", mode);
        metadata.put("strictMode", strictMode);
        if (errorMessage != null && !errorMessage.isBlank()) {
            metadata.put("error", errorMessage);
        }
        return metadata;
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
    private NodeGraph resolveSubgraphGraph(@Nullable ExecutionContext context, String ref, Object directGraphValue) {
        NodeGraph direct = toNodeGraph(directGraphValue);
        if (direct != null) {
            return cloneGraph(direct);
        }

        if (context == null) {
            return null;
        }

        Object registryRaw = context.getVariable(GraphIOKeys.SUBGRAPH_REGISTRY_KEY);
        if (!(registryRaw instanceof Map<?, ?> registry)) {
            return null;
        }

        Object registryValue = ((Map<String, Object>) registry).get(ref);
        NodeGraph resolved = toNodeGraph(registryValue);
        return resolved == null ? null : cloneGraph(resolved);
    }

    private NodeGraph toNodeGraph(Object value) {
        if (value instanceof NodeGraph graph) {
            return graph;
        }
        if (value instanceof SavedGraph saved) {
            return GraphSerializer.fromSavedGraph(saved);
        }
        if (value instanceof String json && !json.isBlank()) {
            try {
                return GraphSerializer.fromJsonToGraph(json);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private NodeGraph cloneGraph(NodeGraph graph) {
        try {
            SavedGraph saved = GraphSerializer.toSavedGraph(graph);
            return GraphSerializer.fromSavedGraph(saved);
        } catch (Exception ignored) {
            return graph;
        }
    }

    private NestedExecutionResult executeSubgraph(@Nullable ExecutionContext context, NodeGraph subgraph, String ref, Map<String, Object> inputs) {
        if (context == null) {
            return new NestedExecutionResult(false, false, Map.of(), "Execution context is required for subgraph execution.");
        }

        Object previousInputs = context.getVariable(GraphIOKeys.GRAPH_INPUTS_KEY);
        Object previousOutputs = context.getVariable(GraphIOKeys.GRAPH_OUTPUTS_KEY);
        Object previousStack = context.getVariable(GraphIOKeys.SUBGRAPH_CALL_STACK_KEY);

        Map<String, Object> newInputs = new LinkedHashMap<>(inputs);
        Map<String, Object> newOutputs = new LinkedHashMap<>();
        List<String> newStack = normalizeCallStack(previousStack);
        newStack.add(ref);

        context.setVariable(GraphIOKeys.GRAPH_INPUTS_KEY, newInputs);
        context.setVariable(GraphIOKeys.GRAPH_OUTPUTS_KEY, newOutputs);
        context.setVariable(GraphIOKeys.SUBGRAPH_CALL_STACK_KEY, newStack);

        boolean success;
        try {
            NodeExecutor executor = new NodeExecutor(subgraph, context);
            success = executor.executeSync();
        } catch (Exception e) {
            restoreContextVariables(context, previousInputs, previousOutputs, previousStack);
            return new NestedExecutionResult(true, false, Map.of(), e.getMessage());
        }

        Map<String, Object> capturedOutputs = toStringObjectMap(context.getVariable(GraphIOKeys.GRAPH_OUTPUTS_KEY));
        restoreContextVariables(context, previousInputs, previousOutputs, previousStack);
        return new NestedExecutionResult(true, success, capturedOutputs, success ? null : "Nested node execution reported failure.");
    }

    private void restoreContextVariables(ExecutionContext context, Object prevInputs, Object prevOutputs, Object prevStack) {
        context.setVariable(GraphIOKeys.GRAPH_INPUTS_KEY, prevInputs);
        context.setVariable(GraphIOKeys.GRAPH_OUTPUTS_KEY, prevOutputs);
        context.setVariable(GraphIOKeys.SUBGRAPH_CALL_STACK_KEY, prevStack);
    }

    private int currentCallDepth(@Nullable ExecutionContext context) {
        if (context == null) {
            return 0;
        }
        Object stackRaw = context.getVariable(GraphIOKeys.SUBGRAPH_CALL_STACK_KEY);
        return normalizeCallStack(stackRaw).size();
    }

    private boolean isRecursiveCall(@Nullable ExecutionContext context, String ref) {
        if (context == null) {
            return false;
        }
        Object stackRaw = context.getVariable(GraphIOKeys.SUBGRAPH_CALL_STACK_KEY);
        List<String> stack = normalizeCallStack(stackRaw);
        return stack.contains(ref);
    }

    @SuppressWarnings("unchecked")
    private List<String> normalizeCallStack(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> normalized = new ArrayList<>();
            for (Object entry : list) {
                if (entry == null) {
                    continue;
                }
                normalized.add(String.valueOf(entry));
            }
            return normalized;
        }
        return new ArrayList<>();
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
            Object existing = context.getVariable(GraphIOKeys.SUBGRAPH_CALLS_KEY);
            Map<String, Object> calls;
            if (existing instanceof Map<?, ?> map) {
                calls = (Map<String, Object>) map;
            } else {
                calls = new LinkedHashMap<>();
                context.setVariable(GraphIOKeys.SUBGRAPH_CALLS_KEY, calls);
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
        state.put("maxCallDepth", maxCallDepth);
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
        Object maxCallDepthObj = map.get("maxCallDepth");
        if (maxCallDepthObj instanceof Number value) {
            maxCallDepth = Math.max(1, value.intValue());
        }
    }

    private record NestedExecutionResult(boolean executed, boolean success, Map<String, Object> outputs, @Nullable String errorMessage) {
    }
}

