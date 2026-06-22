package com.nodecraft.gui.preset;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.ICanvasEditor;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.graph.NodeGraph.Connection;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GraphPresetExporter {

    private GraphPresetExporter() {
    }

    public static GraphPresetRules.GraphPresetDefinition exportSelection(
            ICanvasEditor editor,
            Set<UUID> selectedNodeIds,
            String displayName,
            String description) {
        if (editor == null || selectedNodeIds == null || selectedNodeIds.isEmpty()) {
            return null;
        }

        NodeGraph graph = editor.getCurrentGraph();
        NodeRegistry registry = NodeRegistry.getInstance();
        if (graph == null || registry == null) {
            return null;
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        List<UUID> orderedIds = new ArrayList<>();
        Map<UUID, NodePosition> positions = new HashMap<>();

        for (UUID nodeId : selectedNodeIds) {
            INode node = graph.getNode(nodeId);
            NodePosition pos = editor.getNodePosition(nodeId);
            if (node == null || pos == null) {
                continue;
            }
            orderedIds.add(nodeId);
            positions.put(nodeId, pos);
            minX = Math.min(minX, pos.x);
            minY = Math.min(minY, pos.y);
        }

        if (orderedIds.isEmpty()) {
            return null;
        }

        Map<UUID, String> refByNodeId = new HashMap<>();
        List<GraphPresetRules.PresetNode> presetNodes = new ArrayList<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            UUID nodeId = orderedIds.get(i);
            INode node = graph.getNode(nodeId);
            NodePosition pos = positions.get(nodeId);
            if (node == null || pos == null) {
                continue;
            }

            String ref = "n" + i;
            refByNodeId.put(nodeId, ref);

            GraphPresetRules.PresetNode presetNode = new GraphPresetRules.PresetNode();
            presetNode.ref = ref;
            presetNode.typeId = registry.resolveCanonicalNodeId(node.getTypeId());
            presetNode.x = pos.x - minX;
            presetNode.y = pos.y - minY;
            presetNodes.add(presetNode);
        }

        List<GraphPresetRules.PresetConnection> presetConnections = new ArrayList<>();
        for (Connection conn : graph.getConnections()) {
            UUID sourceId = conn.sourceNode.getId();
            UUID targetId = conn.targetNode.getId();
            if (!selectedNodeIds.contains(sourceId) || !selectedNodeIds.contains(targetId)) {
                continue;
            }
            String fromRef = refByNodeId.get(sourceId);
            String toRef = refByNodeId.get(targetId);
            if (fromRef == null || toRef == null) {
                continue;
            }

            GraphPresetRules.PresetConnection presetConnection = new GraphPresetRules.PresetConnection();
            presetConnection.fromRef = fromRef;
            presetConnection.fromPort = conn.sourcePort.getId();
            presetConnection.toRef = toRef;
            presetConnection.toPort = conn.targetPort.getId();
            presetConnections.add(presetConnection);
        }

        GraphPresetRules.GraphPresetDefinition preset = new GraphPresetRules.GraphPresetDefinition();
        preset.displayName = displayName;
        preset.description = description != null ? description : "";
        preset.kind = "composite";
        preset.nodes = presetNodes;
        preset.connections = presetConnections;

        NodeCraft.LOGGER.info(
                "Exported graph preset \"{}\" with {} nodes and {} connections",
                displayName,
                presetNodes.size(),
                presetConnections.size());
        return preset;
    }
}
