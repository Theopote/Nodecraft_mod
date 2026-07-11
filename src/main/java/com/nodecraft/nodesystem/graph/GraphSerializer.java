package com.nodecraft.nodesystem.graph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nodecraft.core.exception.NodeValidationException;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.io.SavedPosition;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outcome of restoring a single node from saved metadata.
 */
record NodeRestoreResult(@Nullable BaseNode node, boolean unknownTypeSkipped) {
}

/**
 * Serialization utilities for NodeGraph and SavedGraph.
 */
public class GraphSerializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphSerializer.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GraphSerializer() {
        // Utility class.
    }

    public static SavedGraph toSavedGraph(NodeGraph graph) {
        if (graph == null) return null;

        SavedGraph savedGraph = new SavedGraph();
        savedGraph.graphName = graph.getName();
        savedGraph.nodes = new ArrayList<>();
        savedGraph.connections = new ArrayList<>();
        savedGraph.nodePositions = new HashMap<>();
        NodeRegistry registry = NodeRegistry.getInstance();

        for (INode node : graph.getNodes()) {
            if (node instanceof BaseNode baseNode) {
                SavedNode savedNode = new SavedNode();
                savedNode.nodeId = baseNode.getId().toString();
                savedNode.typeId = registry.resolveCanonicalNodeId(baseNode.getTypeId());
                savedNode.state = baseNode.getNodeState();
                savedGraph.nodes.add(savedNode);
                savedGraph.nodePositions.put(
                    savedNode.nodeId,
                    new SavedPosition((float) baseNode.getPositionX(), (float) baseNode.getPositionY())
                );
            } else {
                LOGGER.warn("Skipping non-BaseNode while saving: {}", node.getId());
            }
        }

        for (NodeGraph.Connection conn : graph.getConnections()) {
            SavedConnection savedConn = new SavedConnection();
            savedConn.sourceNodeId = conn.sourceNode.getId().toString();
            savedConn.sourcePortId = conn.sourcePort.getId();
            savedConn.targetNodeId = conn.targetNode.getId().toString();
            savedConn.targetPortId = conn.targetPort.getId();
            savedGraph.connections.add(savedConn);
        }

        return savedGraph;
    }

    public static String toJson(SavedGraph savedGraph) {
        return GSON.toJson(savedGraph);
    }

    public static String toJson(NodeGraph graph) {
        return toJson(toSavedGraph(graph));
    }

    public static void saveToFile(NodeGraph graph, Path filePath) throws IOException {
        String json = toJson(graph);
        Files.writeString(filePath, json, StandardCharsets.UTF_8);
        LOGGER.info("Saved graph to {}", filePath);
    }

    public static SavedGraph fromJson(String json) {
        return GSON.fromJson(json, SavedGraph.class);
    }

    public static NodeGraph fromSavedGraph(SavedGraph savedGraph) {
        return loadFromSavedGraph(savedGraph).graph();
    }

    /**
     * Rebuilds a graph from saved data, skipping unknown node types instead of aborting the whole load.
     */
    public static GraphLoadResult loadFromSavedGraph(@Nullable SavedGraph savedGraph) {
        if (savedGraph == null) {
            return GraphLoadResult.empty("Loaded Graph");
        }

        String graphName = savedGraph.graphName != null ? savedGraph.graphName : "Loaded Graph";
        NodeGraph graph = new NodeGraph(graphName);
        List<String> warnings = new ArrayList<>();
        Map<String, BaseNode> nodesBySavedId = new HashMap<>();
        int skippedUnknownNodeTypes = 0;

        List<SavedNode> nodes = savedGraph.nodes != null ? savedGraph.nodes : List.of();
        List<SavedConnection> connections = savedGraph.connections != null ? savedGraph.connections : List.of();

        for (SavedNode savedNode : nodes) {
            NodeRestoreResult restored = tryRestoreNode(savedNode, warnings);
            if (restored.unknownTypeSkipped()) {
                skippedUnknownNodeTypes++;
            }
            if (restored.node() == null) {
                continue;
            }

            BaseNode newNode = restored.node();
            applySavedPosition(savedGraph, savedNode, newNode);

            try {
                nodesBySavedId.put(savedNode.nodeId, newNode);
                graph.addNode(newNode);
            } catch (Exception e) {
                nodesBySavedId.remove(savedNode.nodeId);
                LOGGER.error("Failed adding node to graph: type={}, id={}", savedNode.typeId, savedNode.nodeId, e);
            }
        }

        for (SavedConnection connection : connections) {
            if (connection == null) {
                continue;
            }
            BaseNode sourceNode = nodesBySavedId.get(connection.sourceNodeId);
            BaseNode targetNode = nodesBySavedId.get(connection.targetNodeId);

            if (sourceNode != null && targetNode != null) {
                boolean success = graph.connect(
                    sourceNode.getId(), connection.sourcePortId,
                    targetNode.getId(), connection.targetPortId
                );
                if (!success) {
                    LOGGER.warn("Failed rebuilding connection: {} ({}) -> {} ({})",
                        connection.sourceNodeId, connection.sourcePortId,
                        connection.targetNodeId, connection.targetPortId);
                }
            } else {
                LOGGER.warn("Failed rebuilding connection: missing node instance for {} -> {}",
                    connection.sourceNodeId, connection.targetNodeId);
            }
        }

        return new GraphLoadResult(graph, Map.copyOf(nodesBySavedId), skippedUnknownNodeTypes, List.copyOf(warnings));
    }

    public static Optional<BaseNode> tryRestoreNode(@Nullable SavedNode savedNode) {
        return Optional.ofNullable(tryRestoreNode(savedNode, null).node());
    }

    static NodeRestoreResult tryRestoreNode(@Nullable SavedNode savedNode, @Nullable List<String> warnings) {
        if (savedNode == null || savedNode.typeId == null || savedNode.nodeId == null) {
            return new NodeRestoreResult(null, false);
        }

        NodeRegistry registry = NodeRegistry.getInstance();
        try {
            INode node = registry.createNodeInstance(savedNode.typeId);
            if (!(node instanceof BaseNode baseNode)) {
                if (node == null) {
                    LOGGER.warn("Cannot create node instance (null): type={}, id={}", savedNode.typeId, savedNode.nodeId);
                } else {
                    LOGGER.warn("Cannot load node: not BaseNode. type={}, id={}, actual={}",
                        savedNode.typeId, savedNode.nodeId, node.getClass().getName());
                }
                return new NodeRestoreResult(null, false);
            }

            try {
                baseNode.setNodeState(savedNode.state);
            } catch (Exception e) {
                if (warnings != null) {
                    GraphLoadResult.addStateRestoreWarning(warnings);
                }
                LOGGER.warn("Failed restoring node state: type={}, id={}", savedNode.typeId, savedNode.nodeId, e);
            }

            return new NodeRestoreResult(baseNode, false);
        } catch (NodeValidationException e) {
            LOGGER.warn("Skipping unregistered node type: {} (saved id: {})", savedNode.typeId, savedNode.nodeId);
            return new NodeRestoreResult(null, true);
        }
    }

    private static void applySavedPosition(SavedGraph savedGraph, SavedNode savedNode, BaseNode node) {
        if (savedGraph.nodePositions == null || savedNode == null || savedNode.nodeId == null) {
            return;
        }
        SavedPosition savedPosition = savedGraph.nodePositions.get(savedNode.nodeId);
        if (savedPosition != null) {
            node.setPosition(savedPosition.x, savedPosition.y);
        }
    }

    public static NodeGraph fromJsonToGraph(String json) {
        SavedGraph savedGraph = fromJson(json);
        return fromSavedGraph(savedGraph);
    }

    public static GraphLoadResult loadFromJson(String json) {
        return loadFromSavedGraph(fromJson(json));
    }

    public static NodeGraph loadFromFile(Path filePath) throws IOException {
        String json = Files.readString(filePath, StandardCharsets.UTF_8);
        return fromJsonToGraph(json);
    }
}
