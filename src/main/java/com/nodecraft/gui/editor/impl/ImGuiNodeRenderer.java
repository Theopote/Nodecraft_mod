package com.nodecraft.gui.editor.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.integration.ImGuiInputAdapter;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.nodes.utilities.assist.TagRelayNode;
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

    public void renderNodesDirect(ImDrawList drawList, ImVec2 canvasPos, NodeGraph graph,
                                  Map<UUID, NodePosition> nodePositions, Map<UUID, Map<String, ImVec2>> portScreenPositions,
                                  java.util.Set<UUID> selectedNodeIds) {

        if (graph == null) return;

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

        for (INode node : graph.getNodes()) {
            portCalculator.precalculatePortTextSizes(node);
            updateNodeDimensionsInPosition(node, nodePositions, canvasZoom, baseTextLineHeight, baseItemSpacingY);
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

    private void updateNodeDimensionsInPosition(INode node, Map<UUID, NodePosition> nodePositions, float canvasZoom,
                                                float baseTextLineHeight, float baseItemSpacingY) {
        UUID nodeId = node.getId();
        NodePosition pos = nodePositions.computeIfAbsent(nodeId, id -> new NodePosition(100, 100));

        if (isCompactRerouteNode(node)) {
            pos.setSize(NodeRenderConstants.REROUTE_NODE_WIDTH_UNSCALED, NodeRenderConstants.REROUTE_NODE_HEIGHT_UNSCALED);
            return;
        }

        String nodeDisplayName = node.getDisplayName();
        float unscaledTitleTextWidth = cache.getCachedTextWidth(nodeDisplayName);

        List<IPort> visibleInputPorts = getVisibleInputPorts(node);
        List<IPort> visibleOutputPorts = getVisibleOutputPorts(node);
        boolean hasInputPorts = !visibleInputPorts.isEmpty();
        boolean hasOutputPorts = !visibleOutputPorts.isEmpty();
        boolean hasAnyPorts = hasInputPorts || hasOutputPorts;

        float maxInputTextWidthUnscaled = 0;
        float maxOutputTextWidthUnscaled = 0;
        if (hasAnyPorts) {
            if (hasInputPorts) {
                for (IPort p : visibleInputPorts) {
                    maxInputTextWidthUnscaled = Math.max(maxInputTextWidthUnscaled, cache.getCachedTextWidth(p.getDisplayName()));
                }
            }
            if (hasOutputPorts) {
                for (IPort p : visibleOutputPorts) {
                    maxOutputTextWidthUnscaled = Math.max(maxOutputTextWidthUnscaled, cache.getCachedTextWidth(p.getDisplayName()));
                }
            }
        }

        float unscaledPortsContentWidth = 0;
        if (hasAnyPorts) {
            if (hasInputPorts) {
                unscaledPortsContentWidth += NodeRenderConstants.PORT_RADIUS_UNSCALED * 2 + NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING + maxInputTextWidthUnscaled;
            }
            if (hasOutputPorts) {
                if (hasInputPorts) {
                    unscaledPortsContentWidth += NodeRenderConstants.INTER_PORT_COLUMN_PADDING;
                }
                unscaledPortsContentWidth += maxOutputTextWidthUnscaled + NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING + NodeRenderConstants.PORT_RADIUS_UNSCALED * 2;
            }
        }

        float unscaledEffectiveContentWidth = Math.max(unscaledTitleTextWidth, unscaledPortsContentWidth);

        float customUIUnscaledHeight = 0;
        float customUIUnscaledRequiredWidth = 0;
        boolean hasCustomUI = customUIRenderer.hasCustomUI(node);

        if (hasCustomUI) {
            customUIUnscaledHeight = customUIRenderer.getCustomUIHeight(node);
            customUIUnscaledRequiredWidth = customUIRenderer.getMinRequiredUIWidth(node);
        }

        float finalUnscaledNodeWidth = Math.max(NodeRenderConstants.MIN_NODE_WIDTH_UNSCALED, unscaledEffectiveContentWidth + 2 * NodeRenderConstants.NODE_HORIZONTAL_PADDING);
        if (hasCustomUI && customUIUnscaledRequiredWidth > 0) {
            finalUnscaledNodeWidth = Math.max(finalUnscaledNodeWidth, customUIUnscaledRequiredWidth + 2 * NodeRenderConstants.NODE_HORIZONTAL_PADDING);
        }

        float unscaledNodeHeaderHeight = baseTextLineHeight + 2 * NodeRenderConstants.NODE_VERTICAL_PADDING;

        float unscaledPortsRegionHeight = 0;
        if (hasAnyPorts) {
            int numInputPorts = visibleInputPorts.size();
            int numOutputPorts = visibleOutputPorts.size();
            float inputPortsVisualHeight = (numInputPorts > 0) ? (numInputPorts * baseTextLineHeight + Math.max(0, numInputPorts - 1) * baseItemSpacingY * 0.8f) : 0;
            float outputPortsVisualHeight = (numOutputPorts > 0) ? (numOutputPorts * baseTextLineHeight + Math.max(0, numOutputPorts - 1) * baseItemSpacingY * 0.8f) : 0;
            unscaledPortsRegionHeight = Math.max(inputPortsVisualHeight, outputPortsVisualHeight);
        }

        float finalUnscaledNodeHeight = unscaledNodeHeaderHeight;
        if (unscaledPortsRegionHeight > 0) {
            finalUnscaledNodeHeight += unscaledPortsRegionHeight + NodeRenderConstants.NODE_VERTICAL_PADDING;
        }
        if (hasCustomUI && customUIUnscaledHeight > 0) {
            // 添加安全边距以防止自定义UI元素在缩放时被裁剪。
            // 由于 ImGui 控件的实际高度可能因样式参数（FramePadding、ItemSpacing 等）
            // 的缩放而略微超出计算的逻辑高度，这里增加一个小的安全边距。
            finalUnscaledNodeHeight += customUIUnscaledHeight + NodeRenderConstants.NODE_HORIZONTAL_PADDING;
        }

        pos.setSize(finalUnscaledNodeWidth, finalUnscaledNodeHeight);
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
        boolean compactRerouteNode = isCompactRerouteNode(node);

        if (!portScreenPositions.containsKey(nodeId)) {
            portScreenPositions.put(nodeId, new HashMap<>());
        }

        String category = NodeRenderConstants.getCategoryFromNodeTypeId(node.getTypeId());
        NodeRenderCache.CategoryColorCache colorCache = cache.getCategoryColorCache(category);
        int baseNodeColor = colorCache.baseColor;
        int nodeBgColor = colorCache.nodeBgColor;
        int borderColor = colorCache.borderColor;

        if (node instanceof TagRelayNode tagRelayNode) {
            int mappedColor = parseHexColorToU32(tagRelayNode.getResolvedColorHex(), baseNodeColor);
            baseNodeColor = mappedColor;
            nodeBgColor = cache.adjustBrightnessCached(mappedColor, 0.35f);
            borderColor = cache.adjustBrightnessCached(mappedColor, 1.35f);
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

        float scaledNodeVerticalPadding = NodeRenderConstants.NODE_VERTICAL_PADDING * canvasZoom;
        float scaledPortCircleToTextPadding = NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING * canvasZoom;
        float portRadiusScaled = NodeRenderConstants.PORT_RADIUS_UNSCALED * canvasZoom;
        float scaledTextLineHeight = baseTextLineHeight * canvasZoom;
        float scaledPortVerticalSpacing = baseItemSpacingY * canvasZoom * 0.8f;

        ImGui.pushID(nodeId.toString());

        float nodeCornerRadiusScaled = NodeRenderConstants.NODE_CORNER_RADIUS_UNSCALED * canvasZoom;
        float nodeBorderThicknessScaled = NodeRenderConstants.NODE_BORDER_THICKNESS_UNSCALED * canvasZoom;

        drawList.addRectFilled(nodeScreenX, nodeScreenY, nodeScreenX + finalNodeWidthScaled, nodeScreenY + finalNodeHeightScaled, nodeBgColor, nodeCornerRadiusScaled);
        if (!compactRerouteNode) {
            drawList.addRectFilled(nodeScreenX, nodeScreenY, nodeScreenX + finalNodeWidthScaled, nodeScreenY + (baseTextLineHeight + 2 * NodeRenderConstants.NODE_VERTICAL_PADDING) * canvasZoom, baseNodeColor, nodeCornerRadiusScaled, ImDrawFlags.RoundCornersTop);
        }
        drawList.addRect(nodeScreenX, nodeScreenY, nodeScreenX + finalNodeWidthScaled, nodeScreenY + finalNodeHeightScaled, borderColor, nodeCornerRadiusScaled, 0, nodeBorderThicknessScaled);

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

        if (!compactRerouteNode) {
            float titleTextWidthUnscaled = cache.getCachedTextWidth(node.getDisplayName());
            float titleX = nodeScreenX + (finalNodeWidthScaled - titleTextWidthUnscaled * canvasZoom) / 2;
            float titleY = nodeScreenY + ((baseTextLineHeight + 2 * NodeRenderConstants.NODE_VERTICAL_PADDING) * canvasZoom - scaledTextLineHeight) / 2;
            drawList.addText(font, baseFontSize * canvasZoom, titleX, titleY, textColor, node.getDisplayName());

            if (node instanceof TagRelayNode tagRelayNode) {
                renderTagRelayBadge(drawList, tagRelayNode, nodeScreenX, nodeScreenY, finalNodeWidthScaled,
                        baseTextLineHeight, baseFontSize, canvasZoom, textColor, font);
            }
        }

        List<IPort> visibleInputPorts = getVisibleInputPorts(node);
        List<IPort> visibleOutputPorts = getVisibleOutputPorts(node);
        boolean hasInputPorts = !visibleInputPorts.isEmpty();
        boolean hasOutputPorts = !visibleOutputPorts.isEmpty();
        boolean hasAnyPorts = hasInputPorts || hasOutputPorts;

        float nodeHeaderHeightScaled = (baseTextLineHeight + 2 * NodeRenderConstants.NODE_VERTICAL_PADDING) * canvasZoom;
        float portYOffset;
        if (compactRerouteNode) {
            portYOffset = nodeScreenY + (finalNodeHeightScaled - scaledTextLineHeight) / 2.0f;
        } else {
            portYOffset = nodeScreenY + nodeHeaderHeightScaled + scaledNodeVerticalPadding / 2;
        }

        if (hasAnyPorts) {
                float currentPortRadiusScaled = compactRerouteNode
                    ? NodeRenderConstants.REROUTE_PORT_RADIUS_UNSCALED * canvasZoom
                    : portRadiusScaled;
                renderNodePorts(drawList, node, visibleInputPorts, visibleOutputPorts, nodeId, nodeScreenX, finalNodeWidthScaled, portYOffset,
                    currentPortRadiusScaled, scaledTextLineHeight, scaledPortVerticalSpacing, scaledPortCircleToTextPadding,
                    font, baseFontSize, canvasZoom, textColor, navHighlightColor,
                    hoveredNodeId, hoveredPortId, isHoveredPortOutput, shouldHighlight, 
                    highlightSinValue, highlightCosValue, portScreenPositions,
                    !compactRerouteNode);
        }

        // 处理自定义UI
        boolean nodeHasCustomUI = customUIRenderer.hasCustomUI(node);
        if (nodeHasCustomUI && !compactRerouteNode) {
            // 在渲染自定义UI前重置交互状态
            customUIRenderer.resetCustomUIInteractionState();
            renderCustomUI(node, nodeId, nodeScreenX, nodeScreenY, finalNodeWidthScaled, 
                    baseTextLineHeight, canvasZoom, hasAnyPorts, baseItemSpacingY);
        }

        // 节点主体交互区域 (Invisible Button)
        ImGui.setCursorScreenPos(nodeScreenX, nodeScreenY);
        ImGui.invisibleButton("node_interaction_area_" + nodeId, finalNodeWidthScaled, finalNodeHeightScaled);

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

    private void renderNodePorts(ImDrawList drawList, INode node, List<IPort> visibleInputPorts, List<IPort> visibleOutputPorts, UUID nodeId, float nodeScreenX, float finalNodeWidthScaled,
                                 float portYOffset, float portRadiusScaled, float scaledTextLineHeight, float scaledPortVerticalSpacing,
                                 float scaledPortCircleToTextPadding, imgui.ImFont font, float baseFontSize, float canvasZoom,
                                 int textColor, int navHighlightColor, UUID hoveredNodeId, String hoveredPortId, 
                                 boolean isHoveredPortOutput, boolean shouldHighlight, float highlightSinValue, 
                                 float highlightCosValue, Map<UUID, Map<String, ImVec2>> portScreenPositions,
                                 boolean showPortLabels) {

        boolean hasInputPorts = !visibleInputPorts.isEmpty();
        boolean hasOutputPorts = !visibleOutputPorts.isEmpty();

        float maxInputTextWidthUnscaled = 0;
        float maxOutputTextWidthUnscaled = 0;
        if (hasInputPorts) {
            for (IPort p : visibleInputPorts) {
                maxInputTextWidthUnscaled = Math.max(maxInputTextWidthUnscaled, cache.getCachedTextWidth(p.getDisplayName()));
            }
        }
        if (hasOutputPorts) {
            for (IPort p : visibleOutputPorts) {
                maxOutputTextWidthUnscaled = Math.max(maxOutputTextWidthUnscaled, cache.getCachedTextWidth(p.getDisplayName()));
            }
        }

        float unscaledPortsContentWidth = 0;
        if (hasInputPorts) {
            unscaledPortsContentWidth += NodeRenderConstants.PORT_RADIUS_UNSCALED * 2 + NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING + maxInputTextWidthUnscaled;
        }
        if (hasOutputPorts) {
            if (hasInputPorts) {
                unscaledPortsContentWidth += NodeRenderConstants.INTER_PORT_COLUMN_PADDING;
            }
            unscaledPortsContentWidth += maxOutputTextWidthUnscaled + NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING + NodeRenderConstants.PORT_RADIUS_UNSCALED * 2;
        }

        float titleTextWidthUnscaled = cache.getCachedTextWidth(node.getDisplayName());
        float unscaledEffectiveContentWidthForPorts = Math.max(titleTextWidthUnscaled, unscaledPortsContentWidth);

        float unscaledAvailableWidthForInputText = 0;
        float unscaledAvailableWidthForOutputText = 0;

        if (hasInputPorts && hasOutputPorts) {
            float totalTextSpaceUnscaled = unscaledEffectiveContentWidthForPorts -
                    (2 * NodeRenderConstants.PORT_RADIUS_UNSCALED * 2) -
                    (2 * NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING) -
                    NodeRenderConstants.INTER_PORT_COLUMN_PADDING;

            if (maxInputTextWidthUnscaled + maxOutputTextWidthUnscaled > 1e-5) {
                unscaledAvailableWidthForInputText = totalTextSpaceUnscaled * (maxInputTextWidthUnscaled / (maxInputTextWidthUnscaled + maxOutputTextWidthUnscaled));
                unscaledAvailableWidthForOutputText = totalTextSpaceUnscaled * (maxOutputTextWidthUnscaled / (maxInputTextWidthUnscaled + maxOutputTextWidthUnscaled));
            } else {
                unscaledAvailableWidthForInputText = totalTextSpaceUnscaled / 2.0f;
                unscaledAvailableWidthForOutputText = totalTextSpaceUnscaled / 2.0f;
            }
        } else if (hasInputPorts) {
            unscaledAvailableWidthForInputText = unscaledEffectiveContentWidthForPorts - NodeRenderConstants.PORT_RADIUS_UNSCALED * 2 - NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING;
        } else if (hasOutputPorts) {
            unscaledAvailableWidthForOutputText = unscaledEffectiveContentWidthForPorts - NodeRenderConstants.PORT_RADIUS_UNSCALED * 2 - NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING;
        }

        // 渲染输入端口
        if (hasInputPorts) {
            renderInputPorts(drawList, visibleInputPorts, nodeId, nodeScreenX, portYOffset, portRadiusScaled,
                    scaledTextLineHeight, scaledPortVerticalSpacing, scaledPortCircleToTextPadding,
                    font, baseFontSize, canvasZoom, textColor, navHighlightColor,
                    hoveredNodeId, hoveredPortId, isHoveredPortOutput, shouldHighlight,
                    highlightSinValue, highlightCosValue, unscaledAvailableWidthForInputText, portScreenPositions, showPortLabels);
        }

        // 渲染输出端口
        if (hasOutputPorts) {
            renderOutputPorts(drawList, visibleOutputPorts, nodeId, nodeScreenX, finalNodeWidthScaled, portYOffset,
                    portRadiusScaled, scaledTextLineHeight, scaledPortVerticalSpacing, scaledPortCircleToTextPadding,
                    font, baseFontSize, canvasZoom, textColor, navHighlightColor,
                    hoveredNodeId, hoveredPortId, isHoveredPortOutput, shouldHighlight,
                    highlightSinValue, highlightCosValue, unscaledAvailableWidthForOutputText, portScreenPositions, showPortLabels);
        }
    }

    private void renderInputPorts(ImDrawList drawList, List<IPort> visibleInputPorts, UUID nodeId, float nodeScreenX, float portYOffset,
                                  float portRadiusScaled, float scaledTextLineHeight, float scaledPortVerticalSpacing,
                                  float scaledPortCircleToTextPadding, imgui.ImFont font, float baseFontSize, float canvasZoom,
                                  int textColor, int navHighlightColor, UUID hoveredNodeId, String hoveredPortId,
                                  boolean isHoveredPortOutput, boolean shouldHighlight, float highlightSinValue,
                                  float highlightCosValue, float unscaledAvailableWidthForInputText, 
                                  Map<UUID, Map<String, ImVec2>> portScreenPositions,
                                  boolean showPortLabels) {

        int numInputPorts = visibleInputPorts.size();
        for (int i = 0; i < numInputPorts; i++) {
            IPort port = visibleInputPorts.get(i);
            float currentPortY = portYOffset + i * (scaledTextLineHeight + scaledPortVerticalSpacing) + scaledTextLineHeight / 2;

            boolean isPortHighlighted = shouldHighlight && nodeId.equals(hoveredNodeId) && port.getId().equals(hoveredPortId) && !isHoveredPortOutput;
            int portColor = NodeRenderConstants.PORT_COLOR_INPUT;
            int portBorderColor = NodeRenderConstants.PORT_INPUT_BORDER_COLOR;

            if (isPortHighlighted) {
                portColor = NodeRenderConstants.PORT_COLOR_INPUT_HIGHLIGHT;
                portBorderColor = navHighlightColor;
                float pulseScale = 1.0f + 0.3f * highlightSinValue;
                float portOuterRadiusAnim = portRadiusScaled * pulseScale;

                float highlightRadius = portOuterRadiusAnim * 1.5f;
                int highlightColor = cache.adjustAlphaCached(portBorderColor, 0.5f + 0.3f * highlightCosValue);
                drawList.addCircle(nodeScreenX, currentPortY, highlightRadius, highlightColor, 12, NodeRenderConstants.PORT_BORDER_THICKNESS_UNSCALED * canvasZoom);
            }

            drawList.addCircleFilled(nodeScreenX, currentPortY, portRadiusScaled, portColor);
            drawList.addCircle(nodeScreenX, currentPortY, portRadiusScaled, portBorderColor, 12, NodeRenderConstants.PORT_BORDER_THICKNESS_UNSCALED * canvasZoom);

            if (showPortLabels) {
                String displayText = cache.truncateTextWithEllipsisOptimized(port.getDisplayName(), unscaledAvailableWidthForInputText);
                drawList.addText(font, baseFontSize * canvasZoom, nodeScreenX + portRadiusScaled + scaledPortCircleToTextPadding, currentPortY - scaledTextLineHeight / 2, textColor, displayText);
            }
            
            // 保存端口位置
            portScreenPositions.get(nodeId).put(port.getId(), new ImVec2(nodeScreenX, currentPortY));
        }
    }

    private void renderOutputPorts(ImDrawList drawList, List<IPort> visibleOutputPorts, UUID nodeId, float nodeScreenX, float finalNodeWidthScaled,
                                   float portYOffset, float portRadiusScaled, float scaledTextLineHeight, float scaledPortVerticalSpacing,
                                   float scaledPortCircleToTextPadding, imgui.ImFont font, float baseFontSize, float canvasZoom,
                                   int textColor, int navHighlightColor, UUID hoveredNodeId, String hoveredPortId,
                                   boolean isHoveredPortOutput, boolean shouldHighlight, float highlightSinValue,
                                   float highlightCosValue, float unscaledAvailableWidthForOutputText,
                                   Map<UUID, Map<String, ImVec2>> portScreenPositions,
                                   boolean showPortLabels) {

        float outputPortCircleX = nodeScreenX + finalNodeWidthScaled;
        int numOutputPorts = visibleOutputPorts.size();
        for (int i = 0; i < numOutputPorts; i++) {
            IPort port = visibleOutputPorts.get(i);
            float currentPortY = portYOffset + i * (scaledTextLineHeight + scaledPortVerticalSpacing) + scaledTextLineHeight / 2;

            String displayText = cache.truncateTextWithEllipsisOptimized(port.getDisplayName(), unscaledAvailableWidthForOutputText);
            float displayTextWidthUnscaled = cache.getCachedTextWidth(displayText);
            float outputTextX = outputPortCircleX - portRadiusScaled - scaledPortCircleToTextPadding - (displayTextWidthUnscaled * canvasZoom);

            boolean isPortHighlighted = shouldHighlight && nodeId.equals(hoveredNodeId) && port.getId().equals(hoveredPortId) && isHoveredPortOutput;
            int portColor = NodeRenderConstants.PORT_COLOR_OUTPUT;
            int portBorderColor = NodeRenderConstants.PORT_OUTPUT_BORDER_COLOR;

            if (isPortHighlighted) {
                portColor = NodeRenderConstants.PORT_COLOR_OUTPUT_HIGHLIGHT;
                portBorderColor = navHighlightColor;
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
            
            // 保存端口位置
            portScreenPositions.get(nodeId).put(port.getId(), new ImVec2(outputPortCircleX, currentPortY));
        }
    }

    private void renderCustomUI(INode node, UUID nodeId, float nodeScreenX, float nodeScreenY, float finalNodeWidthScaled,
                                float baseTextLineHeight, float canvasZoom, boolean hasAnyPorts, float baseItemSpacingY) {
        
        float customUIUnscaledHeight = customUIRenderer.getCustomUIHeight(node);
        float customUIUnscaledRequiredWidth = customUIRenderer.getMinRequiredUIWidth(node);

        if (customUIUnscaledHeight > 0) {
            float customUIStartY = nodeScreenY + (baseTextLineHeight + 2 * NodeRenderConstants.NODE_VERTICAL_PADDING) * canvasZoom;
            if (hasAnyPorts) {
                float unscaledPortsRegionHeight = getUnscaledPortsRegionHeight(node, baseTextLineHeight, baseItemSpacingY);
                customUIStartY += (unscaledPortsRegionHeight + NodeRenderConstants.NODE_VERTICAL_PADDING) * canvasZoom;
            }

            // 注意：在新的统一缩放架构中，我们传递逻辑尺寸但保持屏幕坐标
            // CustomUIRenderer 会在内部应用缩放变换
            float customUILogicalWidth = finalNodeWidthScaled / canvasZoom - 2 * NodeRenderConstants.NODE_HORIZONTAL_PADDING;
            float customUIScreenX = nodeScreenX + NodeRenderConstants.NODE_HORIZONTAL_PADDING * canvasZoom;

            ICustomUINode customUINode = null;
            boolean supportsDirectDrawing = false;
            
            if (node instanceof ICustomUINode) {
                customUINode = (ICustomUINode) node;
                supportsDirectDrawing = customUINode.supportsDirectDrawing();
            }

            if (supportsDirectDrawing) {
                try {
                    // 直接绘制模式仍然使用屏幕坐标（已缩放像素）
                    customUINode.renderCustomUIDirect(
                            ImGui.getWindowDrawList(),
                            customUIScreenX,
                            customUIStartY,
                            customUILogicalWidth * canvasZoom, // 直接绘制需要已缩放的尺寸
                            customUIUnscaledHeight * canvasZoom,
                            canvasZoom
                    );
                } catch (Exception e) {
                    System.err.println("Failed to render direct custom UI for node " + nodeId + ": " + e.getMessage());
                }
            } else {
                CustomUIRenderer.CustomUIRenderInfo info = new CustomUIRenderer.CustomUIRenderInfo(
                        node, customUINode, nodeId,
                        customUIScreenX, customUIStartY, // 使用屏幕坐标
                        customUILogicalWidth,            // 使用逻辑尺寸
                        customUIUnscaledHeight,
                        canvasZoom, supportsDirectDrawing
                );
                customUIRenderer.renderSingleCustomUIWithChildWindow(info);
            }
        }
    }

    private static float getUnscaledPortsRegionHeight(INode node, float baseTextLineHeight, float baseItemSpacingY) {
        float unscaledPortsRegionHeight;
        int numInputPorts = getVisibleInputPorts(node).size();
        int numOutputPorts = getVisibleOutputPorts(node).size();
        float inputPortsVisualHeight = (numInputPorts > 0) ? (numInputPorts * baseTextLineHeight + Math.max(0, numInputPorts - 1) * baseItemSpacingY * 0.8f) : 0;
        float outputPortsVisualHeight = (numOutputPorts > 0) ? (numOutputPorts * baseTextLineHeight + Math.max(0, numOutputPorts - 1) * baseItemSpacingY * 0.8f) : 0;
        unscaledPortsRegionHeight = Math.max(inputPortsVisualHeight, outputPortsVisualHeight);
        return unscaledPortsRegionHeight;
    }

    private static List<IPort> getVisibleInputPorts(INode node) {
        return filterViewerPorts(node.getInputPorts(), node);
    }

    private static boolean isCompactRerouteNode(INode node) {
        return node != null && NodeRenderConstants.REROUTE_NODE_TYPE_ID.equalsIgnoreCase(node.getTypeId());
    }

    private void renderTagRelayBadge(ImDrawList drawList, TagRelayNode node, float nodeScreenX, float nodeScreenY,
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

            if (node instanceof GeometryViewerNode) {
                isLegacyInput =
                        "input_box_geometry".equals(portId) ||
                        "input_cylinder_geometry".equals(portId) ||
                        "input_sphere_geometry".equals(portId) ||
                        "input_torus_geometry".equals(portId) ||
                        "input_color".equals(portId) ||
                        "input_transparency".equals(portId);
            } else if (node instanceof PreviewGeometryNode) {
                isLegacyInput =
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
                        "input_octahedron_geometry".equals(portId);
            } else if (node instanceof ApplyChangesNode) {
                isLegacyInput =
                        "input_box_geometry".equals(portId) ||
                        "input_cylinder_geometry".equals(portId) ||
                        "input_sphere_geometry".equals(portId) ||
                        "input_torus_geometry".equals(portId) ||
                        "input_preview_ids".equals(portId) ||
                        "input_notify".equals(portId);
            } else {
                return ports;
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
        ImGuiNodeInteraction interaction = editor.getInteraction();
        ImVec2 mousePos = ImGui.getIO().getMousePos();

        // 检查鼠标是否在节点矩形区域内（基于位置，不依赖 ImGui 的 isItemActive）
        boolean isMouseInNodeBounds = mousePos.x >= nodeScreenX && mousePos.x <= nodeScreenX + nodeWidthScaled &&
                                     mousePos.y >= nodeScreenY && mousePos.y <= nodeScreenY + nodeHeightScaled;

        // 检查自定义UI区域的交互状态
        boolean isCustomUIHoveredEmpty = false;
        boolean isCustomUIWidgetActive = false;
        if (nodeHasCustomUI) {
            java.util.UUID hoveredCustomUINodeId = customUIRenderer.getCustomUIHoveredEmptyNodeId();
            isCustomUIHoveredEmpty = hoveredCustomUINodeId != null && hoveredCustomUINodeId.equals(nodeId);
            isCustomUIWidgetActive = customUIRenderer.isCustomUIWidgetActive();
        }

        // invisibleButton 是否活跃（标准方式）
        boolean isInvisibleButtonActive = ImGui.isItemActive();

        // 综合判断：节点可拖动条件
        // 1. 标准方式：invisible button 活跃
        // 2. 增强方式：鼠标在节点区域内 + 自定义UI空白区域被悬停（无控件活跃）
        boolean canDragNode = isInvisibleButtonActive || (isMouseInNodeBounds && isCustomUIHoveredEmpty && !isCustomUIWidgetActive);

        // 检查是否是首次点击 (鼠标刚按下左键)
        if (ImGuiInputAdapter.isMouseClicked(ImGuiMouseButton.Left)) {
            // 首先检查鼠标是否在端口区域 - 如果是，优先处理端口连接
            boolean isMouseOnPort = interaction.isMouseOverAnyPortOfNode(nodeId, mousePos, editor.getPortScreenPositions());

            if (isMouseOnPort) { // 鼠标点击在端口上：尝试启动连接
                // 更新悬停端口状态 (如果之前没有更新的话)
                interaction.updateHoveredPort(mousePos, editor.getPortScreenPositions(), editor.getCurrentGraph());

                // 尝试启动连接创建
                UUID currentHoveredNodeId = interaction.getHoveredNodeId();
                String currentHoveredPortId = interaction.getHoveredPortId();
                boolean currentIsHoveredPortOutput = interaction.isHoveredPortOutput();

                if (currentHoveredNodeId != null && currentHoveredPortId != null) {
                    interaction.tryStartConnectionCreation(currentHoveredNodeId, currentHoveredPortId, currentIsHoveredPortOutput, editor.getPortScreenPositions());
                    NodeCraft.LOGGER.debug("从节点内部端口区域启动连接创建: NodeId={}, PortId={}", currentHoveredNodeId, currentHoveredPortId);
                }
            } else if (canDragNode && !isCustomUIWidgetActive) {
                // 鼠标点击在节点主体上（包括自定义UI空白区域）
                // 处理正常的节点选择和拖拽
                interaction.handleClickOnNodeBody(nodeId, ImGui.getIO().getKeyCtrl());

                // 立即尝试启动拖拽
                interaction.tryStartNodeDraggingFromNodeBody(nodeId);
                
                // 确保鼠标被捕获，防止窗口拖动
                ImGui.getIO().setWantCaptureMouse(true);
            }
        }

        // 拖动位移已在 ImGuiNodeEditor 渲染前统一处理，
        // 这里仅保留拖拽状态的生命周期控制（开始/结束）。
        boolean isDragging = interaction.isDraggingNode();
        if (ImGui.isItemDeactivated()) {
            // 如果这个 invisible button 刚刚失去激活状态 (鼠标抬起)
            interaction.clearNodeBodyActive(nodeId);
            // 此时拖拽状态应该由 ImGuiNodeInteraction 内部在鼠标释放时统一处理
            interaction.tryStopNodeDragging(); // 尝试停止拖拽
        }

        // 额外检查：如果正在拖动节点且鼠标释放，确保停止拖动
        if (interaction.isDraggingNode() && ImGuiInputAdapter.isMouseReleased(ImGuiMouseButton.Left)) {
            interaction.tryStopNodeDragging();
        }
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
