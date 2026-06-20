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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@NodeInfo(
    id = "utilities.organization.subgraph",
    displayName = "Subgraph",
    description = "Executes a referenced subgraph with named input/output mapping.",
    category = "utilities.organization",
    order = 5
)
public class SubgraphNode extends BaseNode {

    private static final Pattern NON_ALNUM_UNDERSCORE = Pattern.compile("[^a-zA-Z0-9_]");

    private static final String DYNAMIC_INPUT_PREFIX = "dynamic_input_key_";
    private static final String DYNAMIC_OUTPUT_PREFIX = "dynamic_output_key_";

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

    @NodeProperty(displayName = "Additional Input Keys", category = "Subgraph", order = 6)
    private String additionalInputKeys = "";

    @NodeProperty(displayName = "Additional Output Keys", category = "Subgraph", order = 7)
    private String additionalOutputKeys = "";

    @NodeProperty(displayName = "Emit Debug Trace", category = "Subgraph", order = 8)
    private boolean emitDebugTrace = true;

    private String embeddedGraphJson = "";

    private static final String INPUT_SUBGRAPH_REF_ID = "input_subgraph_ref";
    private static final String INPUT_SUBGRAPH_GRAPH_ID = "input_subgraph_graph";
    private static final String INPUT_VALUE_ID = "input_value";
    private static final String INPUT_INPUTS_ID = "input_inputs";
    private static final String INPUT_OUTPUTS_ID = "input_outputs";
    private static final String INPUT_INPUT_KEYS_ID = "input_input_keys";
    private static final String INPUT_OUTPUT_KEYS_ID = "input_output_keys";
    private static final String INPUT_ENABLED_ID = "input_enabled";

    private static final String OUTPUT_VALUE_ID = "output_value";
    private static final String OUTPUT_INPUTS_ID = "output_inputs";
    private static final String OUTPUT_OUTPUTS_ID = "output_outputs";
    private static final String OUTPUT_MAPPED_OUTPUTS_ID = "output_mapped_outputs";
    private static final String OUTPUT_METADATA_ID = "output_metadata";
    private static final String OUTPUT_DEBUG_TRACE_ID = "output_debug_trace";
    private static final String OUTPUT_VALID_ID = "output_valid";

    private Set<String> activeDynamicInputKeys = new LinkedHashSet<>();
    private Set<String> activeDynamicOutputKeys = new LinkedHashSet<>();

