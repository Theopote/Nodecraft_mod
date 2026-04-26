package com.nodecraft.nodesystem.nodes.utilities.organization;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.api.NodeInfo;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.core.BasePort;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.GraphSerializer;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.io.SavedGraph;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@NodeInfo(
    id = "utilities.organization.subgraph_register",
    displayName = "Subgraph Register",
    description = "Registers a subgraph reference into execution context for Subgraph calls.",
    category = "utilities.organization",
    order = 6
)
public class SubgraphRegisterNode extends BaseNode {

    private static final Map<String, Object> FALLBACK_REGISTRY = new ConcurrentHashMap<>();

    @NodeProperty(displayName = "Default Ref", category = "Subgraph Register", order = 1)
    private String defaultRef = "subgraph";

    private static final String INPUT_SUBGRAPH_REF_ID = "input_subgraph_ref";
    private static final String INPUT_SUBGRAPH_GRAPH_ID = "input_subgraph_graph";
    private static final String INPUT_OVERWRITE_ID = "input_overwrite";

    private static final String OUTPUT_SUCCESS_ID = "output_success";
    private static final String OUTPUT_REF_ID = "output_ref";
    private static final String OUTPUT_REGISTRY_ID = "output_registry";
    private static final String OUTPUT_MESSAGE_ID = "output_message";

    public SubgraphRegisterNode() {
        super(UUID.randomUUID(), "utilities.organization.subgraph_register");

        addInputPort(new BasePort(INPUT_SUBGRAPH_REF_ID, "Subgraph Ref", "Reference key to register", NodeDataType.STRING, this));
        addInputPort(new BasePort(INPUT_SUBGRAPH_GRAPH_ID, "Subgraph Graph", "Graph object / SavedGraph / JSON string", NodeDataType.ANY, this));
        addInputPort(new BasePort(INPUT_OVERWRITE_ID, "Overwrite", "Whether existing key can be replaced", NodeDataType.BOOLEAN, this));

        addOutputPort(new BasePort(OUTPUT_SUCCESS_ID, "Success", "Whether registration succeeded", NodeDataType.BOOLEAN, this));
        addOutputPort(new BasePort(OUTPUT_REF_ID, "Ref", "Resolved subgraph reference", NodeDataType.STRING, this));
        addOutputPort(new BasePort(OUTPUT_REGISTRY_ID, "Registry", "Current subgraph registry map", NodeDataType.ANY, this));
        addOutputPort(new BasePort(OUTPUT_MESSAGE_ID, "Message", "Registration result message", NodeDataType.STRING, this));
    }

    @Override
    public String getDisplayName() {
        return "Subgraph Register";
    }

    @Override
    public String getDescription() {
        return "Registers a subgraph reference into execution context for Subgraph calls.";
    }

    @Override
    public void processNode(@Nullable ExecutionContext context) {
        String ref = resolveRef(inputValues.get(INPUT_SUBGRAPH_REF_ID));
        Object graphObj = inputValues.get(INPUT_SUBGRAPH_GRAPH_ID);
        boolean overwrite = Boolean.TRUE.equals(inputValues.get(INPUT_OVERWRITE_ID));

        Map<String, Object> registry = getOrCreateRegistry(context);
        if (ref == null || ref.isBlank()) {
            outputValues.put(OUTPUT_SUCCESS_ID, false);
            outputValues.put(OUTPUT_REF_ID, "");
            outputValues.put(OUTPUT_REGISTRY_ID, new LinkedHashMap<>(registry));
            outputValues.put(OUTPUT_MESSAGE_ID, "Subgraph reference is empty.");
            return;
        }

        NodeGraph graph = toNodeGraph(graphObj);
        if (graph == null) {
            outputValues.put(OUTPUT_SUCCESS_ID, false);
            outputValues.put(OUTPUT_REF_ID, ref);
            outputValues.put(OUTPUT_REGISTRY_ID, new LinkedHashMap<>(registry));
            outputValues.put(OUTPUT_MESSAGE_ID, "Invalid subgraph graph input.");
            return;
        }

        if (!overwrite && registry.containsKey(ref)) {
            outputValues.put(OUTPUT_SUCCESS_ID, false);
            outputValues.put(OUTPUT_REF_ID, ref);
            outputValues.put(OUTPUT_REGISTRY_ID, new LinkedHashMap<>(registry));
            outputValues.put(OUTPUT_MESSAGE_ID, "Reference already exists and overwrite is false.");
            return;
        }

        registry.put(ref, cloneGraph(graph));
        outputValues.put(OUTPUT_SUCCESS_ID, true);
        outputValues.put(OUTPUT_REF_ID, ref);
        outputValues.put(OUTPUT_REGISTRY_ID, new LinkedHashMap<>(registry));
        outputValues.put(OUTPUT_MESSAGE_ID, "Registered subgraph: " + ref);
    }

    private String resolveRef(Object inputRef) {
        if (inputRef instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        if (defaultRef == null || defaultRef.isBlank()) {
            return null;
        }
        return defaultRef.trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateRegistry(@Nullable ExecutionContext context) {
        if (context != null) {
            Object existing = context.getVariable(GraphIOKeys.SUBGRAPH_REGISTRY_KEY);
            if (existing instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            Map<String, Object> created = new LinkedHashMap<>();
            context.setVariable(GraphIOKeys.SUBGRAPH_REGISTRY_KEY, created);
            return created;
        }
        return FALLBACK_REGISTRY;
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
}

