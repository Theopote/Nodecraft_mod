package com.nodecraft.gui.editor.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.integration.ImGuiInputAdapter;
import com.nodecraft.gui.editor.session.EditorSession;
import com.nodecraft.gui.editor.session.NodeDisplayMode;
import com.nodecraft.gui.node.NodeInfo;
import com.nodecraft.gui.utils.NodeIconManager;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.execution.ExecFrontierSnapshot;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.utilities.assist.RelayNode;
import com.nodecraft.nodesystem.io.SavedGraphComment;
import com.nodecraft.nodesystem.io.SavedGraphGroup;
import com.nodecraft.nodesystem.nodes.utilities.organization.SubgraphNode;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import org.jetbrains.annotations.Nullable;
import com.nodecraft.nodesystem.nodes.output.execute.ApplyChangesNode;
import com.nodecraft.nodesystem.nodes.output.preview.PreviewGeometryNode;
import com.nodecraft.nodesystem.nodes.output.preview.GeometryViewerNode;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.ImDrawList;
import imgui.flag.ImGuiCol;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiMouseButton;

/**
 * ImGui节点编辑器的渲染逻辑组件（重构版）
 * 现在使用专门的子组件来处理不同的渲染任务
 */
public class ImGuiNodeRenderer {
    private final ICanvasEditor editor;
    
    // 子渲染器组件
    private final NodeRenderCache cache;
    private final ConnectionRenderer connectionRenderer;
    private final CustomUIRenderer customUIRenderer;
    private final PortPositionCalculator portCalculator;

    public ImGuiNodeRenderer(ICanvasEditor editor) {
        this.editor = editor;
        this.cache = new NodeRenderCache();
        this.connectionRenderer = new ConnectionRenderer(editor);
        this.customUIRenderer = new CustomUIRenderer(editor);
        this.portCalculator = new PortPositionCalculator(editor);
    }

    public void renderGraphMetadata(
            ImDrawList drawList,
            ImVec2 canvasPos,
            java.util.List<SavedGraphComment> comments,
            java.util.List<SavedGraphGroup> groups,
            float canvasZoom,
            float canvasOffsetX,
            float canvasOffsetY
    ) {
        if (comments != null) {
            for (SavedGraphComment comment : comments) {
                if (comment == null) {
                    continue;
                }
                float x = canvasPos.x + (comment.x + canvasOffsetX) * canvasZoom;
                float y = canvasPos.y + (comment.y + canvasOffsetY) * canvasZoom;
                float w = Math.max(40f, comment.width * canvasZoom);
                float h = Math.max(24f, comment.height * canvasZoom);
                int bg = parseColor(comment.backgroundColor, 0xFFFFEB3B);
                int fg = parseColor(comment.textColor, 0xFF000000);
                drawList.addRectFilled(x, y, x + w, y + h, bg, 4f);
                drawList.addText(x + 6f, y + 6f, fg, comment.text == null ? "" : comment.text);
            }
        }
        if (groups != null) {
            for (SavedGraphGroup group : groups) {
                if (group == null) {
                    continue;
                }
                float x = canvasPos.x + (group.x + canvasOffsetX) * canvasZoom;
                float y = canvasPos.y + (group.y + canvasOffsetY) * canvasZoom;
                float w = Math.max(80f, group.width * canvasZoom);
                float h = Math.max(48f, group.height * canvasZoom);
                int color = parseColor(group.color, 0xFF3498DB);
                drawList.addRect(x, y, x + w, y + h, color, 6f, ImDrawFlags.None, 2f);
                drawList.addText(x + 8f, y + 8f, color, group.title == null ? "Group" : group.title);
            }
        }
    }

