package com.nodecraft.gui.preset;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.nodesystem.api.INode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GraphPresetApplier {

    public record ApplyResult(boolean success, String message, List<UUID> createdNodeIds) {
        public static ApplyResult failure(String message) {
            return new ApplyResult(false, message, List.of());
        }

        public static ApplyResult success(String message, List<UUID> createdNodeIds) {
            return new ApplyResult(true, message, List.copyOf(createdNodeIds));
        }
    }

    private GraphPresetApplier() {
    }

    public static ApplyResult apply(GraphPresetRules.GraphPresetDefinition preset, float originX, float originY) {
        if (preset == null) {
            return ApplyResult.failure("Preset is missing");
        }
        if ("placeholder".equalsIgnoreCase(preset.kind)) {
            return ApplyResult.failure("该预设仍在筹备中");
        }
        if (!"composite".equalsIgnoreCase(preset.kind)) {
            return ApplyResult.failure("Unsupported preset kind: " + preset.kind);
        }
        if (preset.nodes == null || preset.nodes.isEmpty()) {
            return ApplyResult.failure("Preset has no nodes");
        }

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        if (editor == null || editor.getCurrentGraph() == null) {
            return ApplyResult.failure("Editor is not ready");
        }

        Map<String, UUID> refToNodeId = new HashMap<>();
        List<UUID> createdNodeIds = new ArrayList<>();

        for (GraphPresetRules.PresetNode presetNode : preset.nodes) {
            if (presetNode == null || presetNode.ref == null || presetNode.typeId == null) {
                continue;
            }
            INode created = editor.addNode(
                    presetNode.typeId,
                    originX + presetNode.x,
                    originY + presetNode.y);
            if (created == null) {
                rollback(editor, createdNodeIds);
                return ApplyResult.failure("Failed to create node: " + presetNode.typeId);
            }
            refToNodeId.put(presetNode.ref, created.getId());
            createdNodeIds.add(created.getId());
        }

        if (preset.connections != null) {
            for (GraphPresetRules.PresetConnection connection : preset.connections) {
                if (connection == null) {
                    continue;
                }
                UUID sourceNodeId = refToNodeId.get(connection.fromRef);
                UUID targetNodeId = refToNodeId.get(connection.toRef);
                if (sourceNodeId == null || targetNodeId == null) {
                    rollback(editor, createdNodeIds);
                    return ApplyResult.failure("Preset connection references unknown node");
                }
                boolean connected = editor.connectPorts(
                        sourceNodeId,
                        connection.fromPort,
                        targetNodeId,
                        connection.toPort);
                if (!connected) {
                    rollback(editor, createdNodeIds);
                    return ApplyResult.failure("Failed to connect preset ports");
                }
            }
        }

        editor.clearSelectedNodes();
        editor.getSelectedNodeIds().addAll(createdNodeIds);
        if (!createdNodeIds.isEmpty()) {
            editor.setSelectedNodeId(createdNodeIds.get(0));
        }

        NodeCraft.LOGGER.info("Applied graph preset {} ({} nodes)", preset.displayName, createdNodeIds.size());
        return ApplyResult.success("已添加预设: " + preset.displayName, createdNodeIds);
    }

    private static void rollback(ImGuiNodeEditor editor, List<UUID> createdNodeIds) {
        Set<UUID> ids = new HashSet<>(createdNodeIds);
        editor.getSelectedNodeIds().clear();
        editor.getSelectedNodeIds().addAll(ids);
        if (!ids.isEmpty()) {
            editor.setSelectedNodeId(ids.iterator().next());
            editor.deleteSelectedNodes();
        }
    }
}
