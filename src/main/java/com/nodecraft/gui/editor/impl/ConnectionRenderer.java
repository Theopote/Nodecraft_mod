package com.nodecraft.gui.editor.impl;

import java.util.Map;
import java.util.UUID;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.execution.ExecutionPortKind;
import com.nodecraft.nodesystem.graph.NodeGraph;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;

/**
 * 连接线渲染器
 * 专门处理节点之间连接线的渲染，包括连接预览
 */
public class ConnectionRenderer {
    
    private final ICanvasEditor editor;
    
    public ConnectionRenderer(ICanvasEditor editor) {
        this.editor = editor;
    }

    /**
     * 渲染所有连接线
     */
    public void renderConnectionsDirect(ImDrawList drawList, NodeGraph graph,
                                        Map<UUID, Map<String, ImVec2>> portScreenPositions) {
        renderConnectionsDirect(drawList, graph, portScreenPositions, null);
    }

    /**
     * 分层渲染连接线
     * @param selectedNodeIds 选中的节点ID集合，用于决定连接线的分层
     */
    public void renderConnectionsDirect(ImDrawList drawList, NodeGraph graph,
                                        Map<UUID, Map<String, ImVec2>> portScreenPositions,
                                        java.util.Set<UUID> selectedNodeIds) {
        if (graph == null || portScreenPositions.isEmpty()) return;

        float canvasZoom = editor.getCanvasZoom();

        ImGuiNodeInteraction interaction = editor.getInteraction();
        boolean isHoveringConnection = interaction != null && interaction.isHoveringConnection();
        UUID hoveredSourceNodeId = isHoveringConnection ? interaction.getHoveredConnectionSourceNodeId() : null;
        String hoveredSourcePortId = isHoveringConnection ? interaction.getHoveredConnectionSourcePortId() : null;
        UUID hoveredTargetNodeId = isHoveringConnection ? interaction.getHoveredConnectionTargetNodeId() : null;
        String hoveredTargetPortId = isHoveringConnection ? interaction.getHoveredConnectionTargetPortId() : null;

        int hoveredLineColor = ImGui.getColorU32(1.0f, 1.0f, 0.4f, 1.0f);
        int normalLineColor = ImGui.getColorU32(ImGuiCol.PlotLines);
        int typeMismatchLineColor = ImGui.getColorU32(0.95f, 0.2f, 0.2f, 1.0f);
        float hoveredLineThickness = 3.5f * canvasZoom;
        float normalLineThickness = 2.5f * canvasZoom;

        float highlightPortRadius = NodeRenderConstants.PORT_RADIUS_UNSCALED * 1.5f * canvasZoom;

        if (selectedNodeIds == null || selectedNodeIds.isEmpty()) {
            renderAllConnections(drawList, graph, portScreenPositions, canvasZoom,
                    isHoveringConnection, hoveredSourceNodeId, hoveredSourcePortId,
                    hoveredTargetNodeId, hoveredTargetPortId, hoveredLineColor,
                    normalLineColor, typeMismatchLineColor, hoveredLineThickness, normalLineThickness,
                    highlightPortRadius);
            return;
        }

        java.util.List<NodeGraph.Connection> normalConnections = new java.util.ArrayList<>();

        for (NodeGraph.Connection connection : graph.getConnections()) {
            UUID sourceNodeId = connection.sourceNode.getId();
            UUID targetNodeId = connection.targetNode.getId();

            boolean isHighlightedConnection = selectedNodeIds.contains(sourceNodeId) ||
                    selectedNodeIds.contains(targetNodeId);

            if (!isHighlightedConnection) {
                normalConnections.add(connection);
            }
        }

        renderConnectionList(drawList, normalConnections, portScreenPositions, canvasZoom,
                isHoveringConnection, hoveredSourceNodeId, hoveredSourcePortId,
                hoveredTargetNodeId, hoveredTargetPortId, hoveredLineColor,
                normalLineColor, typeMismatchLineColor, hoveredLineThickness, normalLineThickness,
                highlightPortRadius);
    }

