package com.nodecraft.nodesystem.graph;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.io.SavedPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Extracts a selected part of a graph into a reusable subgraph definition.
 *
 * <p>This is the model-level operation behind "Create Subgraph From Selection".
 * It does not mutate the source graph or create editor UI nodes. Callers can use
 * the returned boundary bindings to replace the selection with a Subgraph node
 * in a later editor transaction.</p>
 */
public final class SubgraphExtractionService {

    public static final String GRAPH_INPUT_TYPE_ID = "utilities.organization.graph_input";
    public static final String GRAPH_OUTPUT_TYPE_ID = "utilities.organization.graph_output";
    public static final String GRAPH_INPUT_OUTPUT_PORT_ID = "output_value";
    public static final String GRAPH_OUTPUT_INPUT_PORT_ID = "input_value";

    private static final Pattern NON_ALNUM_UNDERSCORE = Pattern.compile("[^a-zA-Z0-9_]");

    private SubgraphExtractionService() {
    }

    public static ExtractionResult extract(NodeGraph sourceGraph, Set<UUID> selectedNodeIds, String subgraphName) {
        if (sourceGraph == null) {
            throw new IllegalArgumentException("sourceGraph is required");
        }
        if (selectedNodeIds == null || selectedNodeIds.isEmpty()) {
            throw new IllegalArgumentException("selectedNodeIds is empty");
        }

        Set<UUID> selected = new LinkedHashSet<>();
        for (UUID nodeId : selectedNodeIds) {
            if (nodeId != null && sourceGraph.getNode(nodeId) != null) {
                selected.add(nodeId);
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("selectedNodeIds does not contain nodes from the graph");
        }

        SavedGraph fullSavedGraph = GraphSerializer.toSavedGraph(sourceGraph);
        Map<String, SavedNode> savedNodesById = indexSavedNodes(fullSavedGraph);

        SavedGraph subgraph = new SavedGraph();
        subgraph.graphName = normalizeName(subgraphName, sourceGraph.getName());
        subgraph.nodes = new ArrayList<>();
        subgraph.connections = new ArrayList<>();
        subgraph.nodePositions = new LinkedHashMap<>();

        for (UUID nodeId : selected) {
            SavedNode savedNode = savedNodesById.get(nodeId.toString());
            if (savedNode != null) {
                subgraph.nodes.add(savedNode);
            }
            INode node = sourceGraph.getNode(nodeId);
            if (node != null) {
                subgraph.nodePositions.put(nodeId.toString(), new SavedPosition((float) node.getPositionX(), (float) node.getPositionY()));
            }
        }

        List<InputBinding> inputBindings = new ArrayList<>();
        List<OutputBinding> outputBindings = new ArrayList<>();
        Map<BoundaryInputKey, String> inputKeyByBoundary = new LinkedHashMap<>();
        Map<BoundaryOutputKey, String> outputKeyByBoundary = new LinkedHashMap<>();
        Set<String> usedKeys = new HashSet<>();

        for (NodeGraph.Connection connection : sourceGraph.getConnections()) {
            UUID sourceId = connection.sourceNode.getId();
            UUID targetId = connection.targetNode.getId();
            boolean sourceSelected = selected.contains(sourceId);
            boolean targetSelected = selected.contains(targetId);

            if (sourceSelected && targetSelected) {
                subgraph.connections.add(savedConnection(
                    sourceId.toString(),
                    connection.sourcePort.getId(),
                    targetId.toString(),
                    connection.targetPort.getId()
                ));
                continue;
            }

            if (!sourceSelected && targetSelected) {
                BoundaryInputKey boundary = new BoundaryInputKey(sourceId, connection.sourcePort.getId(), targetId, connection.targetPort.getId());
                String inputKey = inputKeyByBoundary.computeIfAbsent(boundary,
                    ignored -> uniqueKey("in", connection.targetNode, connection.targetPort, usedKeys));
                String graphInputNodeId = UUID.randomUUID().toString();
                subgraph.nodes.add(graphInputNode(graphInputNodeId, inputKey, connection.targetPort));
                subgraph.connections.add(savedConnection(
                    graphInputNodeId,
                    GRAPH_INPUT_OUTPUT_PORT_ID,
                    targetId.toString(),
                    connection.targetPort.getId()
                ));
                inputBindings.add(new InputBinding(
                    inputKey,
                    sourceId,
                    connection.sourcePort.getId(),
                    targetId,
                    connection.targetPort.getId(),
                    dataTypeId(connection.sourcePort),
                    dataTypeId(connection.targetPort)
                ));
                continue;
            }

            if (sourceSelected) {
                BoundaryOutputKey boundary = new BoundaryOutputKey(sourceId, connection.sourcePort.getId());
                String outputKey = outputKeyByBoundary.computeIfAbsent(boundary,
                    ignored -> uniqueKey("out", connection.sourceNode, connection.sourcePort, usedKeys));
                if (outputBindings.stream().noneMatch(binding -> binding.outputKey().equals(outputKey))) {
                    String graphOutputNodeId = UUID.randomUUID().toString();
                    subgraph.nodes.add(graphOutputNode(graphOutputNodeId, outputKey, connection.sourcePort));
                    subgraph.connections.add(savedConnection(
                        sourceId.toString(),
                        connection.sourcePort.getId(),
                        graphOutputNodeId,
                        GRAPH_OUTPUT_INPUT_PORT_ID
                    ));
                }
                outputBindings.add(new OutputBinding(
                    outputKey,
                    sourceId,
                    connection.sourcePort.getId(),
                    targetId,
                    connection.targetPort.getId(),
                    dataTypeId(connection.sourcePort),
                    dataTypeId(connection.targetPort)
                ));
            }
        }

        return new ExtractionResult(
            subgraph,
            List.copyOf(inputBindings),
            List.copyOf(outputBindings),
            List.copyOf(inputKeyByBoundary.values()),
            List.copyOf(new LinkedHashSet<>(outputKeyByBoundary.values()))
        );
    }

    private static SavedNode graphInputNode(String nodeId, String inputKey, IPort targetPort) {
        SavedNode node = new SavedNode();
        node.nodeId = nodeId;
        node.typeId = GRAPH_INPUT_TYPE_ID;
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("inputName", inputKey);
        state.put("required", targetPort != null && targetPort.isRequired());
        state.put("inferredType", dataTypeId(targetPort));
        node.state = state;
        return node;
    }

    private static SavedNode graphOutputNode(String nodeId, String outputKey, IPort sourcePort) {
        SavedNode node = new SavedNode();
        node.nodeId = nodeId;
        node.typeId = GRAPH_OUTPUT_TYPE_ID;
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("outputName", outputKey);
        state.put("inferredType", dataTypeId(sourcePort));
        node.state = state;
        return node;
    }

    private static SavedConnection savedConnection(String sourceNodeId, String sourcePortId, String targetNodeId, String targetPortId) {
        SavedConnection connection = new SavedConnection();
        connection.sourceNodeId = sourceNodeId;
        connection.sourcePortId = sourcePortId;
        connection.targetNodeId = targetNodeId;
        connection.targetPortId = targetPortId;
        return connection;
    }

    private static Map<String, SavedNode> indexSavedNodes(SavedGraph savedGraph) {
        Map<String, SavedNode> byId = new HashMap<>();
        if (savedGraph == null || savedGraph.nodes == null) {
            return byId;
        }
        for (SavedNode node : savedGraph.nodes) {
            if (node != null && node.nodeId != null) {
                byId.put(node.nodeId, node);
            }
        }
        return byId;
    }

    private static String normalizeName(String requestedName, String sourceGraphName) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName.trim();
        }
        if (sourceGraphName != null && !sourceGraphName.isBlank()) {
            return sourceGraphName.trim() + " Subgraph";
        }
        return "Extracted Subgraph";
    }

    private static String uniqueKey(String prefix, INode node, IPort port, Set<String> usedKeys) {
        String base = prefix + "_" + sanitize(node != null ? node.getDisplayName() : "node")
            + "_" + sanitize(port != null ? port.getDisplayName() : "value");
        String candidate = base;
        int suffix = 2;
        while (!usedKeys.add(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private static String sanitize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        normalized = NON_ALNUM_UNDERSCORE.matcher(normalized).replaceAll("_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "value" : normalized;
    }

    private static String dataTypeId(IPort port) {
        return port != null && port.getDataType() != null ? port.getDataType().getId() : "any";
    }

    private record BoundaryInputKey(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
    }

    private record BoundaryOutputKey(UUID sourceNodeId, String sourcePortId) {
    }

    public record ExtractionResult(
        SavedGraph savedGraph,
        List<InputBinding> inputBindings,
        List<OutputBinding> outputBindings,
        List<String> inputKeys,
        List<String> outputKeys
    ) {
    }

    public record InputBinding(
        String inputKey,
        UUID externalSourceNodeId,
        String externalSourcePortId,
        UUID internalTargetNodeId,
        String internalTargetPortId,
        String externalType,
        String internalType
    ) {
    }

    public record OutputBinding(
        String outputKey,
        UUID internalSourceNodeId,
        String internalSourcePortId,
        UUID externalTargetNodeId,
        String externalTargetPortId,
        String internalType,
        String externalType
    ) {
    }
}
