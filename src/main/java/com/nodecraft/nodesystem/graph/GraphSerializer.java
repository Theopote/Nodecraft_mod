package com.nodecraft.nodesystem.graph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.io.SavedConnection;
import com.nodecraft.nodesystem.io.SavedGraph;
import com.nodecraft.nodesystem.io.SavedNode;
import com.nodecraft.nodesystem.io.SavedPosition;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        if (savedGraph == null) return null;

        String graphName = savedGraph.graphName != null ? savedGraph.graphName : "Loaded Graph";
        NodeGraph graph = new NodeGraph(graphName);
        NodeRegistry registry = NodeRegistry.getInstance();

        Map<String, BaseNode> loadedNodesMap = new HashMap<>();

        if (savedGraph.nodes != null) {
            for (SavedNode savedNode : savedGraph.nodes) {
                INode iNode = registry.createNodeInstance(savedNode.typeId);
                if (iNode instanceof BaseNode newNode) {
                    try {
                        newNode.setNodeState(savedNode.state);
                        if (savedGraph.nodePositions != null) {
                            SavedPosition savedPosition = savedGraph.nodePositions.get(savedNode.nodeId);
                            if (savedPosition != null) {
                                newNode.setPosition(savedPosition.x, savedPosition.y);
                            }
                        }
                        loadedNodesMap.put(savedNode.nodeId, newNode);
                        graph.addNode(newNode);
                    } catch (Exception e) {
                        LOGGER.error("Failed restoring node state: type={}, id={}", savedNode.typeId, savedNode.nodeId, e);
                    }
                } else {
                    LOGGER.warn("Cannot create node: type={}, id={}", savedNode.typeId, savedNode.nodeId);
                }
            }
        }

        if (savedGraph.connections != null) {
            for (SavedConnection conn : savedGraph.connections) {
                BaseNode sourceNode = loadedNodesMap.get(conn.sourceNodeId);
                BaseNode targetNode = loadedNodesMap.get(conn.targetNodeId);

                if (sourceNode != null && targetNode != null) {
                    boolean success = graph.connect(
                        sourceNode.getId(), conn.sourcePortId,
                        targetNode.getId(), conn.targetPortId
                    );
                    if (!success) {
                        LOGGER.warn("Failed rebuilding connection: {} -> {}", conn.sourcePortId, conn.targetPortId);
                    }
                }
            }
        }

        return graph;
    }

    public static NodeGraph fromJsonToGraph(String json) {
        SavedGraph savedGraph = fromJson(json);
        return fromSavedGraph(savedGraph);
    }

    /**
     * Compatibility hook kept for callers that still invoke migration preflight.
     * Performs best-effort type-id remapping for renamed/relocated nodes.
     */
    public static MigrationReport migrateCompatibilityNodes(SavedGraph savedGraph) {
        if (savedGraph == null || savedGraph.nodes == null || savedGraph.nodes.isEmpty()) {
            return MigrationReport.empty();
        }

        Map<String, String> typeIdRemap = legacyTypeIdRemap();
        if (typeIdRemap.isEmpty()) {
            return MigrationReport.empty();
        }

        int migrated = 0;
        List<String> migratedTypes = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (SavedNode savedNode : savedGraph.nodes) {
            if (savedNode == null || savedNode.typeId == null || savedNode.typeId.isBlank()) {
                continue;
            }
            String oldId = savedNode.typeId;
            String newId = typeIdRemap.get(oldId);
            if (newId == null || newId.equals(oldId)) {
                continue;
            }

            savedNode.typeId = newId;
            migrated++;
            if (!migratedTypes.contains(oldId + "->" + newId)) {
                migratedTypes.add(oldId + "->" + newId);
            }
        }

        if (migrated > 0) {
            notes.add("已自动映射旧节点类型 ID 到新版 ID，请保存以写回新格式。");
        }
        return migrated > 0
            ? new MigrationReport(migrated, migratedTypes, notes)
            : MigrationReport.empty();
    }

    private static Map<String, String> legacyTypeIdRemap() {
        Map<String, String> remap = new HashMap<>();
        // Legacy id kept in older graphs before input category normalization.
        remap.put("input.numeric.boolean_toggle", "input.basic.boolean_toggle");
        return Collections.unmodifiableMap(remap);
    }

    public record MigrationReport(int migratedNodeCount, List<String> migratedTypeIds, List<String> notes) {
        public static MigrationReport empty() {
            return new MigrationReport(0, List.of(), List.of());
        }

        public boolean hasChanges() {
            return migratedNodeCount > 0;
        }
    }

    public static NodeGraph loadFromFile(Path filePath) throws IOException {
        String json = Files.readString(filePath, StandardCharsets.UTF_8);
        return fromJsonToGraph(json);
    }
}