    public SubgraphNode() {
        super(UUID.randomUUID(), "utilities.organization.subgraph");

        addInputPort(new BasePort(INPUT_SUBGRAPH_REF_ID, "Subgraph Ref", "Optional runtime subgraph reference override", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_SUBGRAPH_GRAPH_ID, "Subgraph Graph", "Optional graph object/json/string override", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_VALUE_ID, "Value", "Primary passthrough input", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_INPUTS_ID, "Inputs", "Optional input mapping object", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_OUTPUTS_ID, "Outputs", "Optional mapped output object from external executor", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_INPUT_KEYS_ID, "Input Keys", "Optional input key list override", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_OUTPUT_KEYS_ID, "Output Keys", "Optional output key list override", NodeDataType.LIST, this));
        addInputPort(new BasePort(INPUT_ENABLED_ID, "Enabled", "Disables subgraph execution when false", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_VALUE_ID, "Value", "Resolved output value", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_INPUTS_ID, "Inputs", "Resolved input map passed to subgraph", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_OUTPUTS_ID, "Outputs", "Resolved output map from subgraph", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_MAPPED_OUTPUTS_ID, "Mapped Outputs", "Resolved output map restricted to requested output keys", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_METADATA_ID, "Metadata", "Subgraph invocation metadata", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_DEBUG_TRACE_ID, "Debug Trace", "Debug messages for subgraph mapping/execution", NodeDataType.LIST, this));
        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether subgraph mapping/execution succeeded", NodeDataType.BOOLEAN, this));

        rebuildDynamicPorts(
            resolveRequestedKeys(resolveInputKey(), additionalInputKeys, null),
            resolveRequestedKeys(resolveOutputKey(), additionalOutputKeys, null)
        );
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
        List<Object> debugTrace = new ArrayList<>();
        boolean enabled = !Boolean.FALSE.equals(inputValues.get(INPUT_ENABLED_ID));
        String resolvedSubgraphRef = resolveSubgraphRef(inputValues.get(INPUT_SUBGRAPH_REF_ID));
        Object primaryValue = inputValues.get(INPUT_VALUE_ID);
        debugTrace.add("enabled=" + enabled);
        debugTrace.add("subgraphRef=" + (resolvedSubgraphRef == null ? "" : resolvedSubgraphRef));

        if (!enabled) {
            debugTrace.add("mode=disabled");
            writeResult(primaryValue, Map.of(), Map.of(), Map.of(), buildMetadata(resolvedSubgraphRef, false, false, "disabled", null), debugTrace, true);
            return;
        }

        if (resolvedSubgraphRef == null || resolvedSubgraphRef.isBlank()) {
            debugTrace.add("mode=invalid_ref");
            writeResult(primaryValue, Map.of(), Map.of(), Map.of(), buildMetadata("", false, false, "invalid_ref", null), debugTrace, false);
            return;
        }

        Map<String, Object> inputMap = toStringObjectMap(inputValues.get(INPUT_INPUTS_ID));
        Map<String, Object> outputMap = toStringObjectMap(inputValues.get(INPUT_OUTPUTS_ID));

        String resolvedInputKey = resolveInputKey();
        String resolvedOutputKey = resolveOutputKey();
        List<String> inputKeys = resolveRequestedKeys(
            resolvedInputKey,
            additionalInputKeys,
            inputValues.get(INPUT_INPUT_KEYS_ID)
        );
        List<String> outputKeys = resolveRequestedKeys(
            resolvedOutputKey,
            additionalOutputKeys,
            inputValues.get(INPUT_OUTPUT_KEYS_ID)
        );
        rebuildDynamicPorts(inputKeys, outputKeys);
        debugTrace.add("inputKeys=" + inputKeys);
        debugTrace.add("outputKeys=" + outputKeys);

        Object mappedInputValue = inputMap.containsKey(resolvedInputKey)
            ? inputMap.get(resolvedInputKey)
            : primaryValue;

        Map<String, Object> resolvedInputs = new LinkedHashMap<>(inputMap);
        resolvedInputs.putIfAbsent(resolvedInputKey, mappedInputValue);
        for (String key : inputKeys) {
            if (resolvedInputs.containsKey(key)) {
                continue;
            }
            String dynamicPortId = dynamicInputPortId(key);
            if (inputValues.containsKey(dynamicPortId)) {
                resolvedInputs.put(key, inputValues.get(dynamicPortId));
                continue;
            }
            if (resolvedInputKey.equals(key)) {
                resolvedInputs.put(key, mappedInputValue);
            } else {
                resolvedInputs.put(key, null);
            }
        }

        NodeGraph subgraph = resolveSubgraphGraph(context, resolvedSubgraphRef, inputValues.get(INPUT_SUBGRAPH_GRAPH_ID));
        if (subgraph == null) {
            boolean hadExternalOutput = outputMap.containsKey(resolvedOutputKey);
            Map<String, Object> mappedOutputs = resolveMappedOutputs(outputMap, outputKeys, resolvedOutputKey, mappedInputValue);
            Object resolvedOutputValue = mappedOutputs.get(resolvedOutputKey);
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
            metadata.put("inputKeys", inputKeys);
            metadata.put("outputKeys", outputKeys);
            metadata.put("mappedInputCount", resolvedInputs.size());
            metadata.put("mappedOutputCount", mappedOutputs.size());
            debugTrace.add("mode=skeleton_mapping");
            recordCallMetadata(context, resolvedSubgraphRef, metadata);
            writeResult(
                resolvedOutputValue,
                resolvedInputs,
                new LinkedHashMap<>(outputMap),
                mappedOutputs,
                metadata,
                debugTrace,
                !strictMode || hasAllRequestedOutputKeys(mappedOutputs, outputKeys)
            );
            return;
        }
        debugTrace.add("subgraphResolved=true");

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
            metadata.put("inputKeys", inputKeys);
            metadata.put("outputKeys", outputKeys);
            debugTrace.add("mode=depth_limit");
            recordCallMetadata(context, resolvedSubgraphRef, metadata);
            writeResult(null, resolvedInputs, Map.of(), Map.of(), metadata, debugTrace, false);
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
            metadata.put("inputKeys", inputKeys);
            metadata.put("outputKeys", outputKeys);
            debugTrace.add("mode=recursive_call_blocked");
            recordCallMetadata(context, resolvedSubgraphRef, metadata);
            writeResult(null, resolvedInputs, Map.of(), Map.of(), metadata, debugTrace, false);
            return;
        }

