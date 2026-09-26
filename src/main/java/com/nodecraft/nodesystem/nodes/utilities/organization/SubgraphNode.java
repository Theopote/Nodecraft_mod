package com.nodecraft.nodesystem.nodes.utilities.organization;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeEffect;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.execution.subgraph.SubgraphCallFrameBridge;
import com.nodecraft.nodesystem.graph.GraphInterfaceValidator;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.graph.SubgraphInterfaceScanner;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@NodeInfo(
    effect = NodeEffect.COMPOSITE,
    id = "utilities.organization.subgraph",
    displayName = "Subgraph",
    description = "Executes a referenced subgraph definition with typed interface ports.",
    category = "utilities.organization",
    order = 2
)
public class SubgraphNode extends BaseNode {

    private String displayName = "Subgraph";

    @NodeProperty(displayName = "Subgraph Ref", category = "Subgraph", order = 1)
    private String subgraphRef = "subgraph";

    @NodeProperty(displayName = "Enabled", category = "Subgraph", order = 2)
    private boolean enabled = true;

    private static final String INPUT_ENABLED_ID = "input_enabled";
    private static final String OUTPUT_VALID_ID = "output_valid";
    private static final String OUTPUT_ERROR_ID = "output_error";

    private Set<String> activeDynamicInputKeys = new LinkedHashSet<>();
    private Set<String> activeDynamicOutputKeys = new LinkedHashSet<>();