    private static int parseColor(@Nullable String hex, int fallback) {
        if (hex == null || hex.isBlank()) {
            return fallback;
        }
        String normalized = hex.startsWith("#") ? hex.substring(1) : hex;
        if (normalized.length() != 6) {
            return fallback;
        }
        try {
            int rgb = Integer.parseInt(normalized, 16);
            return 0xFF000000 | rgb;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public void renderNodesDirect(ImDrawList drawList, ImVec2 canvasPos, NodeGraph graph,
                                  Map<UUID, NodePosition> nodePositions, Map<UUID, Map<String, ImVec2>> portScreenPositions,
                                  java.util.Set<UUID> selectedNodeIds) {

        if (graph == null) return;

        customUIRenderer.beginInteractionFrame();
        cache.cleanupCacheIfNeeded();

        float canvasZoom = editor.getCanvasZoom();
        float canvasOffsetX = editor.getCanvasOffsetX();
        float canvasOffsetY = editor.getCanvasOffsetY();

        imgui.ImFont font = ImGui.getFont();
        float baseFontSize = ImGui.getFontSize();
        float baseTextLineHeight = ImGui.getTextLineHeight();
        float baseItemSpacingY = ImGui.getStyle().getItemSpacingY();
        int textColor = ImGui.getColorU32(ImGuiCol.Text);
        int navHighlightColor = ImGui.getColorU32(ImGuiCol.NavHighlight);

        ImGuiNodeInteraction interaction = editor.getInteraction();
        UUID hoveredNodeId = interaction != null ? interaction.getHoveredNodeId() : null;
        String hoveredPortId = interaction != null ? interaction.getHoveredPortId() : null;
        boolean isHoveredPortOutput = interaction != null && interaction.isHoveredPortOutput();
        boolean shouldHighlight = interaction != null && interaction.shouldShowPortHighlight();
        float highlightAnimTime = interaction != null ? interaction.getPortHighlightAnimationTimer() : 0f;

        float highlightSinValue = shouldHighlight ? (float)Math.sin(highlightAnimTime * Math.PI * 2) : 0f;
        float highlightCosValue = shouldHighlight ? (float)Math.cos(highlightAnimTime * Math.PI * 2) : 0f;

        NodeDisplayMode displayMode = resolveDisplayMode();
        for (INode node : graph.getNodes()) {
            portCalculator.precalculatePortTextSizes(node);
            updateNodeDimensionsInPosition(node, nodePositions, displayMode, baseTextLineHeight, baseItemSpacingY);
        }

        java.util.List<INode> unselectedNodes = new java.util.ArrayList<>();
        java.util.List<INode> selectedNodes = new java.util.ArrayList<>();

        for (INode node : graph.getNodes()) {
            if (selectedNodeIds.contains(node.getId())) {
                selectedNodes.add(node);
            } else {
                unselectedNodes.add(node);
            }
        }

        for (INode node : unselectedNodes) {
            renderSingleNode(drawList, canvasPos, node, nodePositions, portScreenPositions, selectedNodeIds,
                    canvasZoom, canvasOffsetX, canvasOffsetY, font, baseFontSize, baseTextLineHeight, baseItemSpacingY,
                    textColor, navHighlightColor, hoveredNodeId, hoveredPortId, isHoveredPortOutput,
                    shouldHighlight, highlightSinValue, highlightCosValue);
        }

        for (INode node : selectedNodes) {
            renderSingleNode(drawList, canvasPos, node, nodePositions, portScreenPositions, selectedNodeIds,
                    canvasZoom, canvasOffsetX, canvasOffsetY, font, baseFontSize, baseTextLineHeight, baseItemSpacingY,
                    textColor, navHighlightColor, hoveredNodeId, hoveredPortId, isHoveredPortOutput,
                    shouldHighlight, highlightSinValue, highlightCosValue);
        }
    }

    private void updateNodeDimensionsInPosition(INode node, Map<UUID, NodePosition> nodePositions,
                                                NodeDisplayMode displayMode,
                                                float baseTextLineHeight, float baseItemSpacingY) {
        UUID nodeId = node.getId();
        NodePosition pos = nodePositions.computeIfAbsent(nodeId, id -> new NodePosition(100, 100));
        NodeVisualLayout.Result layout = NodeVisualLayout.compute(
                node, displayMode, getVisibleInputPorts(node), getVisibleOutputPorts(node),
                cache::getCachedTextWidth, baseTextLineHeight, baseItemSpacingY);
        pos.setSize(layout.width(), layout.height());
    }

    private NodeDisplayMode resolveDisplayMode() {
        EditorSession session = editor.getEditorSession();
        return session != null ? session.getNodeDisplayMode() : NodeDisplayMode.TEXT_ONLY;
    }

    private void renderSingleNode(ImDrawList drawList, ImVec2 canvasPos, INode node,
                                  Map<UUID, NodePosition> nodePositions, Map<UUID, Map<String, ImVec2>> portScreenPositions,
                                  java.util.Set<UUID> selectedNodeIds, float canvasZoom, float canvasOffsetX, float canvasOffsetY,
                                  imgui.ImFont font, float baseFontSize, float baseTextLineHeight, float baseItemSpacingY,
                                  int textColor, int navHighlightColor,
                                  UUID hoveredNodeId, String hoveredPortId, boolean isHoveredPortOutput,
                                  boolean shouldHighlight, float highlightSinValue, float highlightCosValue) {

        UUID nodeId = node.getId();
        NodePosition pos = nodePositions.computeIfAbsent(nodeId, id -> new NodePosition(100, 100));

        float unscaledNodeWidth = pos.width;
        float unscaledNodeHeight = pos.height;

        float nodeScreenX = canvasPos.x + (pos.x * canvasZoom + canvasOffsetX);
        float nodeScreenY = canvasPos.y + (pos.y * canvasZoom + canvasOffsetY);
        float finalNodeWidthScaled = unscaledNodeWidth * canvasZoom;
        float finalNodeHeightScaled = unscaledNodeHeight * canvasZoom;
        NodeDisplayMode displayMode = resolveDisplayMode();
        List<IPort> visibleInputPorts = getVisibleInputPorts(node);
        List<IPort> visibleOutputPorts = getVisibleOutputPorts(node);
        NodeVisualLayout.Result layout = NodeVisualLayout.compute(
                node, displayMode, visibleInputPorts, visibleOutputPorts,
                cache::getCachedTextWidth, baseTextLineHeight, baseItemSpacingY);
        boolean compactRerouteNode = layout.compactReroute();

        if (!portScreenPositions.containsKey(nodeId)) {
            portScreenPositions.put(nodeId, new HashMap<>());
        }

        String category = NodeRenderConstants.getCategoryFromNodeTypeId(node.getTypeId());
        NodeRenderCache.CategoryColorCache colorCache = cache.getCategoryColorCache(category);
        int baseNodeColor = colorCache.baseColor;
        int nodeBgColor = colorCache.nodeBgColor;
        int borderColor = colorCache.borderColor;
        boolean subgraphNode = node instanceof SubgraphNode;

        if (subgraphNode) {
            baseNodeColor = ImGui.colorConvertFloat4ToU32(0.08f, 0.48f, 0.52f, 1.0f);
            nodeBgColor = ImGui.colorConvertFloat4ToU32(0.07f, 0.11f, 0.13f, 0.98f);
            borderColor = ImGui.colorConvertFloat4ToU32(0.26f, 0.84f, 0.82f, 1.0f);
        }

        if (node instanceof RelayNode relayNode) {
            String shortLabel = relayNode.getShortTagLabel();
            if (shortLabel != null && !shortLabel.isBlank()) {
                int mappedColor = parseHexColorToU32(relayNode.getResolvedColorHex(), baseNodeColor);
                baseNodeColor = mappedColor;
                nodeBgColor = cache.adjustBrightnessCached(mappedColor, 0.35f);
                borderColor = cache.adjustBrightnessCached(mappedColor, 1.35f);
            }
        }

        // 检查是否有自定义颜色
        Integer customColor = editor.getNodeCustomColor(nodeId);
        if (customColor != null) {
            // 使用自定义颜色作为节点头部颜色
            baseNodeColor = customColor;
            // 为自定义颜色生成相应的背景色和边框色
            nodeBgColor = cache.adjustBrightnessCached(customColor, 0.3f);
            borderColor = cache.adjustBrightnessCached(customColor, 1.5f);
        }

        // 检查节点状态并应用视觉效果
        boolean isDisabled = editor.isNodeDisabled(nodeId);
        boolean isHidden = !editor.isNodeVisible(nodeId);
        
        if (isDisabled) {
            // 禁用节点：降低亮度，使其呈现灰化效果
            baseNodeColor = cache.adjustBrightnessCached(baseNodeColor, 0.5f);
            nodeBgColor = cache.adjustBrightnessCached(nodeBgColor, 0.6f);
            borderColor = cache.adjustBrightnessCached(borderColor, 0.7f);
        }
        
        if (isHidden) {
            // 隐藏节点：降低透明度，使其呈现半透明效果
            baseNodeColor = cache.adjustAlphaCached(baseNodeColor, 0.6f);
            nodeBgColor = cache.adjustAlphaCached(nodeBgColor, 0.6f);
            borderColor = cache.adjustAlphaCached(borderColor, 0.6f);
        }

        float scaledPortCircleToTextPadding = NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING * canvasZoom;
        float portRadiusScaled = NodeRenderConstants.PORT_RADIUS_UNSCALED * canvasZoom;
        float scaledTextLineHeight = baseTextLineHeight * canvasZoom;
        float scaledPortVerticalSpacing = baseItemSpacingY * canvasZoom * 0.8f;

        ImGui.pushID(nodeId.toString());

        float nodeCornerRadiusScaled = NodeRenderConstants.NODE_CORNER_RADIUS_UNSCALED * canvasZoom;
        float nodeBorderThicknessScaled = NodeRenderConstants.NODE_BORDER_THICKNESS_UNSCALED * canvasZoom;

        drawList.addRectFilled(nodeScreenX, nodeScreenY, nodeScreenX + finalNodeWidthScaled, nodeScreenY + finalNodeHeightScaled, nodeBgColor, nodeCornerRadiusScaled);
        if (!compactRerouteNode) {
            float headerHeightScaled = layout.headerHeight() * canvasZoom;
            drawList.addRectFilled(nodeScreenX, nodeScreenY, nodeScreenX + finalNodeWidthScaled, nodeScreenY + headerHeightScaled, baseNodeColor, nodeCornerRadiusScaled, ImDrawFlags.RoundCornersTop);
        }
        drawList.addRect(nodeScreenX, nodeScreenY, nodeScreenX + finalNodeWidthScaled, nodeScreenY + finalNodeHeightScaled, borderColor, nodeCornerRadiusScaled, 0, nodeBorderThicknessScaled);
        if (subgraphNode) {
            drawList.addRect(nodeScreenX - 2.0f * canvasZoom, nodeScreenY - 2.0f * canvasZoom,
                    nodeScreenX + finalNodeWidthScaled + 2.0f * canvasZoom,
                    nodeScreenY + finalNodeHeightScaled + 2.0f * canvasZoom,
                    borderColor, nodeCornerRadiusScaled + 2.0f * canvasZoom, 0,
                    Math.max(1.0f, nodeBorderThicknessScaled * 0.65f));
        }

        ExecFrontierSnapshot execFrontier = editor.getActiveExecFrontierSnapshot();
        if (execFrontier.isActiveNode(nodeId)) {
            float pulseAlpha = 0.55f + 0.35f * Math.abs(highlightSinValue);
            int execGlow = cache.adjustAlphaCached(NodeRenderConstants.EXEC_FRONTIER_NODE_COLOR, pulseAlpha);
            drawList.addRect(
                    nodeScreenX - 2.0f * canvasZoom,
                    nodeScreenY - 2.0f * canvasZoom,
                    nodeScreenX + finalNodeWidthScaled + 2.0f * canvasZoom,
                    nodeScreenY + finalNodeHeightScaled + 2.0f * canvasZoom,
                    execGlow,
                    nodeCornerRadiusScaled + 2.0f * canvasZoom,
                    0,
                    Math.max(nodeBorderThicknessScaled * 2.0f, 2.5f * canvasZoom)
            );
        } else if (execFrontier.isPendingNode(nodeId)) {
            drawList.addRect(
                    nodeScreenX - canvasZoom,
                    nodeScreenY - canvasZoom,
                    nodeScreenX + finalNodeWidthScaled + canvasZoom,
                    nodeScreenY + finalNodeHeightScaled + canvasZoom,
                    NodeRenderConstants.EXEC_FRONTIER_PENDING_NODE_COLOR,
                    nodeCornerRadiusScaled + canvasZoom,
                    0,
                    Math.max(nodeBorderThicknessScaled * 1.5f, 2.0f * canvasZoom)
            );
        }

        // 为禁用节点添加虚线边框覆盖
        if (isDisabled) {
            int dashedBorderColor = cache.adjustBrightnessCached(borderColor, 1.3f);
            float dashLength = 6.0f * canvasZoom;
            float gapLength = 4.0f * canvasZoom;
            drawDashedRect(drawList, nodeScreenX - 1, nodeScreenY - 1, 
                          nodeScreenX + finalNodeWidthScaled + 1, nodeScreenY + finalNodeHeightScaled + 1,
                          dashedBorderColor, nodeCornerRadiusScaled, nodeBorderThicknessScaled * 1.5f,
                          dashLength, gapLength);
        }
        
        // 为隐藏节点添加对角线条纹效果
        if (isHidden) {
            int stripeColor = cache.adjustAlphaCached(ImGui.getColorU32(ImGuiCol.Text), 0.3f);
            float stripeSpacing = 15.0f * canvasZoom;
            drawDiagonalStripes(drawList, nodeScreenX, nodeScreenY, 
                               nodeScreenX + finalNodeWidthScaled, nodeScreenY + finalNodeHeightScaled,
                               stripeColor, stripeSpacing, canvasZoom);
        }

        if (!compactRerouteNode && layout.showTitle()) {
            String title = NodeVisualLayout.nodeTitle(node);
            float titleTextWidthUnscaled = cache.getCachedTextWidth(title);
            float titleX = nodeScreenX + (finalNodeWidthScaled - titleTextWidthUnscaled * canvasZoom) / 2;
            float titleY = nodeScreenY + (layout.headerHeight() * canvasZoom - scaledTextLineHeight) / 2;
            drawList.addText(font, baseFontSize * canvasZoom, titleX, titleY, textColor, title);

            if (node instanceof RelayNode relayNode) {
                renderTagRelayBadge(drawList, relayNode, nodeScreenX, nodeScreenY, finalNodeWidthScaled,
                        baseTextLineHeight, baseFontSize, canvasZoom, textColor, font);
            }
        }

        if (!compactRerouteNode && layout.showIcon()) {
            renderNodeIconAndCaption(drawList, node, layout, nodeScreenX, nodeScreenY, finalNodeWidthScaled,
                    canvasZoom, font, baseFontSize, textColor, category);
        }

        boolean hasInputPorts = !visibleInputPorts.isEmpty();
        boolean hasOutputPorts = !visibleOutputPorts.isEmpty();
        boolean hasAnyPorts = hasInputPorts || hasOutputPorts;

        float portYOffset;
        if (compactRerouteNode) {
            portYOffset = nodeScreenY + (finalNodeHeightScaled - scaledTextLineHeight) / 2.0f;
        } else {
            portYOffset = nodeScreenY + layout.portRegionTop() * canvasZoom;
        }

        if (hasAnyPorts) {
                float currentPortRadiusScaled = compactRerouteNode
                    ? NodeRenderConstants.REROUTE_PORT_RADIUS_UNSCALED * canvasZoom
                    : portRadiusScaled;
                renderNodePorts(drawList, layout, visibleInputPorts, visibleOutputPorts, nodeId, nodeScreenX, finalNodeWidthScaled, portYOffset,
                    currentPortRadiusScaled, scaledTextLineHeight, scaledPortVerticalSpacing, scaledPortCircleToTextPadding,
                    font, baseFontSize, canvasZoom, textColor, navHighlightColor,
                    hoveredNodeId, hoveredPortId, isHoveredPortOutput, shouldHighlight, 
                    highlightSinValue, highlightCosValue, portScreenPositions,
                    !compactRerouteNode);
        }

        boolean nodeHasCustomUI = layout.hasCustomUI();
        float nodeBodyInteractionHeight = finalNodeHeightScaled;
        if (nodeHasCustomUI && !compactRerouteNode) {
            nodeBodyInteractionHeight = Math.max(0f, layout.customUIRegionTop() * canvasZoom);
        }
        ImGui.setCursorScreenPos(nodeScreenX, nodeScreenY);
        ImGui.invisibleButton("node_interaction_area_" + nodeId, finalNodeWidthScaled, nodeBodyInteractionHeight);

        if (nodeHasCustomUI && !compactRerouteNode) {
            renderCustomUI(node, nodeId, nodeScreenX, nodeScreenY, finalNodeWidthScaled, canvasZoom, layout);
        }

        handleNodeInteraction(nodeId, selectedNodeIds, nodeHasCustomUI,
                nodeScreenX, nodeScreenY, finalNodeWidthScaled, finalNodeHeightScaled);

        if (selectedNodeIds.contains(nodeId)) {
            float selectionCornerRadius = 7.0f * canvasZoom;
            drawList.addRect(nodeScreenX - 2, nodeScreenY - 2,
                    nodeScreenX + finalNodeWidthScaled + 2, nodeScreenY + finalNodeHeightScaled + 2,
                    navHighlightColor, selectionCornerRadius, 0, nodeBorderThicknessScaled);
        }
        ImGui.popID();
    }

    private void renderCustomUI(
            INode node,
            UUID nodeId,
            float nodeScreenX,
            float nodeScreenY,
            float finalNodeWidthScaled,
            float canvasZoom,
            NodeVisualLayout.Result layout) {
        float customUIUnscaledHeight = layout.customUIHeight();
        if (customUIUnscaledHeight <= 0f) {
            return;
        }

        float customUIStartY = nodeScreenY + layout.customUIRegionTop() * canvasZoom;
        float customUILogicalWidth = finalNodeWidthScaled / canvasZoom
                - 2f * NodeRenderConstants.NODE_HORIZONTAL_PADDING;
        float customUIScreenX = nodeScreenX + NodeRenderConstants.NODE_HORIZONTAL_PADDING * canvasZoom;

        ICustomUINode customUINode = null;
        boolean supportsDirectDrawing = false;
        if (node instanceof ICustomUINode customUI) {
            customUINode = customUI;
            supportsDirectDrawing = customUI.supportsDirectDrawing();
        }

        CustomUIRenderer.CustomUIRenderInfo info = new CustomUIRenderer.CustomUIRenderInfo(
                node,
                customUINode,
                nodeId,
                customUIScreenX,
                customUIStartY,
                customUILogicalWidth,
                customUIUnscaledHeight,
                canvasZoom,
                supportsDirectDrawing);
        customUIRenderer.renderNodeCustomUI(info);
    }

    private void renderNodePorts(ImDrawList drawList, NodeVisualLayout.Result layout,
                                 List<IPort> visibleInputPorts, List<IPort> visibleOutputPorts, UUID nodeId,
                                 float nodeScreenX, float finalNodeWidthScaled,
                                 float portYOffset, float portRadiusScaled, float scaledTextLineHeight, float scaledPortVerticalSpacing,
                                 float scaledPortCircleToTextPadding, imgui.ImFont font, float baseFontSize, float canvasZoom,
                                 int textColor, int navHighlightColor, UUID hoveredNodeId, String hoveredPortId,
                                 boolean isHoveredPortOutput, boolean shouldHighlight, float highlightSinValue,
                                 float highlightCosValue, Map<UUID, Map<String, ImVec2>> portScreenPositions,
                                 boolean showPortLabels) {

        boolean hasInputPorts = !visibleInputPorts.isEmpty();
        boolean hasOutputPorts = !visibleOutputPorts.isEmpty();

        if (hasInputPorts) {
            renderInputPorts(drawList, visibleInputPorts, layout.inputLabels(), nodeId, nodeScreenX, portYOffset, portRadiusScaled,
                    scaledTextLineHeight, scaledPortVerticalSpacing, scaledPortCircleToTextPadding,
                    font, baseFontSize, canvasZoom, textColor, navHighlightColor,
                    hoveredNodeId, hoveredPortId, isHoveredPortOutput, shouldHighlight,
                    highlightSinValue, highlightCosValue, portScreenPositions, showPortLabels);
        }

        if (hasOutputPorts) {
            renderOutputPorts(drawList, visibleOutputPorts, layout.outputLabels(), nodeId, nodeScreenX, finalNodeWidthScaled, portYOffset,
                    portRadiusScaled, scaledTextLineHeight, scaledPortVerticalSpacing, scaledPortCircleToTextPadding,
                    font, baseFontSize, canvasZoom, textColor, navHighlightColor,
                    hoveredNodeId, hoveredPortId, isHoveredPortOutput, shouldHighlight,
                    highlightSinValue, highlightCosValue, portScreenPositions, showPortLabels);
        }
    }

    private void renderNodeIconAndCaption(ImDrawList drawList, INode node, NodeVisualLayout.Result layout,
                                          float nodeScreenX, float nodeScreenY, float finalNodeWidthScaled,
                                          float canvasZoom, imgui.ImFont font, float baseFontSize, int textColor,
                                          String category) {
        float iconSizeScaled = layout.iconSize() * canvasZoom;
        float iconLeft = nodeScreenX + layout.iconLeft() * canvasZoom;
        float iconTop = nodeScreenY + layout.iconTop() * canvasZoom;

        String typeId = node.getTypeId();
        String explicitIcon = null;
        NodeInfo info = NodeRegistry.getInstance().getNodeInfo(typeId);
        if (info != null) {
            explicitIcon = info.getIcon();
        }
        int textureId = NodeIconManager.getInstance().loadNodeIcon(typeId, category, explicitIcon);
        if (textureId > 0) {
            drawList.addImage(textureId, iconLeft, iconTop, iconLeft + iconSizeScaled, iconTop + iconSizeScaled,
                    0.0f, 0.0f, 1.0f, 1.0f);
        } else {
            int placeholder = ImGui.colorConvertFloat4ToU32(0.55f, 0.55f, 0.55f, 0.85f);
            drawList.addRectFilled(iconLeft, iconTop, iconLeft + iconSizeScaled, iconTop + iconSizeScaled, placeholder, 2.0f * canvasZoom);
        }

        String caption = layout.caption();
        if (caption != null && !caption.isEmpty()) {
            // Cap to the center band between port-letter columns so caption does not overlap letters.
            float maxCaptionWidth = Math.max(8f, layout.captionMaxWidth());
            String drawnCaption = cache.truncateTextWithEllipsisOptimized(caption, maxCaptionWidth);
            float captionWidthScaled = cache.getCachedTextWidth(drawnCaption) * canvasZoom;
            float captionX = nodeScreenX + (finalNodeWidthScaled - captionWidthScaled) / 2f;
            float captionY = nodeScreenY + layout.captionTop() * canvasZoom;
            drawList.addText(font, baseFontSize * canvasZoom, captionX, captionY, textColor, drawnCaption);
        }
    }

    private void renderInputPorts(ImDrawList drawList, List<IPort> visibleInputPorts, List<String> inputLabels,
                                  UUID nodeId, float nodeScreenX, float portYOffset,
                                  float portRadiusScaled, float scaledTextLineHeight, float scaledPortVerticalSpacing,
                                  float scaledPortCircleToTextPadding, imgui.ImFont font, float baseFontSize, float canvasZoom,
                                  int textColor, int navHighlightColor, UUID hoveredNodeId, String hoveredPortId,
                                  boolean isHoveredPortOutput, boolean shouldHighlight, float highlightSinValue,
                                  float highlightCosValue,
                                  Map<UUID, Map<String, ImVec2>> portScreenPositions,
                                  boolean showPortLabels) {

        int numInputPorts = visibleInputPorts.size();
        for (int i = 0; i < numInputPorts; i++) {
            IPort port = visibleInputPorts.get(i);
            float currentPortY = portYOffset + i * (scaledTextLineHeight + scaledPortVerticalSpacing) + scaledTextLineHeight / 2;

            boolean isPortHighlighted = shouldHighlight && nodeId.equals(hoveredNodeId) && port.getId().equals(hoveredPortId) && !isHoveredPortOutput;
            ExecFrontierSnapshot execFrontier = editor.getActiveExecFrontierSnapshot();
            boolean execPortActive = execFrontier.active()
                    && NodeRenderConstants.isExecPort(port)
                    && execFrontier.isActiveNode(nodeId);
            int portColor = NodeRenderConstants.getInputPortFillColor(port, isPortHighlighted || execPortActive);
            int portBorderColor = NodeRenderConstants.getPortBorderColor(port, true, isPortHighlighted || execPortActive, navHighlightColor);

            if (isPortHighlighted || execPortActive) {
                float pulseScale = 1.0f + 0.3f * highlightSinValue;
                float portOuterRadiusAnim = portRadiusScaled * pulseScale;

                float highlightRadius = portOuterRadiusAnim * 1.5f;
                int highlightColor = cache.adjustAlphaCached(portBorderColor, 0.5f + 0.3f * highlightCosValue);
                drawList.addCircle(nodeScreenX, currentPortY, highlightRadius, highlightColor, 12, NodeRenderConstants.PORT_BORDER_THICKNESS_UNSCALED * canvasZoom);
            }

            drawList.addCircleFilled(nodeScreenX, currentPortY, portRadiusScaled, portColor);
            drawList.addCircle(nodeScreenX, currentPortY, portRadiusScaled, portBorderColor, 12, NodeRenderConstants.PORT_BORDER_THICKNESS_UNSCALED * canvasZoom);

            if (showPortLabels) {
                String displayText = i < inputLabels.size() ? inputLabels.get(i) : NodePortLabelUtil.firstLetter(port.getDisplayName());
                drawList.addText(font, baseFontSize * canvasZoom, nodeScreenX + portRadiusScaled + scaledPortCircleToTextPadding, currentPortY - scaledTextLineHeight / 2, textColor, displayText);
            }

            portScreenPositions.get(nodeId).put(port.getId(), new ImVec2(nodeScreenX, currentPortY));
        }
    }

    private void renderOutputPorts(ImDrawList drawList, List<IPort> visibleOutputPorts, List<String> outputLabels,
                                   UUID nodeId, float nodeScreenX, float finalNodeWidthScaled,
                                   float portYOffset, float portRadiusScaled, float scaledTextLineHeight, float scaledPortVerticalSpacing,
                                   float scaledPortCircleToTextPadding, imgui.ImFont font, float baseFontSize, float canvasZoom,
                                   int textColor, int navHighlightColor, UUID hoveredNodeId, String hoveredPortId,
                                   boolean isHoveredPortOutput, boolean shouldHighlight, float highlightSinValue,
                                   float highlightCosValue,
                                   Map<UUID, Map<String, ImVec2>> portScreenPositions,
                                   boolean showPortLabels) {

        float outputPortCircleX = nodeScreenX + finalNodeWidthScaled;
        int numOutputPorts = visibleOutputPorts.size();
        for (int i = 0; i < numOutputPorts; i++) {
            IPort port = visibleOutputPorts.get(i);
            float currentPortY = portYOffset + i * (scaledTextLineHeight + scaledPortVerticalSpacing) + scaledTextLineHeight / 2;

            String displayText = i < outputLabels.size() ? outputLabels.get(i) : NodePortLabelUtil.firstLetter(port.getDisplayName());
            float displayTextWidthUnscaled = cache.getCachedTextWidth(displayText);
            float outputTextX = outputPortCircleX - portRadiusScaled - scaledPortCircleToTextPadding - (displayTextWidthUnscaled * canvasZoom);

            boolean isPortHighlighted = shouldHighlight && nodeId.equals(hoveredNodeId) && port.getId().equals(hoveredPortId) && isHoveredPortOutput;
            ExecFrontierSnapshot execFrontier = editor.getActiveExecFrontierSnapshot();
            boolean execPortActive = execFrontier.active()
                    && NodeRenderConstants.isExecPort(port)
                    && execFrontier.isActiveNode(nodeId);
            int portColor = NodeRenderConstants.getOutputPortFillColor(port, isPortHighlighted || execPortActive);
            int portBorderColor = NodeRenderConstants.getPortBorderColor(port, false, isPortHighlighted || execPortActive, navHighlightColor);

            if (isPortHighlighted || execPortActive) {
                float pulseScale = 1.0f + 0.3f * highlightSinValue;
                float portOuterRadiusAnim = portRadiusScaled * pulseScale;

                float highlightRadius = portOuterRadiusAnim * 1.5f;
                int highlightColor = cache.adjustAlphaCached(portBorderColor, 0.5f + 0.3f * highlightCosValue);
                drawList.addCircle(outputPortCircleX, currentPortY, highlightRadius, highlightColor, 12, NodeRenderConstants.PORT_BORDER_THICKNESS_UNSCALED * canvasZoom);
            }

            drawList.addCircleFilled(outputPortCircleX, currentPortY, portRadiusScaled, portColor);
            drawList.addCircle(outputPortCircleX, currentPortY, portRadiusScaled, portBorderColor, 12, NodeRenderConstants.PORT_BORDER_THICKNESS_UNSCALED * canvasZoom);

            if (showPortLabels) {
                drawList.addText(font, baseFontSize * canvasZoom, outputTextX, currentPortY - scaledTextLineHeight / 2, textColor, displayText);
            }

            portScreenPositions.get(nodeId).put(port.getId(), new ImVec2(outputPortCircleX, currentPortY));
        }
    }

    private static List<IPort> getVisibleInputPorts(INode node) {
        return filterViewerPorts(node.getInputPorts(), node);
    }

    private void renderTagRelayBadge(ImDrawList drawList, RelayNode node, float nodeScreenX, float nodeScreenY,
                                     float finalNodeWidthScaled, float baseTextLineHeight, float baseFontSize,
                                     float canvasZoom, int defaultTextColor, imgui.ImFont font) {
        String shortLabel = node.getShortTagLabel();
        if (shortLabel == null || shortLabel.isBlank()) {
            return;
        }

        float badgePadX = 5.0f * canvasZoom;
        float badgePadY = 2.0f * canvasZoom;
        float badgeMarginX = 6.0f * canvasZoom;
        float badgeMarginY = 3.0f * canvasZoom;

        float maxLabelWidthUnscaled = Math.max(16.0f, (finalNodeWidthScaled * 0.45f) / Math.max(canvasZoom, 0.001f));
        String displayLabel = cache.truncateTextWithEllipsisOptimized(shortLabel, maxLabelWidthUnscaled);
        float labelWidthScaled = cache.getCachedTextWidth(displayLabel) * canvasZoom;
        float badgeHeight = baseTextLineHeight * canvasZoom + badgePadY * 2;
        float badgeWidth = labelWidthScaled + badgePadX * 2;

        float badgeX2 = nodeScreenX + finalNodeWidthScaled - badgeMarginX;
        float badgeX1 = Math.max(nodeScreenX + badgeMarginX, badgeX2 - badgeWidth);
        float badgeY1 = nodeScreenY + badgeMarginY;
        float badgeY2 = badgeY1 + badgeHeight;

        int badgeColor = parseHexColorToU32(node.getResolvedColorHex(), NodeRenderConstants.DEFAULT_CATEGORY_COLOR);
        int badgeBgColor = cache.adjustAlphaCached(badgeColor, 0.92f);
        int badgeBorderColor = cache.adjustBrightnessCached(badgeColor, 0.78f);

        drawList.addRectFilled(badgeX1, badgeY1, badgeX2, badgeY2, badgeBgColor, 4.0f * canvasZoom);
        drawList.addRect(badgeX1, badgeY1, badgeX2, badgeY2, badgeBorderColor, 4.0f * canvasZoom, 0,
                NodeRenderConstants.NODE_BORDER_THICKNESS_UNSCALED * 0.8f * canvasZoom);

        int badgeTextColor = chooseContrastingTextColor(badgeColor, defaultTextColor);
        drawList.addText(font, baseFontSize * canvasZoom, badgeX1 + badgePadX, badgeY1 + badgePadY,
                badgeTextColor, displayLabel);
    }

    private static int parseHexColorToU32(String colorHex, int fallback) {
        if (colorHex == null) {
            return fallback;
        }

        String normalized = colorHex.trim();
        if (!normalized.startsWith("#")) {
            return fallback;
        }

        try {
            if (normalized.length() == 7) {
                int r = Integer.parseInt(normalized.substring(1, 3), 16);
                int g = Integer.parseInt(normalized.substring(3, 5), 16);
                int b = Integer.parseInt(normalized.substring(5, 7), 16);
                return ImGui.colorConvertFloat4ToU32(r / 255.0f, g / 255.0f, b / 255.0f, 1.0f);
            }

            if (normalized.length() == 9) {
                int a = Integer.parseInt(normalized.substring(1, 3), 16);
                int r = Integer.parseInt(normalized.substring(3, 5), 16);
                int g = Integer.parseInt(normalized.substring(5, 7), 16);
                int b = Integer.parseInt(normalized.substring(7, 9), 16);
                return ImGui.colorConvertFloat4ToU32(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f);
            }
        } catch (NumberFormatException ignored) {
            return fallback;
        }

        return fallback;
    }

    private static int chooseContrastingTextColor(int backgroundColor, int fallbackTextColor) {
        int r = backgroundColor & 0xFF;
        int g = (backgroundColor >> 8) & 0xFF;
        int b = (backgroundColor >> 16) & 0xFF;

        float luminance = 0.2126f * (r / 255.0f) + 0.7152f * (g / 255.0f) + 0.0722f * (b / 255.0f);
        if (luminance > 0.55f) {
            return ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.12f, 1.0f);
        }
        if (luminance <= 0.45f) {
            return ImGui.colorConvertFloat4ToU32(0.95f, 0.95f, 0.95f, 1.0f);
        }
        return fallbackTextColor;
    }

    private static List<IPort> getVisibleOutputPorts(INode node) {
        return filterViewerPorts(node.getOutputPorts(), node);
    }

    private static List<IPort> filterViewerPorts(List<IPort> ports, INode node) {
        if (ports == null || ports.isEmpty()) {
            return ports;
        }

        List<IPort> visiblePorts = new ArrayList<>();
        for (IPort port : ports) {
            if (port == null) {
                continue;
            }

            String portId = port.getId();
            boolean isLegacyInput;

            switch (node) {
                case GeometryViewerNode geometryViewerNode -> isLegacyInput =
                        "input_box_geometry".equals(portId) ||
                                "input_cylinder_geometry".equals(portId) ||
                                "input_sphere_geometry".equals(portId) ||
                                "input_torus_geometry".equals(portId) ||
                                "input_color".equals(portId) ||
                                "input_transparency".equals(portId);
                case PreviewGeometryNode previewGeometryNode -> isLegacyInput =
                        "input_box_geometry".equals(portId) ||
                                "input_cylinder_geometry".equals(portId) ||
                                "input_sphere_geometry".equals(portId) ||
                                "input_torus_geometry".equals(portId) ||
                                "input_cone_geometry".equals(portId) ||
                                "input_frustum_cone_geometry".equals(portId) ||
                                "input_ellipsoid_geometry".equals(portId) ||
                                "input_hemisphere_geometry".equals(portId) ||
                                "input_prism_geometry".equals(portId) ||
                                "input_tetrahedron_geometry".equals(portId) ||
                                "input_octahedron_geometry".equals(portId) ||
                                "input_icosahedron_geometry".equals(portId) ||
                                "input_dodecahedron_geometry".equals(portId);
                case ApplyChangesNode applyChangesNode -> isLegacyInput =
                        "input_box_geometry".equals(portId) ||
                                "input_cylinder_geometry".equals(portId) ||
                                "input_sphere_geometry".equals(portId) ||
                                "input_torus_geometry".equals(portId) ||
                                "input_preview_ids".equals(portId) ||
                                "input_notify".equals(portId);
                case null, default -> {
                    return ports;
                }
            }

            if (isLegacyInput && !port.isConnected()) {
                continue;
            }

            visiblePorts.add(port);
        }
        return visiblePorts;
    }

    private void handleNodeInteraction(UUID nodeId, java.util.Set<UUID> selectedNodeIds,
                                         boolean nodeHasCustomUI,
                                         float nodeScreenX, float nodeScreenY,
                                         float nodeWidthScaled, float nodeHeightScaled) {
        // 不在此处用 isWindowHovered 短路：自定义 UI Item 会影响 Hovered 判定。
        // 单击选中已在 ImGuiNodeEditor 与框选相同的 getNodeIdUnderMouse 路径处理；
        // 这里只负责端口连线与拖拽生命周期。

        ImGuiNodeInteraction interaction = editor.getInteraction();
        ImVec2 mousePos = ImGui.getIO().getMousePos();

        boolean isMouseInNodeBounds = mousePos.x >= nodeScreenX && mousePos.x <= nodeScreenX + nodeWidthScaled &&
                                     mousePos.y >= nodeScreenY && mousePos.y <= nodeScreenY + nodeHeightScaled;

        boolean isCustomUIWidgetActive = isIsCustomUIWidgetActive(nodeId, nodeHasCustomUI, isMouseInNodeBounds);

        if (ImGuiInputAdapter.isMouseClicked(ImGuiMouseButton.Left)) {
            UUID clickTarget = interaction.getPendingClickTargetNodeId();
            if (!nodeId.equals(clickTarget)) {
                return;
            }

            boolean isMouseOnPort = interaction.isMouseOverAnyPortOfNode(nodeId, mousePos, editor.getPortScreenPositions());

            if (isMouseOnPort) {
                interaction.updateHoveredPort(mousePos, editor.getPortScreenPositions(), editor.getCurrentGraph());

                UUID currentHoveredNodeId = interaction.getHoveredNodeId();
                String currentHoveredPortId = interaction.getHoveredPortId();
                boolean currentIsHoveredPortOutput = interaction.isHoveredPortOutput();

                if (currentHoveredNodeId != null && currentHoveredPortId != null) {
                    interaction.tryStartConnectionCreation(currentHoveredNodeId, currentHoveredPortId, currentIsHoveredPortOutput, editor.getPortScreenPositions());
                    NodeCraft.LOGGER.debug("从节点内部端口区域启动连接创建: NodeId={}, PortId={}", currentHoveredNodeId, currentHoveredPortId);
                }
            } else if (isMouseInNodeBounds) {
                // 选中由 Editor 主路径负责；这里仅在非控件区域补启拖拽（防主路径漏启）。
                boolean overCustomUIWidget = nodeHasCustomUI && customUIRenderer.isCustomUIWidgetHovered(nodeId);
                if (!isCustomUIWidgetActive && !overCustomUIWidget && !interaction.isDraggingNode()) {
                    interaction.tryStartNodeDraggingFromNodeBody(nodeId);
                }
                ImGui.getIO().setWantCaptureMouse(true);
            }
        }

        if (ImGui.isItemDeactivated()) {
            interaction.clearNodeBodyActive(nodeId);
            interaction.tryStopNodeDragging();
        }

        if (interaction.isDraggingNode() && ImGuiInputAdapter.isMouseReleased(ImGuiMouseButton.Left)) {
            interaction.tryStopNodeDragging();
        }
    }

    /**
     * 自定义 UI 控件是否应阻止节点拖拽（仍允许选中）。
     */
    public boolean isCustomUIWidgetBlockingNodeDrag(UUID nodeId) {
        return customUIRenderer.isCustomUIWidgetHovered(nodeId) || customUIRenderer.isCustomUIWidgetActive();
    }

    private boolean isIsCustomUIWidgetActive(UUID nodeId, boolean nodeHasCustomUI, boolean isMouseInNodeBounds) {
        boolean isCustomUIHoveredEmpty = false;
        boolean isCustomUIWidgetActive = false;
        if (nodeHasCustomUI) {
            UUID hoveredCustomUINodeId = customUIRenderer.getCustomUIHoveredEmptyNodeId();
            isCustomUIHoveredEmpty = hoveredCustomUINodeId != null && hoveredCustomUINodeId.equals(nodeId);
            isCustomUIWidgetActive = customUIRenderer.isCustomUIWidgetActive();
        }

        // invisibleButton 是否活跃（标准方式）
        boolean isInvisibleButtonActive = ImGui.isItemActive();

        // 综合判断：节点可拖动条件
        // 1. 标准方式：invisible button 活跃
        // 2. 增强方式：鼠标在节点区域内 + 自定义UI空白区域被悬停（无控件活跃）
        boolean canDragNode = isInvisibleButtonActive || (isMouseInNodeBounds && isCustomUIHoveredEmpty && !isCustomUIWidgetActive);
        return isCustomUIWidgetActive;
    }

    public void renderConnectionsDirect(ImDrawList drawList, NodeGraph graph,
                                        Map<UUID, Map<String, ImVec2>> portScreenPositions,
                                        java.util.Set<UUID> selectedNodeIds) {
        connectionRenderer.renderConnectionsDirect(drawList, graph, portScreenPositions, selectedNodeIds);
    }

    public void renderForegroundConnections(ImDrawList drawList, NodeGraph graph,
                                            Map<UUID, Map<String, ImVec2>> portScreenPositions,
                                            java.util.Set<UUID> selectedNodeIds) {
        connectionRenderer.renderForegroundConnections(drawList, graph, portScreenPositions, selectedNodeIds);
    }

    public void drawConnectionPreview(ImDrawList drawList, ImVec2 startPos, float canvasZoom, boolean isFromOutput, boolean typeMismatch) {
        connectionRenderer.drawConnectionPreview(drawList, startPos, canvasZoom, isFromOutput, typeMismatch);
    }

    public void drawSelectionBox(ImDrawList drawList, ImVec2 canvasPos, NodePosition boxSelectStart, NodePosition boxSelectEnd) {
        NodeDrawingUtils.drawSelectionBox(drawList, canvasPos, boxSelectStart, boxSelectEnd, editor);
    }

    public void calculatePortPositions(ImVec2 canvasPos, NodeGraph graph,
                                       Map<UUID, NodePosition> nodePositions,
                                       Map<UUID, Map<String, ImVec2>> portScreenPositions) {
        portCalculator.calculatePortPositions(canvasPos, graph, nodePositions, portScreenPositions);
    }

    // === 节点状态视觉效果绘制方法 ===

    /**
     * 绘制虚线矩形边框
     */
    private void drawDashedRect(ImDrawList drawList, float x1, float y1, float x2, float y2,
                               int color, float rounding, float thickness, float dashLength, float gapLength) {
        // 简化实现：绘制多个短线段来模拟虚线效果

        // 绘制上边
        drawDashedLine(drawList, x1, y1, x2, y1, color, thickness, dashLength, gapLength);
        // 绘制右边
        drawDashedLine(drawList, x2, y1, x2, y2, color, thickness, dashLength, gapLength);
        // 绘制下边
        drawDashedLine(drawList, x2, y2, x1, y2, color, thickness, dashLength, gapLength);
        // 绘制左边
        drawDashedLine(drawList, x1, y2, x1, y1, color, thickness, dashLength, gapLength);
    }

    /**
     * 绘制虚线
     */
    private void drawDashedLine(ImDrawList drawList, float x1, float y1, float x2, float y2,
                               int color, float thickness, float dashLength, float gapLength) {
        float totalLength = (float)Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
        if (totalLength < 0.001f) return;
        
        float dirX = (x2 - x1) / totalLength;
        float dirY = (y2 - y1) / totalLength;
        
        float currentPos = 0;
        float totalDashCycle = dashLength + gapLength;
        
        while (currentPos < totalLength) {
            float dashStart = currentPos;
            float dashEnd = Math.min(currentPos + dashLength, totalLength);
            
            if (dashEnd > dashStart) {
                float startX = x1 + dirX * dashStart;
                float startY = y1 + dirY * dashStart;
                float endX = x1 + dirX * dashEnd;
                float endY = y1 + dirY * dashEnd;
                
                drawList.addLine(startX, startY, endX, endY, color, thickness);
            }
            
            currentPos += totalDashCycle;
        }
    }

    /**
     * 绘制对角线条纹
     */
    private void drawDiagonalStripes(ImDrawList drawList, float x1, float y1, float x2, float y2,
                                    int color, float spacing, float thickness) {
        float width = x2 - x1;
        float height = y2 - y1;
        
        // 从左上角到右下角绘制对角线
        for (float offset = -height; offset < width; offset += spacing) {
            float startX = x1 + offset;
            float startY = y1;
            float endX = x1 + offset + height;
            float endY = y2;
            
            // 裁剪线段到矩形边界内
            if (startX < x1) {
                float t = (x1 - startX) / (endX - startX);
                startX = x1;
                startY = y1 + t * height;
            }
            if (endX > x2) {
                float t = (x2 - startX) / (endX - startX);
                endX = x2;
                endY = startY + t * (endY - startY);
            }
            
            if (startX <= endX && startY <= endY) {
                drawList.addLine(startX, startY, endX, endY, color, thickness);
            }
        }
    }
}
