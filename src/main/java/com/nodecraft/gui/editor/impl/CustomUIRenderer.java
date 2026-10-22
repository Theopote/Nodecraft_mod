package com.nodecraft.gui.editor.impl;

import java.util.UUID;
import com.nodecraft.nodesystem.api.INode;
import imgui.ImGui;
import com.nodecraft.core.NodeCraft;

/**
 * 自定义UI渲染器
 * 专门处理节点的自定义UI渲染逻辑
 */
public class CustomUIRenderer {
    
    private final ICanvasEditor editor;

    // === 自定义UI交互状态追踪（帧级累积，多节点画布安全） ===
    private java.util.UUID lastCustomUIHoveredNodeId = null;
    private java.util.UUID activeWidgetNodeId = null;
    private boolean lastCustomUIWasHoveredEmpty = false;
    private boolean lastCustomUIHasActiveWidget = false;
    private boolean lastCustomUIHasHoveredWidget = false;
    
    public CustomUIRenderer(ICanvasEditor editor) {
        this.editor = editor;
    }

    /**
     * 获取最近一次渲染中，鼠标悬停在自定义UI空白区域的节点ID。
     * 如果鼠标不在任何自定义UI空白区域，返回 null。
     */
    public java.util.UUID getCustomUIHoveredEmptyNodeId() {
        return lastCustomUIWasHoveredEmpty ? lastCustomUIHoveredNodeId : null;
    }

    /**
     * 检查最近渲染的自定义UI是否有活跃的控件（滑块、输入框等正在被交互）。
     */
    public boolean isCustomUIWidgetActive() {
        return lastCustomUIHasActiveWidget;
    }

    /** Node that owns the active custom UI widget this frame, if any. */
    public java.util.UUID getActiveWidgetNodeId() {
        return activeWidgetNodeId;
    }

    /**
     * 重置自定义UI交互状态（每帧开始前调用一次，勿在每个节点绘制前重复调用）。
     */
    public void beginInteractionFrame() {
        lastCustomUIHoveredNodeId = null;
        activeWidgetNodeId = null;
        lastCustomUIWasHoveredEmpty = false;
        lastCustomUIHasActiveWidget = false;
        lastCustomUIHasHoveredWidget = false;
    }

    /** @deprecated use {@link #beginInteractionFrame()} */
    @Deprecated
    public void resetCustomUIInteractionState() {
        beginInteractionFrame();
    }

    /**
     * 鼠标是否悬停在指定节点自定义 UI 的控件上（非空白区域）。
     */
    public boolean isCustomUIWidgetHovered(UUID nodeId) {
        return nodeId != null && nodeId.equals(lastCustomUIHoveredNodeId) && lastCustomUIHasHoveredWidget;
    }

    /**
     * 自定义UI渲染信息
     */
    public static class CustomUIRenderInfo {
        public final INode node;
        public final ICustomUINode customUINode;
        public final UUID nodeId;
        public final float screenX;
        public final float screenY;
        public final float width;
        public final float height;
        public final float zoom;
        public final boolean supportsDirectDrawing;

        public CustomUIRenderInfo(INode node, ICustomUINode customUINode, UUID nodeId,
                           float screenX, float screenY, float width, float height,
                           float zoom, boolean supportsDirectDrawing) {
            this.node = node;
            this.customUINode = customUINode;
            this.nodeId = nodeId;
            this.screenX = screenX;
            this.screenY = screenY;
            this.width = width;
            this.height = height;
            this.zoom = zoom;
            this.supportsDirectDrawing = supportsDirectDrawing;
        }
    }

    /**
     * 统一入口：ImGui 控件路径或直接绘制路径，并累积帧级交互状态。
     */
    public void renderNodeCustomUI(CustomUIRenderInfo info) {
        if (info.customUINode != null && info.supportsDirectDrawing) {
            renderDirectCustomUI(info);
            return;
        }
        renderSingleCustomUIWithChildWindow(info);
    }

    private void renderDirectCustomUI(CustomUIRenderInfo info) {
        float scaledWidth = info.width * info.zoom;
        float scaledHeight = info.height * info.zoom;
        try {
            boolean uiInteracted = info.customUINode.renderCustomUIDirect(
                    ImGui.getWindowDrawList(),
                    info.screenX,
                    info.screenY,
                    scaledWidth,
                    scaledHeight,
                    info.zoom);
            recordDirectDrawInteraction(info, uiInteracted);
        } catch (Exception e) {
            NodeCraft.LOGGER.warn("Failed to render direct custom UI for node {}", info.nodeId, e);
        }
    }

