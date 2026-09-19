package com.nodecraft.gui.editor.command;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.document.EditorDocumentState;
import com.nodecraft.gui.editor.impl.ICanvasEditor.NodeAlignmentAction;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.gui.editor.interaction.EditorInteractionState;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Owns add / delete / duplicate / align node commands.
 * <p>
 * See {@code docs/architecture/imgui-node-editor-breakup.md} (Phase M).
 */
public final class NodeCommandService {

    public interface Host {
        EditorDocumentState document();

        EditorInteractionState interaction();

        @Nullable
        ImGuiNodeHistory history();

        void notifyStructureDirty();

        void notifyNodeAdded(Map<String, Object> eventData);

        void clearSelectedNodes();

        void setSelectedNodeId(UUID nodeId);

        void removeNodePosition(UUID nodeId);

        void removeSelectedNode(UUID nodeId);
    }

    private final Host host;

    public NodeCommandService(Host host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public @Nullable INode addNode(String nodeTypeId, float x, float y) {
        return addNodeWithState(nodeTypeId, null, x, y, null);
    }

    public @Nullable INode addNodeWithState(
            String nodeTypeId,
            @Nullable UUID oldNodeId,
            float x,
            float y,
            @Nullable Object nodeState
    ) {
        EditorDocumentState document = host.document();
        if (document.getGraph() == null) {
            NodeCraft.LOGGER.error("无法添加节点: 当前没有节点图");
            return null;
        }

        try {
            NodeRegistry registry = NodeRegistry.getInstance();
            INode node = registry.createNodeInstance(nodeTypeId);

            if (node == null) {
                NodeCraft.LOGGER.error("无法创建节点: 未找到类型 {}", nodeTypeId);
                return null;
            }

            if (nodeState != null) {
                try {
                    node.setNodeState(nodeState);
                } catch (Exception e) {
                    NodeCraft.LOGGER.warn("添加节点 {} 时恢复状态失败: {}", nodeTypeId, e.getMessage());
                }
            }

            document.getGraph().addNode(node);
            document.getNodePositions().put(node.getId(), new NodePosition(x, y));

            host.notifyStructureDirty();

            ImGuiNodeHistory history = host.history();
            if (history != null && history.isRecording()) {
                history.recordAddNode(node, x, y);
            }

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("nodeId", node.getId());
            eventData.put("nodeType", nodeTypeId);
            eventData.put("x", x);
            eventData.put("y", y);
            host.notifyNodeAdded(eventData);

            return node;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("添加节点时出错: {}", e.getMessage(), e);
            return null;
        }
    }

    public boolean deleteSelected() {
        try {
            EditorDocumentState document = host.document();
            NodeGraph graph = document.getGraph();
            if (graph == null) {
                return false;
            }

            Set<UUID> selectedNodeIds = host.interaction().getSelectedNodeIds();
            if (selectedNodeIds.isEmpty()) {
                return false;
            }

            ImGuiNodeHistory history = host.history();
            List<ImGuiNodeHistory.RemovedNodeSnapshot> snapshots = new ArrayList<>();
            if (history != null && history.isRecording()) {
                for (UUID nodeId : new ArrayList<>(selectedNodeIds)) {
                    INode node = graph.getNode(nodeId);
                    if (node == null) {
                        continue;
                    }
                    NodePosition pos = document.getNodePosition(nodeId);
                    if (pos == null) {
                        pos = new NodePosition(0, 0);
                    }
                    ImGuiNodeHistory.RemovedNodeSnapshot snapshot =
                            history.captureRemovedNodeSnapshot(node, pos.x, pos.y);
                    if (snapshot != null) {
                        snapshots.add(snapshot);
                    }
                }
                if (!snapshots.isEmpty()) {
                    history.recordRemoveNodes(snapshots);
                }
            }

            for (UUID nodeId : new ArrayList<>(selectedNodeIds)) {
                INode node = graph.getNode(nodeId);
                if (node != null) {
                    graph.removeNode(nodeId);
                    host.removeNodePosition(nodeId);
                    host.removeSelectedNode(nodeId);
                }
            }

            NodeCraft.LOGGER.info("已删除选中的节点");
            host.notifyStructureDirty();
            return true;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("删除节点时出错: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean duplicateSelected() {
        EditorDocumentState document = host.document();
        EditorInteractionState interaction = host.interaction();
        if (document.getGraph() == null) {
            NodeCraft.LOGGER.warn("无法复制节点：当前没有节点图");
            return false;
        }
        if (interaction.getSelectedNodeIds().isEmpty()) {
            NodeCraft.LOGGER.warn("没有选中的节点可复制");
            return false;
        }
        UUID nodeId = interaction.getSelectedNodeIds().iterator().next();
        INode sourceNode = document.getGraph().getNode(nodeId);
        if (sourceNode == null) {
            NodeCraft.LOGGER.error("复制失败：找不到选中的节点 {}", nodeId);
            return false;
        }
        NodeCraft.LOGGER.info(
                "开始复制节点: {} (ID: {}, 类型: {})",
                sourceNode.getDisplayName(),
                nodeId,
                sourceNode.getTypeId()
        );
        NodePosition sourcePos = document.getNodePosition(nodeId);
        if (sourcePos == null) {
            NodeCraft.LOGGER.error("复制失败：节点 {} 没有位置信息", nodeId);
            return false;
        }
        NodeCraft.LOGGER.info("源节点位置: ({}, {})", sourcePos.x, sourcePos.y);
        float offsetX = 30;
        float offsetY = 0;
        INode newNode;
        try {
            newNode = addNodeWithState(
                    sourceNode.getTypeId(),
                    null,
                    sourcePos.x + offsetX,
                    sourcePos.y + offsetY,
                    sourceNode.getNodeState()
            );
        } catch (Exception e) {
            NodeCraft.LOGGER.error("复制节点失败: {}", e.getMessage());
            newNode = null;
        }

        if (newNode != null) {
            host.clearSelectedNodes();
            host.setSelectedNodeId(newNode.getId());
            NodeCraft.LOGGER.info(
                    "节点复制成功: {} -> {} (新ID: {})",
                    sourceNode.getDisplayName(),
                    newNode.getDisplayName(),
                    newNode.getId()
            );
            host.notifyStructureDirty();
            return true;
        }
        NodeCraft.LOGGER.error("复制节点失败: 无法创建新节点");
        return false;
    }

    public boolean align(Set<UUID> nodeIds, NodeAlignmentAction action) {
        if (nodeIds == null || nodeIds.size() < 2 || action == null) {
            return false;
        }

        EditorDocumentState document = host.document();
        List<NodePosition> positions = new ArrayList<>();
        for (UUID nodeId : nodeIds) {
            NodePosition position = document.getNodePositions().get(nodeId);
            if (position != null) {
                positions.add(position);
            }
        }
        if (positions.size() < 2) {
            return false;
        }

        boolean changed = switch (action) {
            case ALIGN_LEFT -> alignLeft(positions);
            case ALIGN_CENTER -> alignCenter(positions);
            case DISTRIBUTE_HORIZONTAL -> distributeHorizontal(positions);
        };

        if (changed) {
            host.notifyStructureDirty();
            NodeCraft.LOGGER.info("Applied node alignment {} to {} nodes", action, positions.size());
        }
        return changed;
    }

    static boolean alignLeft(List<NodePosition> positions) {
        float left = Float.MAX_VALUE;
        for (NodePosition position : positions) {
            left = Math.min(left, position.x);
        }

        boolean changed = false;
        for (NodePosition position : positions) {
            if (Float.compare(position.x, left) != 0) {
                position.x = left;
                changed = true;
            }
        }
        return changed;
    }

    static boolean alignCenter(List<NodePosition> positions) {
        float minCenter = Float.MAX_VALUE;
        float maxCenter = -Float.MAX_VALUE;
        for (NodePosition position : positions) {
            float center = position.x + safeWidth(position) / 2.0f;
            minCenter = Math.min(minCenter, center);
            maxCenter = Math.max(maxCenter, center);
        }
        float targetCenter = (minCenter + maxCenter) / 2.0f;

        boolean changed = false;
        for (NodePosition position : positions) {
            float nextX = targetCenter - safeWidth(position) / 2.0f;
            if (Float.compare(position.x, nextX) != 0) {
                position.x = nextX;
                changed = true;
            }
        }
        return changed;
    }

    static boolean distributeHorizontal(List<NodePosition> positions) {
        if (positions.size() < 3) {
            return false;
        }
        positions.sort(Comparator.comparingDouble(position -> position.x + safeWidth(position) / 2.0f));

        NodePosition first = positions.getFirst();
        NodePosition last = positions.getLast();
        float firstCenter = first.x + safeWidth(first) / 2.0f;
        float lastCenter = last.x + safeWidth(last) / 2.0f;
        float step = (lastCenter - firstCenter) / (positions.size() - 1);

        boolean changed = false;
        for (int i = 1; i < positions.size() - 1; i++) {
            NodePosition position = positions.get(i);
            float targetCenter = firstCenter + step * i;
            float nextX = targetCenter - safeWidth(position) / 2.0f;
            if (Float.compare(position.x, nextX) != 0) {
                position.x = nextX;
                changed = true;
            }
        }
        return changed;
    }

    private static float safeWidth(NodePosition position) {
        return position.width > 0.0f ? position.width : 150.0f;
    }
}