    /**
     * 渲染前景连接线（与选中节点相关的连接线）
     * 应该在渲染节点之后调用，确保连接线显示在节点上方
     */
    public void renderForegroundConnections(ImDrawList drawList, NodeGraph graph,
                                            Map<UUID, Map<String, ImVec2>> portScreenPositions,
                                            java.util.Set<UUID> selectedNodeIds) {
        if (graph == null || portScreenPositions.isEmpty()) {
            return;
        }

        float canvasZoom = editor.getCanvasZoom();

        ImGuiNodeInteraction interaction = editor.getInteraction();
        boolean isHoveringConnection = interaction != null && interaction.isHoveringConnection();
        UUID hoveredSourceNodeId = isHoveringConnection ? interaction.getHoveredConnectionSourceNodeId() : null;
        String hoveredSourcePortId = isHoveringConnection ? interaction.getHoveredConnectionSourcePortId() : null;
        UUID hoveredTargetNodeId = isHoveringConnection ? interaction.getHoveredConnectionTargetNodeId() : null;
        String hoveredTargetPortId = isHoveringConnection ? interaction.getHoveredConnectionTargetPortId() : null;

        int hoveredLineColor = ImGui.getColorU32(1.0f, 1.0f, 0.4f, 1.0f);
        int normalLineColor = ImGui.getColorU32(ImGuiCol.PlotLines);
        int typeMismatchLineColor = ImGui.getColorU32(0.95f, 0.2f, 0.2f, 1.0f);
        int highlightedLineColor = NodeDrawingUtils.adjustBrightnessFast(normalLineColor, 1.3f);
        float hoveredLineThickness = 3.5f * canvasZoom;
        float normalLineThickness = 2.5f * canvasZoom;
        float highlightPortRadius = NodeRenderConstants.PORT_RADIUS_UNSCALED * 1.5f * canvasZoom;

        if (selectedNodeIds != null && !selectedNodeIds.isEmpty()) {
            java.util.List<NodeGraph.Connection> highlightedConnections = new java.util.ArrayList<>();
            for (NodeGraph.Connection connection : graph.getConnections()) {
                UUID sourceNodeId = connection.sourceNode.getId();
                UUID targetNodeId = connection.targetNode.getId();

                if (selectedNodeIds.contains(sourceNodeId) || selectedNodeIds.contains(targetNodeId)) {
                    highlightedConnections.add(connection);
                }
            }

            renderConnectionList(drawList, highlightedConnections, portScreenPositions, canvasZoom,
                    isHoveringConnection, hoveredSourceNodeId, hoveredSourcePortId,
                    hoveredTargetNodeId, hoveredTargetPortId, hoveredLineColor,
                    highlightedLineColor, typeMismatchLineColor, hoveredLineThickness, normalLineThickness,
                    highlightPortRadius);
        }
    }

    /**
     * 绘制连接预览线。
     * @param typeMismatch 若为 true 则使用红色表示当前悬停目标端口类型不匹配
     */
    public void drawConnectionPreview(ImDrawList drawList, ImVec2 startPos, float canvasZoom, boolean isFromOutput, boolean typeMismatch) {
        float startX = startPos.x;
        float startY = startPos.y;

        float endX = ImGui.getMousePosX();
        float endY = ImGui.getMousePosY();

        float controlPointDistanceUnscaled = 50.0f;
        float controlPointDistanceScaled = controlPointDistanceUnscaled * canvasZoom;

        float ctrl1X, ctrl1Y, ctrl2X, ctrl2Y;
        if (isFromOutput) {
            ctrl1X = startX + controlPointDistanceScaled;
            ctrl1Y = startY;
            ctrl2X = endX - controlPointDistanceScaled;
        } else {
            ctrl1X = startX - controlPointDistanceScaled;
            ctrl1Y = startY;
            ctrl2X = endX + controlPointDistanceScaled;
        }
        ctrl2Y = endY;

        int lineColor = typeMismatch ? ImGui.getColorU32(0.95f, 0.2f, 0.2f, 1.0f) : ImGui.getColorU32(ImGuiCol.PlotLines);
        float lineThickness = 2.0f * canvasZoom;

        drawList.addBezierCubic(startX, startY, ctrl1X, ctrl1Y, ctrl2X, ctrl2Y, endX, endY, lineColor, lineThickness);
    }

    private void renderAllConnections(ImDrawList drawList, NodeGraph graph,
                                      Map<UUID, Map<String, ImVec2>> portScreenPositions, float canvasZoom,
                                      boolean isHoveringConnection, UUID hoveredSourceNodeId, String hoveredSourcePortId,
                                      UUID hoveredTargetNodeId, String hoveredTargetPortId, int hoveredLineColor,
                                      int normalLineColor, int typeMismatchLineColor, float hoveredLineThickness, float normalLineThickness,
                                      float highlightPortRadius) {

        renderConnectionList(drawList, graph.getConnections(), portScreenPositions, canvasZoom,
                isHoveringConnection, hoveredSourceNodeId, hoveredSourcePortId,
                hoveredTargetNodeId, hoveredTargetPortId, hoveredLineColor,
                normalLineColor, typeMismatchLineColor, hoveredLineThickness, normalLineThickness,
                highlightPortRadius);
    }