    public SubgraphNode() {
        super(UUID.randomUUID(), "utilities.organization.subgraph");

        addInputPort(new BasePort(
                INPUT_ENABLED_ID,
                "Enabled",
                "Disables subgraph execution when false",
                NodeDataType.BOOLEAN,
                this
        ));

        addOutputPort(new BasePort(OUTPUT_VALID_ID, "Valid", "Whether subgraph execution succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_ERROR_ID, "Error", "Why subgraph execution failed", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return displayName == null || displayName.isBlank() ? "Subgraph" : displayName;
    }

    @Override
    public String getDescription() {
        return "Executes a referenced subgraph definition with typed interface ports.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        boolean runtimeEnabled = !Boolean.FALSE.equals(inputValues.get(INPUT_ENABLED_ID));
        boolean active = enabled && runtimeEnabled;
        String ref = resolvedSubgraphRef();

        if (!active) {
            writeDisabledOutputs();
            outputValues.put(OUTPUT_VALID_ID, true);
            outputValues.put(OUTPUT_ERROR_ID, "");
            return;
        }

        if (ref == null || ref.isBlank()) {
            writeFailure("Subgraph ref is empty");
            return;
        }

        SavedGraph definition = resolveDefinition(context, ref);
        if (definition == null) {
            writeFailure("Missing subgraph definition for ref: " + ref);
            return;
        }

        GraphInterfaceValidator.ValidationResult validation = GraphInterfaceValidator.validate(definition);
        if (!validation.valid()) {
            writeFailure(validation.error() == null ? "Invalid subgraph interface" : validation.error());
            return;
        }

        SubgraphInterfaceScanner.InterfaceSpec interfaceSpec = SubgraphInterfaceScanner.scan(definition);
        rebuildDynamicPorts(interfaceSpec);

        int depth = SubgraphCallStackBridge.depth(context);
        if (depth >= GenerationLimits.MAX_SUBGRAPH_CALL_DEPTH) {
            writeFailure("Maximum subgraph call depth reached: " + GenerationLimits.MAX_SUBGRAPH_CALL_DEPTH);
            return;
        }

        if (SubgraphCallStackBridge.contains(context, ref)) {
            writeFailure("Detected recursive subgraph call for ref: " + ref);
            return;
        }

        Map<String, Object> inputs = collectInputs(interfaceSpec.inputs());
        SubgraphCallStackBridge.StackFrame stackFrame = SubgraphCallStackBridge.push(context, ref);
        SubgraphCallFrameBridge.FrameHandle frameHandle = SubgraphCallFrameBridge.push(context, ref, inputs);
        Map<String, SavedGraph> previousDefinitions = null;
        try {
            if (context == null) {
                writeFailure("Execution context is required for subgraph execution");
                return;
            }

            previousDefinitions = context.getSubgraphDefinitions();
            context.setSubgraphDefinitions(mergeDefinitions(previousDefinitions, definition));

            NodeGraph subgraph = GraphSerializer.fromSavedGraph(definition);
            invalidateGraphIoCache(subgraph);

            boolean skipSideEffects = context.isSkipOutputExecuteSideEffects();
            boolean success = NodeExecutor.nestedSync(subgraph, context, skipSideEffects).executeSync();

            Map<String, Object> outputs = SubgraphCallFrameBridge.current(context) != null
                    ? new LinkedHashMap<>(Objects.requireNonNull(SubgraphCallFrameBridge.current(context)).outputs())
                    : Map.of();

            writeDynamicOutputs(outputs, interfaceSpec.outputs());
            outputValues.put(OUTPUT_VALID_ID, success);
            outputValues.put(OUTPUT_ERROR_ID, success ? "" : "Nested node execution reported failure");
        } catch (Exception e) {
            writeFailure(e.getMessage() == null ? "Subgraph execution failed" : e.getMessage());
        } finally {
            if (context != null && previousDefinitions != null) {
                context.setSubgraphDefinitions(previousDefinitions);
            }
            SubgraphCallFrameBridge.restore(frameHandle);
            SubgraphCallStackBridge.restore(stackFrame);
        }
    }

    private void writeDisabledOutputs() {
        for (String key : activeDynamicOutputKeys) {
            outputValues.put(SubgraphPortIds.dynamicOutputPortId(key), null);
        }
        outputValues.put(OUTPUT_VALID_ID, true);
        outputValues.put(OUTPUT_ERROR_ID, "");
    }

    private void writeFailure(String error) {
        for (String key : activeDynamicOutputKeys) {
            outputValues.put(SubgraphPortIds.dynamicOutputPortId(key), null);
        }
        outputValues.put(OUTPUT_VALID_ID, false);
        outputValues.put(OUTPUT_ERROR_ID, error == null ? "" : error);
    }

    private Map<String, Object> collectInputs(List<SubgraphInterfaceScanner.InputSpec> inputSpecs) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        for (SubgraphInterfaceScanner.InputSpec spec : inputSpecs) {
            String portId = SubgraphPortIds.dynamicInputPortId(spec.name());
            if (inputValues.containsKey(portId)) {
                inputs.put(spec.name(), inputValues.get(portId));
            }
        }
        return inputs;
    }

    private void writeDynamicOutputs(Map<String, Object> outputs, List<SubgraphInterfaceScanner.OutputSpec> outputSpecs) {
        for (SubgraphInterfaceScanner.OutputSpec spec : outputSpecs) {
            outputValues.put(SubgraphPortIds.dynamicOutputPortId(spec.name()), outputs.get(spec.name()));
        }
    }

    private void invalidateGraphIoCache(NodeGraph subgraph) {
        if (subgraph == null) {
            return;
        }
        List<UUID> graphIoNodeIds = new ArrayList<>();
        for (var node : subgraph.getNodes()) {
            String typeId = node.getTypeId();
            if (SubgraphInterfaceScanner.GRAPH_INPUT_TYPE_ID.equals(typeId)
                    || SubgraphInterfaceScanner.GRAPH_OUTPUT_TYPE_ID.equals(typeId)) {
                graphIoNodeIds.add(node.getId());
            }
        }
        subgraph.getExecutionCache().invalidateAll(graphIoNodeIds);
        subgraph.getExecutionCache().clear();
    }

    @SuppressWarnings("unchecked")
    private SavedGraph resolveDefinition(@Nullable ExecutionContext context, String ref) {
        if (context != null) {
            SavedGraph fromContext = context.getSubgraphDefinitions().get(ref);
            if (fromContext != null) {
                return fromContext;
            }
            Object raw = context.getVariable(GraphIOKeys.SUBGRAPH_DEFINITIONS_KEY);
            if (raw instanceof Map<?, ?> map) {
                Object value = ((Map<String, Object>) map).get(ref);
                if (value instanceof SavedGraph saved) {
                    return saved;
                }
            }
        }
        return null;
    }

    private static Map<String, SavedGraph> mergeDefinitions(
            @Nullable Map<String, SavedGraph> parent,
            SavedGraph childDefinition
    ) {
        Map<String, SavedGraph> merged = new LinkedHashMap<>();
        if (parent != null) {
            merged.putAll(parent);
        }
        if (childDefinition.subgraphDefinitions != null) {
            merged.putAll(childDefinition.subgraphDefinitions);
        }
        return merged;
    }

    private String resolvedSubgraphRef() {
        if (subgraphRef == null || subgraphRef.isBlank()) {
            return null;
        }
        return subgraphRef.trim();
    }

    public void syncPortsFromDefinition(@Nullable SavedGraph definition) {
        if (definition == null) {
            return;
        }
        SubgraphInterfaceScanner.InterfaceSpec spec = SubgraphInterfaceScanner.scan(definition);
        if (spec.valid()) {
            rebuildDynamicPorts(spec);
        }
    }

    private void rebuildDynamicPorts(SubgraphInterfaceScanner.InterfaceSpec interfaceSpec) {
        Set<String> desiredInputKeys = new LinkedHashSet<>();
        Set<String> desiredOutputKeys = new LinkedHashSet<>();
        Map<String, NodeDataType> inputTypes = new LinkedHashMap<>();
        Map<String, NodeDataType> outputTypes = new LinkedHashMap<>();

        for (SubgraphInterfaceScanner.InputSpec input : interfaceSpec.inputs()) {
            desiredInputKeys.add(input.name());
            inputTypes.put(input.name(), input.type());
        }
        for (SubgraphInterfaceScanner.OutputSpec output : interfaceSpec.outputs()) {
            desiredOutputKeys.add(output.name());
            outputTypes.put(output.name(), output.type());
        }

        if (desiredInputKeys.equals(activeDynamicInputKeys) && desiredOutputKeys.equals(activeDynamicOutputKeys)) {
            return;
        }

        inputPorts.removeIf(port -> port.getId().startsWith(SubgraphPortIds.DYNAMIC_INPUT_PREFIX));
        outputPorts.removeIf(port -> port.getId().startsWith(SubgraphPortIds.DYNAMIC_OUTPUT_PREFIX));

        for (String key : desiredInputKeys) {
            NodeDataType type = inputTypes.getOrDefault(key, NodeDataType.ANY);
            addInputPort(new BasePort(
                    SubgraphPortIds.dynamicInputPortId(key),
                    "In " + key,
                    "Subgraph input: " + key,
                    type,
                    this
            ));
        }

        for (String key : desiredOutputKeys) {
            NodeDataType type = outputTypes.getOrDefault(key, NodeDataType.ANY);
            addOutputPort(new BasePort(
                    SubgraphPortIds.dynamicOutputPortId(key),
                    "Out " + key,
                    "Subgraph output: " + key,
                    type,
                    this
            ));
        }

        activeDynamicInputKeys = desiredInputKeys;
        activeDynamicOutputKeys = desiredOutputKeys;
    }

    @Override
    public Object getNodeState() {
        Map<String, Object> state = new HashMap<>();
        state.put("displayName", getDisplayName());
        state.put("subgraphRef", subgraphRef);
        state.put("enabled", enabled);
        return state;
    }

    @Override
    public void setNodeState(Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return;
        }
        Object displayNameObj = map.get("displayName");
        if (displayNameObj instanceof String value && !value.isBlank()) {
            displayName = value.trim();
        }
        Object subgraphRefObj = map.get("subgraphRef");
        if (subgraphRefObj instanceof String value) {
            subgraphRef = value;
        }
        Object enabledObj = map.get("enabled");
        if (enabledObj instanceof Boolean value) {
            enabled = value;
        }
    }
}
