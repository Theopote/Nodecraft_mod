package com.nodecraft.gui.editor.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.graph.NodeGraph;

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

    public void drawGrid(ImDrawList drawList, ImVec2 canvasTopLeft, float canvasViewWidth, float canvasViewHeight) {
        NodeDrawingUtils.drawGrid(drawList, canvasTopLeft, canvasViewWidth, canvasViewHeight, editor);
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

        String nodeDisplayName = node.getDisplayName();
        float unscaledTitleTextWidth = cache.getCachedTextWidth(nodeDisplayName);

        boolean hasInputPorts = !node.getInputPorts().isEmpty();
        boolean hasOutputPorts = !node.getOutputPorts().isEmpty();
        boolean hasAnyPorts = hasInputPorts || hasOutputPorts;

        float maxInputTextWidthUnscaled = 0;
        float maxOutputTextWidthUnscaled = 0;
        if (hasAnyPorts) {
            if (hasInputPorts) {
                for (IPort p : node.getInputPorts()) {
                    maxInputTextWidthUnscaled = Math.max(maxInputTextWidthUnscaled, cache.getCachedTextWidth(p.getDisplayName()));
                }
            }
            if (hasOutputPorts) {
                for (IPort p : node.getOutputPorts()) {
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
            int numInputPorts = node.getInputPorts().size();
            int numOutputPorts = node.getOutputPorts().size();
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
            float customUISafetyMargin = 4.0f; // 逻辑单位的安全边距
            finalUnscaledNodeHeight += customUIUnscaledHeight + customUISafetyMargin + NodeRenderConstants.NODE_VERTICAL_PADDING;
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

        if (!portScreenPositions.containsKey(nodeId)) {
            portScreenPositions.put(nodeId, new HashMap<>());
        }

        String category = NodeRenderConstants.getCategoryFromNodeTypeId(node.getTypeId());
        NodeRenderCache.CategoryColorCache colorCache = cache.getCategoryColorCache(category);
        int baseNodeColor = colorCache.baseColor;
        int nodeBgColor = colorCache.nodeBgColor;
        int borderColor = colorCache.borderColor;

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

        float scaledNodeHorizontalPadding = NodeRenderConstants.NODE_HORIZONTAL_PADDING * canvasZoom;
        float scaledNodeVerticalPadding = NodeRenderConstants.NODE_VERTICAL_PADDING * canvasZoom;
        float scaledPortCircleToTextPadding = NodeRenderConstants.PORT_CIRCLE_TO_TEXT_PADDING * canvasZoom;
        float portRadiusScaled = NodeRenderConstants.PORT_RADIUS_UNSCALED * canvasZoom;
        float scaledTextLineHeight = baseTextLineHeight * canvasZoom;
        float scaledPortVerticalSpacing = baseItemSpacingY * canvasZoom * 0.8f;

        ImGui.pushID(nodeId.toString());

        float nodeCornerRadiusScaled = NodeRenderConstants.NODE_CORNER_RADIUS_UNSCALED * canvasZoom;
        float nodeBorderThicknessScaled = NodeRenderConstants.NODE_BORDER_THICKNESS_UNSCALED * canvasZoom;

        drawList.addRectFilled(nodeScreenX, nodeScreenY, nodeScreenX + finalNodeWidthScaled, nodeScreenY + finalNodeHeightScaled, nodeBgColor, nodeCornerRadiusScaled);
        drawList.addRectFilled(nodeScreenX, nodeScreenY, nodeScreenX + finalNodeWidthScaled, nodeScreenY + (baseTextLineHeight + 2 * NodeRenderConstants.NODE_VERTICAL_PADDING) * canvasZoom, baseNodeColor, nodeCornerRadiusScaled, ImDrawFlags.RoundCornersTop);
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
                               stripeColor, stripeSpacing, 1.0f * canvasZoom);
        }

        float titleTextWidthUnscaled = cache.getCachedTextWidth(node.getDisplayName());
        float titleX = nodeScreenX + (finalNodeWidthScaled - titleTextWidthUnscaled * canvasZoom) / 2;
        float titleY = nodeScreenY + ((baseTextLineHeight + 2 * NodeRenderConstants.NODE_VERTICAL_PADDING) * canvasZoom - scaledTextLineHeight) / 2;
        drawList.addText(font, baseFontSize * canvasZoom, titleX, titleY, textColor, node.getDisplayName());

        boolean hasInputPorts = !node.getInputPorts().isEmpty();
        boolean hasOutputPorts = !node.getOutputPorts().isEmpty();
        boolean hasAnyPorts = hasInputPorts || hasOutputPorts;

        float nodeHeaderHeightScaled = (baseTextLineHeight + 2 * NodeRenderConstants.NODE_VERTICAL_PADDING) * canvasZoom;
        float portYOffset = nodeScreenY + nodeHeaderHeightScaled + scaledNodeVerticalPadding / 2;

        if (hasAnyPorts) {
            renderNodePorts(drawList, node, nodeId, nodeScreenX, finalNodeWidthScaled, portYOffset,
                    portRadiusScaled, scaledTextLineHeight, scaledPortVerticalSpacing, scaledPortCircleToTextPadding,
                    font, baseFontSize, canvasZoom, textColor, navHighlightColor,
                    hoveredNodeId, hoveredPortId, isHoveredPortOutput, shouldHighlight, 
                    highlightSinValue, highlightCosValue, portScreenPositions);
        }

        // 处理自定义UI
        boolean nodeHasCustomUI = customUIRenderer.hasCustomUI(node);
        if (nodeHasCustomUI) {
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
            float selectionBorderThickness = 3.0f * canvasZoom;
            float selectionCornerRadius = 7.0f * canvasZoom;
            drawList.addRect(nodeScreenX - 2, nodeScreenY - 2,
                    nodeScreenX + finalNodeWidthScaled + 2, nodeScreenY + finalNodeHeightScaled + 2,
                    navHighlightColor, selectionCornerRadius, 0, nodeBorderThicknessScaled);
        }
        ImGui.popID();
    }

    private void renderNodePorts(ImDrawList drawList, INode node, UUID nodeId, float nodeScreenX, float finalNodeWidthScaled,
                                 float portYOffset, float portRadiusScaled, float scaledTextLineHeight, float scaledPortVerticalSpacing,
                                 float scaledPortCircleToTextPadding, imgui.ImFont font, float baseFontSize, float canvasZoom,
                                 int textColor, int navHighlightColor, UUID hoveredNodeId, String hoveredPortId, 
                                 boolean isHoveredPortOutput, boolean shouldHighlight, float highlightSinValue, 
                                 float highlightCosValue, Map<UUID, Map<String, ImVec2>> portScreenPositions) {

        boolean hasInputPorts = !node.getInputPorts().isEmpty();
        boolean hasOutputPorts = !node.getOutputPorts().isEmpty();

        float maxInputTextWidthUnscaled = 0;
        float maxOutputTextWidthUnscaled = 0;
        if (hasInputPorts) {
            for (IPort p : node.getInputPorts()) {
                maxInputTextWidthUnscaled = Math.max(maxInputTextWidthUnscaled, cache.getCachedTextWidth(p.getDisplayName()));
            }
        }
        if (hasOutputPorts) {
            for (IPort p : node.getOutputPorts()) {
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
            renderInputPorts(drawList, node, nodeId, nodeScreenX, portYOffset, portRadiusScaled, 
                    scaledTextLineHeight, scaledPortVerticalSpacing, scaledPortCircleToTextPadding,
                    font, baseFontSize, canvasZoom, textColor, navHighlightColor,
                    hoveredNodeId, hoveredPortId, isHoveredPortOutput, shouldHighlight,
                    highlightSinValue, highlightCosValue, unscaledAvailableWidthForInputText, portScreenPositions);
        }

        // 渲染输出端口
        if (hasOutputPorts) {
            renderOutputPorts(drawList, node, nodeId, nodeScreenX, finalNodeWidthScaled, portYOffset, 
                    portRadiusScaled, scaledTextLineHeight, scaledPortVerticalSpacing, scaledPortCircleToTextPadding,
                    font, baseFontSize, canvasZoom, textColor, navHighlightColor,
                    hoveredNodeId, hoveredPortId, isHoveredPortOutput, shouldHighlight,
                    highlightSinValue, highlightCosValue, unscaledAvailableWidthForOutputText, portScreenPositions);
        }
    }

    private void renderInputPorts(ImDrawList drawList, INode node, UUID nodeId, float nodeScreenX, float portYOffset,
                                  float portRadiusScaled, float scaledTextLineHeight, float scaledPortVerticalSpacing,
                                  float scaledPortCircleToTextPadding, imgui.ImFont font, float baseFontSize, float canvasZoom,
                                  int textColor, int navHighlightColor, UUID hoveredNodeId, String hoveredPortId,
                                  boolean isHoveredPortOutput, boolean shouldHighlight, float highlightSinValue,
                                  float highlightCosValue, float unscaledAvailableWidthForInputText, 
                                  Map<UUID, Map<String, ImVec2>> portScreenPositions) {

        int numInputPorts = node.getInputPorts().size();
        for (int i = 0; i < numInputPorts; i++) {
            IPort port = node.getInputPorts().get(i);
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

            String displayText = cache.truncateTextWithEllipsisOptimized(port.getDisplayName(), unscaledAvailableWidthForInputText);
            drawList.addText(font, baseFontSize * canvasZoom, nodeScreenX + portRadiusScaled + scaledPortCircleToTextPadding, currentPortY - scaledTextLineHeight / 2, textColor, displayText);
            
            // 保存端口位置
            portScreenPositions.get(nodeId).put(port.getId(), new ImVec2(nodeScreenX, currentPortY));
        }
    }

    private void renderOutputPorts(ImDrawList drawList, INode node, UUID nodeId, float nodeScreenX, float finalNodeWidthScaled,
                                   float portYOffset, float portRadiusScaled, float scaledTextLineHeight, float scaledPortVerticalSpacing,
                                   float scaledPortCircleToTextPadding, imgui.ImFont font, float baseFontSize, float canvasZoom,
                                   int textColor, int navHighlightColor, UUID hoveredNodeId, String hoveredPortId,
                                   boolean isHoveredPortOutput, boolean shouldHighlight, float highlightSinValue,
                                   float highlightCosValue, float unscaledAvailableWidthForOutputText,
                                   Map<UUID, Map<String, ImVec2>> portScreenPositions) {

        float outputPortCircleX = nodeScreenX + finalNodeWidthScaled;
        int numOutputPorts = node.getOutputPorts().size();
        for (int i = 0; i < numOutputPorts; i++) {
            IPort port = node.getOutputPorts().get(i);
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

            drawList.addText(font, baseFontSize * canvasZoom, outputTextX, currentPortY - scaledTextLineHeight / 2, textColor, displayText);
            
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
        int numInputPorts = node.getInputPorts().size();
        int numOutputPorts = node.getOutputPorts().size();
        float inputPortsVisualHeight = (numInputPorts > 0) ? (numInputPorts * baseTextLineHeight + Math.max(0, numInputPorts - 1) * baseItemSpacingY * 0.8f) : 0;
        float outputPortsVisualHeight = (numOutputPorts > 0) ? (numOutputPorts * baseTextLineHeight + Math.max(0, numOutputPorts - 1) * baseItemSpacingY * 0.8f) : 0;
        unscaledPortsRegionHeight = Math.max(inputPortsVisualHeight, outputPortsVisualHeight);
        return unscaledPortsRegionHeight;
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
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
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

        // 如果节点处于可拖动状态并且交互状态为 DRAGGING_NODE，则持续移动节点
        // 注意：一旦拖动已经开始（isDraggingNode()==true），不再检查 isCustomUIWidgetActive，
        // 因为拖动过程中鼠标经过UI控件（如滑块）会导致它们被激活，从而错误地阻断拖动
        boolean isDragging = interaction.isDraggingNode();
        if ((isInvisibleButtonActive || (isMouseInNodeBounds && (isDragging || !isCustomUIWidgetActive))) 
                && isDragging && ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            // 确保当前活跃的节点是正在拖动的节点，或者拖动的节点是选中集的一部分
            if (Objects.equals(interaction.getDraggingNodeId(), nodeId) || selectedNodeIds.contains(nodeId)) {
                float deltaX = ImGui.getIO().getMouseDelta().x / editor.getCanvasZoom();
                float deltaY = ImGui.getIO().getMouseDelta().y / editor.getCanvasZoom();

                if (deltaX != 0 || deltaY != 0) {
                    // 移动所有选中的节点
                    for (UUID selectedNodeId : selectedNodeIds) {
                        NodePosition selectedNodePos = editor.getNodePositions().get(selectedNodeId);
                        if (selectedNodePos != null) {
                            selectedNodePos.x += deltaX;
                            selectedNodePos.y += deltaY;
                        }
                    }
                    editor.getNodeIO().markDirty(); // 标记编辑器为脏
                }
            }
        } else if (ImGui.isItemDeactivated()) {
            // 如果这个 invisible button 刚刚失去激活状态 (鼠标抬起)
            interaction.clearNodeBodyActive(nodeId);
            // 此时拖拽状态应该由 ImGuiNodeInteraction 内部在鼠标释放时统一处理
            interaction.tryStopNodeDragging(); // 尝试停止拖拽
        }

        // 额外检查：如果正在拖动节点且鼠标释放，确保停止拖动
        if (interaction.isDraggingNode() && ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
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

    public void drawConnectionPreview(ImDrawList drawList, ImVec2 startPos, float canvasZoom, boolean isFromOutput) {
        connectionRenderer.drawConnectionPreview(drawList, startPos, canvasZoom, isFromOutput);
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
        float totalDashCycle = dashLength + gapLength;
        
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