    /**
     * 渲染单个节点的自定义UI（ImGui 控件）
     */
    public void renderSingleCustomUIWithChildWindow(CustomUIRenderInfo info) {
        try {
            ICustomUINode.ContentBounds bounds = null;
            if (info.customUINode != null) {
                bounds = info.customUINode.getContentBounds(info.zoom);
            }

            // 将逻辑尺寸转换为缩放后的像素尺寸
            float scaledWidth = info.width * info.zoom;
            float scaledHeight = info.height * info.zoom;
            
            // 增加安全边距以防止内容被裁剪
            // ImGui 控件的实际渲染尺寸可能因样式属性缩放而略大于计算值
            float safetyMarginPixels = 4.0f * info.zoom;
            float safeWidth = scaledWidth + safetyMarginPixels;
            float safeHeight = scaledHeight + safetyMarginPixels;

            if (bounds != null) {
                safeWidth = Math.max(scaledWidth, bounds.minWidth);
                safeHeight = Math.max(scaledHeight, bounds.minHeight);

                float maxSafeWidth = scaledWidth * 2.0f;
                float maxSafeHeight = scaledHeight * 2.0f;
                safeWidth = Math.min(safeWidth, maxSafeWidth);
                safeHeight = Math.min(safeHeight, maxSafeHeight);
            }

            // === 画布级别缩放变换 ===
            // 保存原始样式状态
            float originalFramePaddingX = ImGui.getStyle().getFramePaddingX();
            float originalFramePaddingY = ImGui.getStyle().getFramePaddingY();
            float originalItemSpacingX = ImGui.getStyle().getItemSpacingX();
            float originalItemSpacingY = ImGui.getStyle().getItemSpacingY();
            float originalIndentSpacing = ImGui.getStyle().getIndentSpacing();
            float originalFrameBorderSize = ImGui.getStyle().getFrameBorderSize();
            float originalFrameRounding = ImGui.getStyle().getFrameRounding();
            float originalGrabRounding = ImGui.getStyle().getGrabRounding();
            float originalScrollbarSize = ImGui.getStyle().getScrollbarSize();
            float originalScrollbarRounding = ImGui.getStyle().getScrollbarRounding();
            float originalGrabMinSize = ImGui.getStyle().getGrabMinSize();
            float originalWindowPaddingX = ImGui.getStyle().getWindowPaddingX();
            float originalWindowPaddingY = ImGui.getStyle().getWindowPaddingY();
            float originalItemInnerSpacingX = ImGui.getStyle().getItemInnerSpacingX();
            float originalItemInnerSpacingY = ImGui.getStyle().getItemInnerSpacingY();
            
            // 应用统一的缩放变换
            // 这样 ImGui 控件的所有部分（边框、内边距、交互区域等）都会正确缩放
            float zoom = info.zoom;
            // 统一做紧凑化，并通过 y 向 frame padding 归一化常见控件高度。
            float compactFramePaddingFactor = 0.70f;
            float compactItemSpacingFactor = 0.52f;
            float compactInnerSpacingFactor = 0.68f;

            float framePadX = originalFramePaddingX * zoom * compactFramePaddingFactor;
            float framePadY = originalFramePaddingY * zoom * compactFramePaddingFactor;
            // 限制垂直 padding 区间，减少按钮/滑条/输入框高度差异。
            framePadY = Math.max(1.0f * zoom, Math.min(framePadY, 2.8f * zoom));

            ImGui.getStyle().setFramePadding(framePadX, framePadY);
            ImGui.getStyle().setItemSpacing(
                    originalItemSpacingX * zoom * compactItemSpacingFactor,
                    originalItemSpacingY * zoom * compactItemSpacingFactor);
            ImGui.getStyle().setIndentSpacing(originalIndentSpacing * zoom);
            ImGui.getStyle().setFrameBorderSize(originalFrameBorderSize * zoom);
            ImGui.getStyle().setFrameRounding(originalFrameRounding * zoom);
            ImGui.getStyle().setGrabRounding(originalGrabRounding * zoom);
            ImGui.getStyle().setScrollbarSize(originalScrollbarSize * zoom);
            ImGui.getStyle().setScrollbarRounding(originalScrollbarRounding * zoom);
            ImGui.getStyle().setGrabMinSize(Math.max(8.0f * zoom, originalGrabMinSize * zoom * 0.82f));
            // 保持窗口内边距为 0，确保可用渲染区域与节点内容区一致
            ImGui.getStyle().setWindowPadding(0, 0);
            ImGui.getStyle().setItemInnerSpacing(
                    originalItemInnerSpacingX * zoom * compactInnerSpacingFactor,
                    originalItemInnerSpacingY * zoom * compactInnerSpacingFactor);

            try {
                // 在当前窗口内直接渲染（不再使用子窗口），避免重叠节点时子窗口层级覆盖问题
                // 不使用强制裁剪，避免节点移动时自定义UI被意外截断
                float clipMinX = info.screenX;
                float clipMinY = info.screenY;
                float clipMaxX = info.screenX + safeWidth;
                float clipMaxY = info.screenY + safeHeight;
                ImGui.setCursorScreenPos(info.screenX, info.screenY);
                ImGui.beginGroup();
                imgui.ImGui imguiInstance = new imgui.ImGui();

                // 必须用 ImGui.pushClipRect（而非 DrawList），才会裁剪鼠标命中测试。
                // DrawList 版只裁剪绘制：控件垂直略溢出时，节点外同水平范围内仍会点到按钮。
                float clipPadding = Math.max(2.0f * info.zoom, 2.0f);
                ImGui.pushClipRect(
                    clipMinX,
                    clipMinY,
                    clipMaxX,
                    clipMaxY + clipPadding,
                    true);
                boolean widgetHoveredBeforeGroupEnd = false;
                boolean widgetActiveBeforeGroupEnd = false;
                try {
                    if (info.customUINode != null) {
                        try {
                            // 关键：使用窗口级字体缩放，让控件文字与控件尺寸同步缩放。
                            // 仅在当前自定义UI渲染段内生效，结束后恢复为 1.0。
                            imguiInstance.setWindowFontScale(zoom);
                            info.customUINode.renderCustomUI(info.width, info.height, zoom);
                        } catch (Exception e) {
                            NodeCraft.LOGGER.error("自定义UI渲染失败 (节点: {}): {}", info.nodeId, e.getMessage(), e);
                        } finally {
                            imguiInstance.setWindowFontScale(1.0f);
                        }
                    }
                    // 必须在 endGroup 之前采样：EndGroup 会把整块区域合成一个可悬停 Item，
                    // 若在其后用 isAnyItemHovered，空白区也会被误判为「悬停在控件上」，导致无法点选节点。
                    widgetHoveredBeforeGroupEnd = ImGui.isAnyItemHovered();
                    widgetActiveBeforeGroupEnd = ImGui.isAnyItemActive();
                } finally {
                    ImGui.popClipRect();
                }
                ImGui.endGroup();

                boolean regionHovered = ImGui.isMouseHoveringRect(
                        info.screenX,
                        info.screenY,
                        info.screenX + scaledWidth,
                        info.screenY + scaledHeight,
                        true);
                ImGuiNodeInteraction interaction = editor.getInteraction();
                boolean isNodeBeingDragged = interaction != null && interaction.isDraggingNode()
                        && info.nodeId.equals(interaction.getDraggingNodeId());
                recordImGuiWidgetInteraction(
                        info.nodeId,
                        regionHovered,
                        widgetHoveredBeforeGroupEnd,
                        widgetActiveBeforeGroupEnd,
                        isNodeBeingDragged);

            } finally {
                // 恢复原始样式状态
                ImGui.getStyle().setFramePadding(originalFramePaddingX, originalFramePaddingY);
                ImGui.getStyle().setItemSpacing(originalItemSpacingX, originalItemSpacingY);
                ImGui.getStyle().setIndentSpacing(originalIndentSpacing);
                ImGui.getStyle().setFrameBorderSize(originalFrameBorderSize);
                ImGui.getStyle().setFrameRounding(originalFrameRounding);
                ImGui.getStyle().setGrabRounding(originalGrabRounding);
                ImGui.getStyle().setScrollbarSize(originalScrollbarSize);
                ImGui.getStyle().setScrollbarRounding(originalScrollbarRounding);
                ImGui.getStyle().setGrabMinSize(originalGrabMinSize);
                ImGui.getStyle().setWindowPadding(originalWindowPaddingX, originalWindowPaddingY);
                ImGui.getStyle().setItemInnerSpacing(originalItemInnerSpacingX, originalItemInnerSpacingY);
            }

        } catch (Exception e) {
            NodeCraft.LOGGER.error("自定义UI子窗口创建失败 (节点: {}): {}", info.nodeId, e.getMessage(), e);
        }
    }