        NestedExecutionResult nestedResult = executeSubgraph(context, subgraph, resolvedSubgraphRef, resolvedInputs);
        Map<String, Object> executedOutputs = nestedResult.outputs();
        debugTrace.add("nestedExecuted=" + nestedResult.executed());
        debugTrace.add("nestedSuccess=" + nestedResult.success());

        boolean hadExternalOutput = executedOutputs.containsKey(resolvedOutputKey);
        Map<String, Object> mappedOutputs = resolveMappedOutputs(executedOutputs, outputKeys, resolvedOutputKey, mappedInputValue);
        Object resolvedOutputValue = mappedOutputs.get(resolvedOutputKey);

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
        metadata.put("inputKeys", inputKeys);
        metadata.put("outputKeys", outputKeys);
        metadata.put("mappedInputCount", resolvedInputs.size());
        metadata.put("mappedOutputCount", mappedOutputs.size());

        recordCallMetadata(context, resolvedSubgraphRef, metadata);
        debugTrace.add("mode=" + (nestedResult.executed() ? "executed" : "execution_failed"));
        writeResult(
            resolvedOutputValue,
            resolvedInputs,
            new LinkedHashMap<>(executedOutputs),
            mappedOutputs,
            metadata,
            debugTrace,
            nestedResult.success() && (!strictMode || hasAllRequestedOutputKeys(mappedOutputs, outputKeys))
        );
    }

    private void writeResult(
        Object value,
        Map<String, Object> inputs,
        Map<String, Object> outputs,
        Map<String, Object> mappedOutputs,
        Map<String, Object> metadata,
        List<Object> debugTrace,
        boolean valid
    ) {
        outputValues.put(OUTPUT_VALUE_ID, value);
        outputValues.put(OUTPUT_INPUTS_ID, inputs);
        outputValues.put(OUTPUT_OUTPUTS_ID, outputs);
        outputValues.put(OUTPUT_MAPPED_OUTPUTS_ID, mappedOutputs);
        outputValues.put(OUTPUT_METADATA_ID, metadata);
        outputValues.put(OUTPUT_DEBUG_TRACE_ID, emitDebugTrace ? debugTrace : List.of());
        outputValues.put(OUTPUT_VALID_ID, valid);
        writeDynamicOutputValues(mappedOutputs);
    }

    private void writeDynamicOutputValues(Map<String, Object> mappedOutputs) {
        for (String key : activeDynamicOutputKeys) {
            String portId = dynamicOutputPortId(key);
            outputValues.put(portId, mappedOutputs.get(key));
        }
    }

    private Map<String, Object> resolveMappedOutputs(
        Map<String, Object> availableOutputs,
        List<String> outputKeys,
        String primaryOutputKey,
        Object primaryFallback
    ) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (String key : outputKeys) {
            if (availableOutputs.containsKey(key)) {
                mapped.put(key, availableOutputs.get(key));
                continue;
            }
            if (strictMode) {
                mapped.put(key, null);
                continue;
            }
            if (primaryOutputKey.equals(key)) {
                mapped.put(key, primaryFallback);
                availableOutputs.putIfAbsent(key, primaryFallback);
            } else {
                mapped.put(key, null);
            }
        }
        return mapped;
    }

    private boolean hasAllRequestedOutputKeys(Map<String, Object> outputs, List<String> requiredKeys) {
        for (String key : requiredKeys) {
            if (!outputs.containsKey(key) || outputs.get(key) == null) {
                return false;
            }
        }
        return true;
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

    private List<String> resolveRequestedKeys(String primaryKey, String propertyKeys, Object overrideKeysObj) {
        Set<String> keys = new LinkedHashSet<>();
        if (primaryKey != null && !primaryKey.isBlank()) {
            keys.add(primaryKey);
        }
        addKeysFromCsv(keys, propertyKeys);
        addKeysFromObject(keys, overrideKeysObj);
        return new ArrayList<>(keys);
    }

    private void rebuildDynamicPorts(List<String> inputKeys, List<String> outputKeys) {
        Set<String> desiredInputKeys = new LinkedHashSet<>(inputKeys);
        Set<String> desiredOutputKeys = new LinkedHashSet<>(outputKeys);

        if (desiredInputKeys.equals(activeDynamicInputKeys) && desiredOutputKeys.equals(activeDynamicOutputKeys)) {
            return;
        }

        inputPorts.removeIf(port -> port.getId().startsWith(DYNAMIC_INPUT_PREFIX));
        outputPorts.removeIf(port -> port.getId().startsWith(DYNAMIC_OUTPUT_PREFIX));

        for (String key : desiredInputKeys) {
            String portId = dynamicInputPortId(key);
            addInputPort(new BasePort(
                portId,
                "In " + key,
                "Dynamic subgraph input for key: " + key,
                NodeDataType.ANY,
                this
            ));
        }

        for (String key : desiredOutputKeys) {
            String portId = dynamicOutputPortId(key);
            addOutputPort(new BasePort(
                portId,
                "Out " + key,
                "Dynamic subgraph output for key: " + key,
                NodeDataType.ANY,
                this
            ));
        }

        activeDynamicInputKeys = desiredInputKeys;
        activeDynamicOutputKeys = desiredOutputKeys;
    }

    private String dynamicInputPortId(String key) {
        return DYNAMIC_INPUT_PREFIX + keyToToken(key);
    }

    private String dynamicOutputPortId(String key) {
        return DYNAMIC_OUTPUT_PREFIX + keyToToken(key);
    }

    private String keyToToken(String key) {
        if (key == null || key.isBlank()) {
            return "empty";
        }
        String normalized = NON_ALNUM_UNDERSCORE.matcher(key.trim()).replaceAll("_");
        return normalized.isEmpty() ? "empty" : normalized;
    }

    private void addKeysFromCsv(Set<String> keys, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        String[] parts = csv.split(",");
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (!trimmed.isEmpty()) {
                keys.add(trimmed);
            }
        }
    }

    private void addKeysFromObject(Set<String> keys, Object value) {
        if (value instanceof String csv) {
            addKeysFromCsv(keys, csv);
            return;
        }
        if (value instanceof List<?> list) {
            for (Object entry : list) {
                if (entry == null) {
                    continue;
                }
                String normalized = String.valueOf(entry).trim();
                if (!normalized.isEmpty()) {
                    keys.add(normalized);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private NodeGraph resolveSubgraphGraph(@Nullable ExecutionContext context, String ref, Object directGraphValue) {
        NodeGraph direct = toNodeGraph(directGraphValue);
        if (direct != null) {
            return cloneGraph(direct);
        }

        NodeGraph embedded = toNodeGraph(embeddedGraphJson);
        if (embedded != null) {
            return cloneGraph(embedded);
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
        if (context == null) {
            return;
        }
        Object existing = context.getVariable(GraphIOKeys.SUBGRAPH_CALLS_KEY);
        Map<String, Object> calls;
        if (existing instanceof Map<?, ?> map) {
            calls = (Map<String, Object>) map;
        } else {
            calls = new LinkedHashMap<>();
            context.setVariable(GraphIOKeys.SUBGRAPH_CALLS_KEY, calls);
        }
        calls.put(subgraphRefName + "::" + getId(), new LinkedHashMap<>(metadata));
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("subgraphRef", subgraphRef);
        state.put("inputKey", inputKey);
        state.put("outputKey", outputKey);
        state.put("strictMode", strictMode);
        state.put("maxCallDepth", maxCallDepth);
        state.put("additionalInputKeys", additionalInputKeys);
        state.put("additionalOutputKeys", additionalOutputKeys);
        state.put("emitDebugTrace", emitDebugTrace);
        state.put("embeddedGraphJson", embeddedGraphJson);
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
        Object additionalInputKeysObj = map.get("additionalInputKeys");
        if (additionalInputKeysObj instanceof String value) {
            additionalInputKeys = value;
        }
        Object additionalOutputKeysObj = map.get("additionalOutputKeys");
        if (additionalOutputKeysObj instanceof String value) {
            additionalOutputKeys = value;
        }
        Object emitDebugTraceObj = map.get("emitDebugTrace");
        if (emitDebugTraceObj instanceof Boolean value) {
            emitDebugTrace = value;
        }
        Object embeddedGraphJsonObj = map.get("embeddedGraphJson");
        if (embeddedGraphJsonObj instanceof String value) {
            embeddedGraphJson = value;
        }
        rebuildDynamicPorts(
            resolveRequestedKeys(resolveInputKey(), additionalInputKeys, null),
            resolveRequestedKeys(resolveOutputKey(), additionalOutputKeys, null)
        );
    }

    private record NestedExecutionResult(boolean executed, boolean success, Map<String, Object> outputs, @Nullable String errorMessage) {
    }
}