    private void renderConnectionList(ImDrawList drawList, java.util.List<NodeGraph.Connection> connections,
                                      Map<UUID, Map<String, ImVec2>> portScreenPositions, float canvasZoom,
                                      boolean isHoveringConnection, UUID hoveredSourceNodeId, String hoveredSourcePortId,
                                      UUID hoveredTargetNodeId, String hoveredTargetPortId, int hoveredLineColor,
                                      int lineColor, int typeMismatchLineColor, float hoveredLineThickness, float normalLineThickness,
                                      float highlightPortRadius) {

        for (NodeGraph.Connection connection : connections) {
            ImVec2 sourcePortPos = getPortScreenPosition(connection.sourceNode.getId(), connection.sourcePort.getId(), portScreenPositions);
            ImVec2 targetPortPos = getPortScreenPosition(connection.targetNode.getId(), connection.targetPort.getId(), portScreenPositions);

            if (sourcePortPos != null && targetPortPos != null) {
                float startX = sourcePortPos.x;
                float startY = sourcePortPos.y;
                float endX = targetPortPos.x;
                float endY = targetPortPos.y;

                float scaledControlOffset = getScaledControlOffset(endX, startX, canvasZoom);

                float ctrl1X = startX + scaledControlOffset;
                float ctrl2X = endX - scaledControlOffset;

                boolean isCurrentConnectionHovered = isHoveringConnection &&
                        connection.sourceNode.getId().equals(hoveredSourceNodeId) &&
                        connection.sourcePort.getId().equals(hoveredSourcePortId) &&
                        connection.targetNode.getId().equals(hoveredTargetNodeId) &&
                        connection.targetPort.getId().equals(hoveredTargetPortId);

                boolean typeMismatch = !NodeDataType.isConnectableTo(connection.sourcePort.getDataType(), connection.targetPort.getDataType());
                boolean isExecConnection = ExecutionPortKind.isExecConnection(connection);
                int normalColor;
                if (typeMismatch) {
                    normalColor = typeMismatchLineColor;
                } else if (isExecConnection) {
                    normalColor = NodeRenderConstants.CONNECTION_COLOR_EXEC;
                } else {
                    normalColor = lineColor;
                }
                int currentLineColor = isCurrentConnectionHovered
                        ? (isExecConnection ? NodeRenderConstants.CONNECTION_COLOR_EXEC_HIGHLIGHT : hoveredLineColor)
                        : normalColor;
                float currentThickness = isCurrentConnectionHovered
                        ? hoveredLineThickness
                        : (isExecConnection ? normalLineThickness + (0.75f * canvasZoom) : normalLineThickness);

                drawList.addBezierCubic(
                        startX, startY,
                        ctrl1X, startY,
                        ctrl2X, endY,
                        endX, endY,
                        currentLineColor,
                        currentThickness
                );

                if (isCurrentConnectionHovered) {
                    int sourceHighlight = isExecConnection
                            ? NodeRenderConstants.PORT_COLOR_EXEC_HIGHLIGHT
                            : NodeRenderConstants.PORT_COLOR_OUTPUT_HIGHLIGHT;
                    int targetHighlight = isExecConnection
                            ? NodeRenderConstants.PORT_COLOR_EXEC_HIGHLIGHT
                            : NodeRenderConstants.PORT_COLOR_INPUT_HIGHLIGHT;
                    drawList.addCircleFilled(startX, startY, highlightPortRadius, sourceHighlight);
                    drawList.addCircleFilled(endX, endY, highlightPortRadius, targetHighlight);
                }
            }
        }
    }

    private ImVec2 getPortScreenPosition(UUID nodeId, String portId, Map<UUID, Map<String, ImVec2>> portScreenPositions) {
        Map<String, ImVec2> ports = portScreenPositions.get(nodeId);
        return (ports != null) ? ports.get(portId) : null;
    }

    private static float getScaledControlOffset(float endX, float startX, float canvasZoom) {
        float minControlOffsetUnscaled = 30.0f;
        float maxControlOffsetUnscaled = 150.0f;

        float distanceBetweenNodesScaled = Math.abs(endX - startX);

        float initialControlOffsetScaled = distanceBetweenNodesScaled * 0.4f;

        float finalControlOffsetScaled = Math.max(initialControlOffsetScaled, minControlOffsetUnscaled * canvasZoom);
        finalControlOffsetScaled = Math.min(finalControlOffsetScaled, maxControlOffsetUnscaled * canvasZoom);

        return finalControlOffsetScaled;
    }
} 