    /**
     * 使用防裁剪模式渲染
     */
    private boolean renderWithAntiClippingMode(CustomUIRenderInfo info,
                                               ICustomUINode.ViewPortRenderInfo renderInfo,
                                               ICustomUINode.ContentBounds bounds) {
        boolean uiInteracted = false;

        try {
            imgui.ImVec2 originalClipMin = new imgui.ImVec2();
            imgui.ImVec2 originalClipMax = new imgui.ImVec2();
            ImGui.getWindowDrawList().getClipRectMin(originalClipMin);
            ImGui.getWindowDrawList().getClipRectMax(originalClipMax);

            float expandedClipMinX = Math.min(originalClipMin.x, info.screenX - bounds.marginLeft);
            float expandedClipMinY = Math.min(originalClipMin.y, info.screenY - bounds.marginTop);
            float expandedClipMaxX = Math.max(originalClipMax.x, info.screenX + info.width + bounds.marginRight);
            float expandedClipMaxY = Math.max(originalClipMax.y, info.screenY + info.height + bounds.marginBottom);

            ImGui.pushClipRect(
                    expandedClipMinX, expandedClipMinY,
                    expandedClipMaxX, expandedClipMaxY,
                    false
            );
            try {
                uiInteracted = info.customUINode.renderWithViewPortAwareness(
                        renderInfo, bounds.minWidth, bounds.minHeight);
            } finally {
                ImGui.popClipRect();
            }

        } catch (Exception e) {
            NodeCraft.LOGGER.warn("防裁剪渲染失败", e);
            try {
                uiInteracted = info.customUINode.renderCustomUI(
                        info.width, info.height, info.zoom);
            } catch (Exception fallbackError) {
                NodeCraft.LOGGER.warn("回退渲染也失败", fallbackError);
            }
        }
        return uiInteracted;
    }

