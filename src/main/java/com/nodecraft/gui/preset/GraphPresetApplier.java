package com.nodecraft.gui.preset;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.layout.GraphNodeAutoLayout;
import com.nodecraft.nodesystem.api.INode;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GraphPresetApplier {

    private GraphPresetApplier() {
    }

    public record ApplyResult(boolean success, String message, List<UUID> createdNodeIds) {
        public static ApplyResult failure(String message) {
            return new ApplyResult(false, message, List.of());
        }

        public static ApplyResult success(String message, List<UUID> createdNodeIds) {
            return new ApplyResult(true, message, List.copyOf(createdNodeIds));
        }
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
        Map<String, LayoutPosition> layoutPositions = resolveLayoutPositions(preset);

        for (GraphPresetRules.PresetNode presetNode : preset.nodes) {
            if (presetNode == null || presetNode.ref == null || presetNode.typeId == null) {
                continue;
            }
            LayoutPosition position = layoutPositions.getOrDefault(
                    presetNode.ref,
                    new LayoutPosition(presetNode.x, presetNode.y));
            INode created = editor.addNode(
                    presetNode.typeId,
                    originX + position.x,
                    originY + position.y);
            if (created == null) {
                rollback(editor, createdNodeIds);
                return ApplyResult.failure("Failed to create node: " + presetNode.typeId);
            }
            if (presetNode.state != null && !presetNode.state.isEmpty()) {
                created.setNodeState(presetNode.state);
            }
            refToNodeId.put(presetNode.ref, created.getId());
            createdNodeIds.add(created.getId());
        }

        int declaredConnections = countDeclaredConnections(preset);
        int connectedCount = 0;
        if (preset.connections != null) {
            for (GraphPresetRules.PresetConnection connection : preset.connections) {
                if (connection == null) {
                    continue;
                }
                UUID sourceNodeId = refToNodeId.get(connection.fromRef);
                UUID targetNodeId = refToNodeId.get(connection.toRef);
                if (sourceNodeId == null || targetNodeId == null) {
                    String detail = String.format(
                            "Unknown node reference: %s -> %s",
                            connection.fromRef,
                            connection.toRef);
                    NodeCraft.LOGGER.error(
                            "Preset apply failed for {} — {}. Declared {} connections, created {} before failure.",
                            preset.id,
                            detail,
                            declaredConnections,
                            connectedCount);
                    rollback(editor, createdNodeIds);
                    return ApplyResult.failure(formatConnectionFailure(
                            preset, declaredConnections, connectedCount, detail));
                }

                boolean connected = editor.connectPorts(
                        sourceNodeId,
                        connection.fromPort,
                        targetNodeId,
                        connection.toPort);
                if (!connected) {
                    String detail = String.format(
                            "%s.%s -> %s.%s",
                            connection.fromRef,
                            connection.fromPort,
                            connection.toRef,
                            connection.toPort);
                    NodeCraft.LOGGER.error(
                            "Preset apply failed for {} — connection refused: {}. Declared {} connections, created {} before failure.",
                            preset.id,
                            detail,
                            declaredConnections,
                            connectedCount);
                    rollback(editor, createdNodeIds);
                    return ApplyResult.failure(formatConnectionFailure(
                            preset, declaredConnections, connectedCount, detail));
                }
                connectedCount++;
            }
        }

        editor.clearSelectedNodes();
        editor.getSelectedNodeIds().addAll(createdNodeIds);
        if (!createdNodeIds.isEmpty()) {
            editor.setSelectedNodeId(createdNodeIds.getFirst());
        }

        NodeCraft.LOGGER.info(
                "Applied graph preset {} ({} nodes, {} connections)",
                preset.displayName,
                createdNodeIds.size(),
                connectedCount);
        return ApplyResult.success("已添加预设: " + preset.displayName, createdNodeIds);
    }

    private static int countDeclaredConnections(GraphPresetRules.GraphPresetDefinition preset) {
        if (preset.connections == null) {
            return 0;
        }
        int count = 0;
        for (GraphPresetRules.PresetConnection connection : preset.connections) {
            if (connection != null) {
                count++;
            }
        }
        return count;
    }

    private static String formatConnectionFailure(
            GraphPresetRules.GraphPresetDefinition preset,
            int declaredConnections,
            int connectedCount,
            String failedConnection) {
        return String.format(
                "预设加载失败: %s — 连接未完整创建 (期望 %d, 实际 %d). FAILED: %s",
                preset.displayName != null ? preset.displayName : preset.id,
                declaredConnections,
                connectedCount,
                failedConnection);
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

    /**
     * Connection-aware layered layout so dropped presets do not stack on top of each other
     * and keep left-to-right flow with fewer edge crossings.
     */
    static Map<String, LayoutPosition> resolveLayoutPositions(GraphPresetRules.GraphPresetDefinition preset) {
        List<GraphNodeAutoLayout.NodeRef> refs = new ArrayList<>();
        for (GraphPresetRules.PresetNode presetNode : preset.nodes) {
            if (presetNode != null && presetNode.ref != null && !presetNode.ref.isBlank()) {
                refs.add(new GraphNodeAutoLayout.NodeRef(presetNode.ref));
            }
        }

        List<GraphNodeAutoLayout.Edge> edges = new ArrayList<>();
        if (preset.connections != null) {
            for (GraphPresetRules.PresetConnection connection : preset.connections) {
                if (connection != null
                        && connection.fromRef != null
                        && connection.toRef != null) {
                    edges.add(new GraphNodeAutoLayout.Edge(connection.fromRef, connection.toRef));
                }
            }
        }

        List<GraphNodeAutoLayout.Arranged> arranged = GraphNodeAutoLayout.autoLayout(refs, edges);
        return getStringLayoutPositionMap(preset, arranged);
    }

    private static @NonNull Map<String, LayoutPosition> getStringLayoutPositionMap(GraphPresetRules.GraphPresetDefinition preset, List<GraphNodeAutoLayout.Arranged> arranged) {
        Map<String, LayoutPosition> positionsByRef = new HashMap<>();
        for (GraphNodeAutoLayout.Arranged item : arranged) {
            positionsByRef.put(item.ref(), new LayoutPosition(item.offsetX(), item.offsetY()));
        }

        // Any node missing from the arranged set (should be rare) keeps authored coords.
        for (GraphPresetRules.PresetNode presetNode : preset.nodes) {
            if (presetNode == null || presetNode.ref == null) {
                continue;
            }
            positionsByRef.putIfAbsent(presetNode.ref, new LayoutPosition(presetNode.x, presetNode.y));
        }
        return positionsByRef;
    }

    record LayoutPosition(float x, float y) {
    }
}
