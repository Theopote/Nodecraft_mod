package com.nodecraft.gui.editor.connection;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.document.EditorDocumentState;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns connect / disconnect / dangling cleanup / reroute insert / connection-preview validation.
 * <p>
 * See {@code docs/architecture/imgui-node-editor-breakup.md} (Phase L).
 */
public final class ConnectionEditService {

    public interface Host {
        EditorDocumentState document();

        @Nullable
        ImGuiNodeHistory history();

        void notifyStructureDirty();

        void notifyConnectionAdded(Map<String, Object> eventData);

        @Nullable
        INode addNode(String nodeTypeId, float x, float y);

        void removeNodePosition(UUID nodeId);

        void clearSelectedNodes();

        void setSelectedNodeId(UUID nodeId);
    }

    /**
     * Snapshot of an in-progress wire drag used for preview validation (no ImGui dependency).
     */
    public record DragPreview(
            UUID sourceNodeId,
            String sourcePortId,
            boolean fromOutput,
            @Nullable UUID hoveredNodeId,
            @Nullable String hoveredPortId,
            boolean hoveredIsOutput
    ) {
    }

    private final Host host;

    public ConnectionEditService(Host host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public boolean connect(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        EditorDocumentState document = host.document();
        if (document.getGraph() == null) {
            NodeCraft.LOGGER.error("无法连接端口: 当前没有节点图");
            return false;
        }

        try {
            INode sourceNode = document.getGraph().getNode(sourceNodeId);
            INode targetNode = document.getGraph().getNode(targetNodeId);

            if (sourceNode == null || targetNode == null) {
                NodeCraft.LOGGER.error("无法连接端口: 未找在节点");
                return false;
            }

            boolean success = document.getGraph().connect(sourceNodeId, sourcePortId, targetNodeId, targetPortId);

            if (success) {
                host.notifyStructureDirty();
                ImGuiNodeHistory history = host.history();
                if (history != null && history.isRecording()) {
                    history.recordAddConnection(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
                }
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("sourceNodeId", sourceNodeId);
                eventData.put("sourcePortId", sourcePortId);
                eventData.put("targetNodeId", targetNodeId);
                eventData.put("targetPortId", targetPortId);
                host.notifyConnectionAdded(eventData);
            }

            return success;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("连接端口时出错: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean disconnect(UUID sourceNodeId, String sourcePortId, UUID targetNodeId, String targetPortId) {
        EditorDocumentState document = host.document();
        if (document.getGraph() == null) {
            return false;
        }
        try {
            INode sourceNode = document.getGraph().getNode(sourceNodeId);
            INode targetNode = document.getGraph().getNode(targetNodeId);
            if (sourceNode == null || targetNode == null) {
                NodeCraft.LOGGER.warn("断开连接失败：未找到源节点或目标节点");
                return false;
            }
            if (document.getGraph().isConnected(sourceNodeId, sourcePortId, targetNodeId, targetPortId)) {
                ImGuiNodeHistory history = host.history();
                if (history != null && history.isRecording()) {
                    history.recordRemoveConnection(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
                }
                document.getGraph().disconnectPorts(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
                host.notifyStructureDirty();
                NodeCraft.LOGGER.info(
                        "成功断开连接: {}({}) -> {}({})",
                        sourceNode.getDisplayName(),
                        sourcePortId,
                        targetNode.getDisplayName(),
                        targetPortId
                );
                return true;
            }
            NodeCraft.LOGGER.warn("断开连接失败：端口未连接");
            return false;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("断开端口连接时出错: {}", e.getMessage(), e);
            return false;
        }
    }

    public void cleanupDangling() {
        EditorDocumentState document = host.document();
        if (document.getGraph() == null) {
            return;
        }

        int removedCount = 0;
        for (NodeGraph.Connection connection : document.getGraph().getConnections()) {
            INode sourceNode = connection.sourceNode();
            INode targetNode = connection.targetNode();
            if (sourceNode == null || targetNode == null) {
                document.getGraph().removeConnection(connection);
                removedCount++;
                continue;
            }

            if (!hasPort(sourceNode.getOutputPorts(), connection.sourcePort().getId())
                    || !hasPort(targetNode.getInputPorts(), connection.targetPort().getId())) {
                document.getGraph().removeConnection(connection);
                removedCount++;
            }
        }

        if (removedCount > 0) {
            NodeCraft.LOGGER.info("已清理 {} 条悬挂连线（端口已被动态移除）", removedCount);
        }
    }

    /**
     * Inserts a reroute node at world coordinates on an existing connection.
     */
    public void insertReroute(
            float worldX,
            float worldY,
            UUID sourceNodeId,
            String sourcePortId,
            UUID targetNodeId,
            String targetPortId
    ) {
        EditorDocumentState document = host.document();
        if (sourceNodeId == null || sourcePortId == null || targetNodeId == null || targetPortId == null) {
            return;
        }
        if (document.getGraph() == null
                || !document.getGraph().isConnected(sourceNodeId, sourcePortId, targetNodeId, targetPortId)) {
            return;
        }

        INode rerouteNode = host.addNode("utilities.assist.reroute", worldX, worldY);
        if (rerouteNode == null) {
            NodeCraft.LOGGER.warn("双击连接线插入中继失败：无法创建中继节点");
            return;
        }

        UUID rerouteNodeId = rerouteNode.getId();
        boolean oldDisconnected = disconnect(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
        if (!oldDisconnected) {
            document.getGraph().removeNode(rerouteNodeId);
            host.removeNodePosition(rerouteNodeId);
            NodeCraft.LOGGER.warn("双击连接线插入中继失败：无法断开原连接");
            return;
        }

        boolean firstConnected = connect(sourceNodeId, sourcePortId, rerouteNodeId, "input_signal");
        boolean secondConnected = connect(rerouteNodeId, "output_signal", targetNodeId, targetPortId);

        if (!firstConnected || !secondConnected) {
            disconnect(sourceNodeId, sourcePortId, rerouteNodeId, "input_signal");
            disconnect(rerouteNodeId, "output_signal", targetNodeId, targetPortId);
            document.getGraph().removeNode(rerouteNodeId);
            host.removeNodePosition(rerouteNodeId);
            connect(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
            NodeCraft.LOGGER.warn("双击连接线插入中继失败：连接重建失败，已回滚");
            return;
        }

        host.clearSelectedNodes();
        host.setSelectedNodeId(rerouteNodeId);
        host.notifyStructureDirty();

        NodeCraft.LOGGER.info(
                "已在连接线上插入中继节点: {}({}) -> {} -> {}({})",
                sourceNodeId,
                sourcePortId,
                rerouteNodeId,
                targetNodeId,
                targetPortId
        );
    }

    public boolean isPreviewTypeMismatch(@Nullable NodeGraph graph, @Nullable DragPreview preview) {
        return previewInvalidReason(graph, preview) != null;
    }

    @Nullable
    public String previewInvalidReason(@Nullable NodeGraph graph, @Nullable DragPreview preview) {
        if (graph == null || preview == null) {
            return null;
        }
        UUID sourceNodeId = preview.sourceNodeId();
        String sourcePortId = preview.sourcePortId();
        UUID hoveredNodeId = preview.hoveredNodeId();
        String hoveredPortId = preview.hoveredPortId();
        boolean isFromOutput = preview.fromOutput();
        boolean hoveredIsOutput = preview.hoveredIsOutput();
        if (sourceNodeId == null || sourcePortId == null || hoveredNodeId == null || hoveredPortId == null) {
            return null;
        }
        INode sourceNode = graph.getNode(sourceNodeId);
        INode hoveredNode = graph.getNode(hoveredNodeId);
        if (sourceNode == null || hoveredNode == null) {
            return null;
        }
        IPort sourcePort = null;
        IPort targetPort = null;
        if (isFromOutput && !hoveredIsOutput) {
            sourcePort = findPort(sourceNode, sourcePortId, true);
            targetPort = findPort(hoveredNode, hoveredPortId, false);
        } else if (!isFromOutput && hoveredIsOutput) {
            targetPort = findPort(sourceNode, sourcePortId, false);
            sourcePort = findPort(hoveredNode, hoveredPortId, true);
        } else {
            return isFromOutput ? "只能连接到输入端" : "只能连接到输出端";
        }
        if (sourcePort == null || targetPort == null) {
            return "目标端口不可连接";
        }
        if (sourcePort.getNode().getId().equals(targetPort.getNode().getId())) {
            return "不能连接到同一节点";
        }
        if (!NodeDataType.isConnectableTo(sourcePort.getDataType(), targetPort.getDataType())) {
            return String.format(
                    "类型不匹配: 输出 %s 无法连接到输入 %s",
                    sourcePort.getDataType().getDisplayName(),
                    targetPort.getDataType().getDisplayName()
            );
        }
        UUID connectedNodeId = graph.getConnectedOutputNodeId(targetPort.getNode().getId(), targetPort.getId());
        String connectedPortId = graph.getConnectedOutputPortId(targetPort.getNode().getId(), targetPort.getId());
        boolean isSameExistingConnection = connectedNodeId != null
                && connectedNodeId.equals(sourcePort.getNode().getId())
                && connectedPortId != null
                && connectedPortId.equals(sourcePort.getId());
        if (connectedNodeId != null && !isSameExistingConnection && !targetPort.allowsMultipleIncomingConnections()) {
            return "该输入端只允许一个输入连接";
        }
        if (!graph.canConnect(
                sourcePort.getNode().getId(),
                sourcePort.getId(),
                targetPort.getNode().getId(),
                targetPort.getId())) {
            return "该连接无效";
        }
        return null;
    }

    public static @Nullable IPort findPort(INode node, String portId, boolean isOutputPort) {
        if (node == null || portId == null) {
            return null;
        }
        List<IPort> ports = isOutputPort ? node.getOutputPorts() : node.getInputPorts();
        if (ports == null) {
            return null;
        }
        for (IPort port : ports) {
            if (port != null && portId.equals(port.getId())) {
                return port;
            }
        }
        return null;
    }

    private static boolean hasPort(List<IPort> ports, String portId) {
        if (ports == null || portId == null) {
            return false;
        }
        for (IPort port : ports) {
            if (port != null && portId.equals(port.getId())) {
                return true;
            }
        }
        return false;
    }
}