    /**
     * 累积 ImGui 控件路径的交互状态；同一帧内多个自定义 UI 节点不会互相覆盖 active 状态。
     */
    void recordImGuiWidgetInteraction(
            UUID nodeId,
            boolean regionHovered,
            boolean widgetHovered,
            boolean widgetActive,
            boolean nodeBeingDragged) {
        if (regionHovered || nodeBeingDragged) {
            ImGui.getIO().setWantCaptureMouse(true);
        }
        mergeInteractionSnapshot(nodeId, regionHovered, widgetHovered, widgetActive, nodeBeingDragged);
    }

    /**
     * 纯合并逻辑（无 ImGui 依赖），供多节点帧级状态累积与单元测试使用。
     */
    void mergeInteractionSnapshot(
            UUID nodeId,
            boolean regionHovered,
            boolean widgetHovered,
            boolean widgetActive,
            boolean nodeBeingDragged) {
        if (nodeBeingDragged) {
            if (!lastCustomUIHasActiveWidget) {
                lastCustomUIHoveredNodeId = nodeId;
                lastCustomUIWasHoveredEmpty = true;
                lastCustomUIHasHoveredWidget = false;
            }
            return;
        }
        if (!regionHovered) {
            return;
        }

        if (widgetActive) {
            lastCustomUIHasActiveWidget = true;
            activeWidgetNodeId = nodeId;
            lastCustomUIHoveredNodeId = nodeId;
            lastCustomUIWasHoveredEmpty = false;
            lastCustomUIHasHoveredWidget = true;
            return;
        }

        if (lastCustomUIHasActiveWidget) {
            return;
        }

        if (!widgetHovered) {
            lastCustomUIHoveredNodeId = nodeId;
            lastCustomUIWasHoveredEmpty = true;
            lastCustomUIHasHoveredWidget = false;
        } else {
            lastCustomUIHoveredNodeId = nodeId;
            lastCustomUIWasHoveredEmpty = false;
            lastCustomUIHasHoveredWidget = true;
        }
    }

    /**
     * 直接绘制路径：记录区域悬停与交互，供节点拖拽拦截使用。
     */
    void recordDirectDrawInteraction(CustomUIRenderInfo info, boolean uiInteracted) {
        float scaledWidth = info.width * info.zoom;
        float scaledHeight = info.height * info.zoom;
        boolean regionHovered = ImGui.isMouseHoveringRect(
                info.screenX,
                info.screenY,
                info.screenX + scaledWidth,
                info.screenY + scaledHeight,
                true);
        ImGuiNodeInteraction interaction = editor.getInteraction();
        boolean isNodeBeingDragged = interaction != null && interaction.isDraggingNode()
                && info.nodeId.equals(interaction.getDraggingNodeId());

        if (isNodeBeingDragged) {
            recordImGuiWidgetInteraction(info.nodeId, true, false, false, true);
            return;
        }
        if (!regionHovered) {
            return;
        }

        ImGui.getIO().setWantCaptureMouse(true);
        if (uiInteracted) {
            mergeInteractionSnapshot(info.nodeId, true, true, true, false);
            return;
        }
        mergeInteractionSnapshot(info.nodeId, true, false, false, false);
    }

    /**
     * 检查节点是否有自定义UI（仅识别 {@link ICustomUINode}）。
     */
    public boolean hasCustomUI(INode node) {
        return node instanceof ICustomUINode customUINode && customUINode.hasCustomUI();
    }

    /**
     * 获取自定义UI的高度（仅识别 {@link ICustomUINode}）。
     */
    public float getCustomUIHeight(INode node) {
        if (node instanceof ICustomUINode customUINode) {
            return customUINode.getCustomUIHeight();
        }
        return 0;
    }

    /**
     * 获取自定义UI的最小宽度（仅识别 {@link ICustomUINode}）。
     */
    public float getMinRequiredUIWidth(INode node) {
        if (node instanceof ICustomUINode customUINode) {
            return customUINode.getMinRequiredUIWidth();
        }
        return 0;
    }
}
