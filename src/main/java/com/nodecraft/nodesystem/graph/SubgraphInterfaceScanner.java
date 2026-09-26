package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Scans child graph Graph Input / Graph Output nodes to build a typed subgraph interface.
 */
public final class SubgraphInterfaceScanner {

    public static final String GRAPH_INPUT_TYPE_ID = SubgraphExtractionService.GRAPH_INPUT_TYPE_ID;
    public static final String GRAPH_OUTPUT_TYPE_ID = SubgraphExtractionService.GRAPH_OUTPUT_TYPE_ID;

    private SubgraphInterfaceScanner() {
    }

    public record InputSpec(String name, NodeDataType type, boolean required) {
    }

    public record OutputSpec(String name, NodeDataType type) {
    }

    public record InterfaceSpec(
            List<InputSpec> inputs,
            List<OutputSpec> outputs,
            boolean valid,
            @Nullable String error
    ) {
        public static InterfaceSpec invalid(String error) {
            return new InterfaceSpec(List.of(), List.of(), false, error);
        }
    }

    public static InterfaceSpec scan(NodeGraph graph) {
        if (graph == null) {
            return InterfaceSpec.invalid("Graph is null");
        }
        SavedGraph saved = GraphSerializer.toSavedGraph(graph);
        return scan(saved);
    }

    public static InterfaceSpec scan(SavedGraph saved) {
        if (saved == null || saved.nodes == null || saved.nodes.isEmpty()) {
            return InterfaceSpec.invalid("Subgraph definition is empty");
        }

        List<InputSpec> inputs = new ArrayList<>();
        List<OutputSpec> outputs = new ArrayList<>();
        Set<String> inputNames = new HashSet<>();
        Set<String> outputNames = new HashSet<>();

        for (SavedNode node : saved.nodes) {
            if (node == null || node.typeId == null) {
                continue;
            }
            String typeId = node.typeId.toLowerCase(Locale.ROOT);
            Map<String, Object> state = toStateMap(node.state);

            if (GRAPH_INPUT_TYPE_ID.equals(typeId)) {
                String name = readName(state, "inputName", "input");
                if (!inputNames.add(name)) {
                    return InterfaceSpec.invalid("Duplicate graph input name: " + name);
                }
                inputs.add(new InputSpec(name, readDeclaredType(state), readRequired(state)));
            } else if (GRAPH_OUTPUT_TYPE_ID.equals(typeId)) {
                String name = readName(state, "outputName", "output");
                if (!outputNames.add(name)) {
                    return InterfaceSpec.invalid("Duplicate graph output name: " + name);
                }
                outputs.add(new OutputSpec(name, readDeclaredType(state)));
            }
        }

        if (inputs.isEmpty() && outputs.isEmpty()) {
            return InterfaceSpec.invalid("Subgraph has no Graph Input or Graph Output nodes");
        }

        return new InterfaceSpec(List.copyOf(inputs), List.copyOf(outputs), true, null);
    }

    private static Map<String, Object> toStateMap(@Nullable Object state) {
        if (!(state instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                normalized.put(key, entry.getValue());
            }
        }
        return normalized;
    }

    private static String readName(Map<String, Object> state, String key, String fallback) {
        Object raw = state.get(key);
        if (raw instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        return fallback;
    }

    private static boolean readRequired(Map<String, Object> state) {
        Object raw = state.get("required");
        return raw instanceof Boolean value && value;
    }

    private static NodeDataType readDeclaredType(Map<String, Object> state) {
        Object raw = state.get("declaredType");
        if (!(raw instanceof String typeId) || typeId.isBlank()) {
            raw = state.get("inferredType");
        }
        if (raw instanceof String typeId && !typeId.isBlank()) {
            NodeDataType resolved = NodeDataType.fromId(typeId.trim());
            if (resolved != null) {
                return resolved;
            }
        }
        return NodeDataType.ANY;
    }
}
