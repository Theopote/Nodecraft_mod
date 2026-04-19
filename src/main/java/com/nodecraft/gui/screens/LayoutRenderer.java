package com.nodecraft.gui.screens;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.CanvasComponent;
import com.nodecraft.gui.components.EditorComponent;
import com.nodecraft.gui.components.PropertyPanelComponent;
import com.nodecraft.gui.editor.integration.ImGuiInputAdapter;
import com.nodecraft.gui.layout.LayoutDimensions;
import com.nodecraft.gui.layout.LayoutManager;
import com.nodecraft.gui.layout.LayoutConfig;

import java.util.Map;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiPopupFlags;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.gui.DrawContext;

/**
 * 负责根据 LayoutManager 计算的布局信息渲染组件。
 */
public class LayoutRenderer {
    private final LayoutManager layoutManager;
    private final ComponentManager componentManager;
    private LayoutConfig layoutConfig;
    private final boolean showMenuBar;

    // 添加标志位来控制是否突出显示画布
    private boolean emphasizeCanvas = true;

    // 拖拽分隔线相关变量
    private boolean isDraggingLeftSplitter = false;  // 左侧分割线拖拽状态
    private boolean isDraggingRightSplitter = false; // 右侧分割线拖拽状态
    private float dragStartX = 0;                    // 拖拽开始位置
    private float initialNodePanelRatio = 0;         // 拖拽开始时的节点面板比例
    private float initialPropertyPanelRatio = 0;     // 拖拽开始时的属性面板比例
    private float initialCanvasRatio = 0;            // 拖拽开始时的画布比例
    private float lastContentWidth = 0;              // 上一次内容区域宽度，用于计算拖拽比例

    // 拖拽敏感区域宽度
    private static final float SPLITTER_HOVER_WIDTH = 6f; // 减小敏感区域宽度
    // 鼠标光标类型标记
    private boolean isHoveringLeftSplitter = false;
    private boolean isHoveringRightSplitter = false;
    // 调试标志 - 关闭调试输出
    private boolean debugSplitter = false;

    public LayoutRenderer(
            LayoutManager layoutManager,
            ComponentManager componentManager,
            LayoutConfig layoutConfig,
            boolean showMenuBar) {
        this.layoutManager = layoutManager;
        this.componentManager = componentManager;
        this.layoutConfig = layoutConfig;
        this.showMenuBar = showMenuBar;
    }

    /**
     * 设置是否突出显示画布
     * @param emphasize 是否突出显示
     */
    public void setEmphasizeCanvas(boolean emphasize) {
        this.emphasizeCanvas = emphasize;
    }

    /**
     * 渲染主窗口内的组件布局。
     * @param vanillaContext DrawContext
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param delta 渲染 delta
     */
    public void render(DrawContext vanillaContext, int mouseX, int mouseY, float delta) {
        renderImGuiOnly(delta);
    }

    public void renderImGuiOnly(float delta) {
        try {
            // 获取可用内容区域
            final float contentStartX = ImGui.getWindowContentRegionMinX();
            final float contentStartY = ImGui.getWindowContentRegionMinY();
            final float contentWidth = ImGui.getContentRegionAvailX();
            final float contentHeight = ImGui.getContentRegionAvailY();

            // 可用于布局计算的区域 (检查确保不为负)
            final float effectiveWidth = Math.max(0f, contentWidth);
            final float effectiveHeight = Math.max(0f, contentHeight);

            // 更新记录的内容宽度
            lastContentWidth = effectiveWidth;

            // 调用 LayoutManager 计算布局
            layoutManager.calculateLayout(effectiveWidth, effectiveHeight, contentStartX, contentStartY, layoutConfig, showMenuBar);
            logDetachedLayoutState("layout", contentStartX, contentStartY, effectiveWidth, effectiveHeight);

            // 检查组件管理器
            if (componentManager == null) {
                NodeCraft.LOGGER.error("ComponentManager is null in LayoutRenderer");
                return;
            }

            // 获取所有注册的组件
            Map<String, EditorComponent> componentsToRender = layoutManager.getRegisteredComponents();

            // 如果正在拖拽或悬停在分隔线上，阻止窗口移动
            boolean isDragging = isDraggingSplitter();
            boolean isHovering = isHoveringSplitter();

            if (isDragging) {
                // 在拖拽过程中强制捕获所有鼠标输入

                // 防止触发任何其他鼠标事件
                ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);

                if (debugSplitter) {
                    NodeCraft.LOGGER.debug("拖拽模式激活：已禁用窗口移动");
                }
            }

            // 处理分隔线拖拽（在组件渲染前处理拖拽，确保布局正确）
            handleSplitterDragging(contentStartX, contentWidth);

            // 拖拽时也保持完整渲染，避免内容临时消失。
            renderNonCanvasComponents(componentsToRender, delta);

            // 最后渲染画布组件，使其始终显示在最上层
            renderCanvasComponent(delta);

        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染组件布局时出错", e);
        }
    }

    /**
     * 处理分隔线拖拽逻辑
     * @param contentStartX 内容区起始X坐标
     * @param contentWidth 内容区宽度
     */
    private void handleSplitterDragging(float contentStartX, float contentWidth) {
        if (contentWidth <= 0) return;

        // 获取鼠标状态
        ImGuiIO io = ImGui.getIO();
        final float mouseX = io.getMousePosX();
        final float mouseY = io.getMousePosY();

        // 获取ImGui窗口位置，用于计算相对坐标
        final float windowX = ImGui.getWindowPosX();
        final float windowY = ImGui.getWindowPosY();

        // 检查鼠标是否在ImGui窗口内
        final float windowWidth = ImGui.getWindowWidth();
        final float windowHeight = ImGui.getWindowHeight();
        boolean mouseInWindow = mouseX >= windowX && mouseX <= windowX + windowWidth &&
                mouseY >= windowY && mouseY <= windowY + windowHeight;

        if (!mouseInWindow && !isDraggingLeftSplitter && !isDraggingRightSplitter) {
            // 重置悬停状态
            isHoveringLeftSplitter = false;
            isHoveringRightSplitter = false;
            return;
        }

        // 获取组件布局
        CanvasComponent canvasComponent = componentManager.getCanvasComponent();
        EditorComponent nodePanel = componentManager.getNodeLibraryComponent();
        EditorComponent propertyPanel = componentManager.getPropertyPanelComponent();

        if (canvasComponent == null) return;

        LayoutDimensions canvasDims = layoutManager.getComputedLayout(canvasComponent);
        if (canvasDims == null) return;

        // 重置悬停状态
        isHoveringLeftSplitter = false;
        isHoveringRightSplitter = false;

        // 是否显示节点库和属性面板
        boolean showNodePanel = layoutConfig.nodePanelRatio() > 0.001f;
        boolean showPropertyPanel = layoutConfig.propertyPanelRatio() > 0.001f;

        // 计算左侧分隔线位置
        float nodePanelRight;
        float nodePanelRightAbsolute = 0; // 绝对坐标

        if (showNodePanel && nodePanel != null) {
            LayoutDimensions nodePanelDims = layoutManager.getComputedLayout(nodePanel);
            if (nodePanelDims != null) {
                nodePanelRight = nodePanelDims.x() + nodePanelDims.width();
                nodePanelRightAbsolute = windowX + nodePanelRight;
            }
        }

        // 计算右侧分隔线位置
        float canvasLeft = canvasDims.x();
        float canvasRight = canvasLeft + canvasDims.width();
        float canvasRightAbsolute = windowX + canvasRight; // 绝对坐标

        // 处理已经开始的拖拽
        if (isDraggingLeftSplitter || isDraggingRightSplitter) {
            // 强制设置鼠标捕获并显示正确的光标
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);

            // 关键修改：禁用窗口拖动

            // 显示调试信息
            if (debugSplitter) {
                NodeCraft.LOGGER.debug("拖拽中 - 左侧={}, 右侧={}, 鼠标X={}, 鼠标Y={}",
                        isDraggingLeftSplitter, isDraggingRightSplitter, mouseX, mouseY);
            }

            // 处理拖拽结束
            if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) {
                isDraggingLeftSplitter = false;
                isDraggingRightSplitter = false;
                NodeCraft.LOGGER.info("结束拖拽分隔线: 最终节点面板比例={}, 画布比例={}, 属性面板比例={}",
                        layoutConfig.nodePanelRatio(), layoutConfig.canvasRatio(), layoutConfig.propertyPanelRatio());
                return;
            }

            // 计算拖拽距离对应的比例变化
            float dragDelta = mouseX - dragStartX;
            float ratioChange = dragDelta / contentWidth;

            // 更新布局比例
            float newNodePanelRatio = layoutConfig.nodePanelRatio();
            float newCanvasRatio = layoutConfig.canvasRatio();
            float newPropertyPanelRatio = layoutConfig.propertyPanelRatio();

            if (isDraggingLeftSplitter) {
                // 调整节点面板和画布的比例
                newNodePanelRatio = initialNodePanelRatio + ratioChange;
                newPropertyPanelRatio = initialPropertyPanelRatio;

                // 确保比例在合理范围内
                newNodePanelRatio = Math.max(0.05f, Math.min(0.5f, newNodePanelRatio));
                newCanvasRatio = Math.max(0.2f, 1f - newNodePanelRatio - newPropertyPanelRatio);

                // 画布触底时，回推节点库，保持右侧面板不漂移
                if (newCanvasRatio <= 0.2f) {
                    newNodePanelRatio = 1f - newPropertyPanelRatio - 0.2f;
                    newNodePanelRatio = Math.max(0.05f, Math.min(0.5f, newNodePanelRatio));
                    newCanvasRatio = 1f - newNodePanelRatio - newPropertyPanelRatio;
                }
            } else if (isDraggingRightSplitter) {
                // 调整画布和属性面板的比例
                newNodePanelRatio = initialNodePanelRatio;
                newPropertyPanelRatio = initialPropertyPanelRatio - ratioChange;

                // 确保比例在合理范围内
                newPropertyPanelRatio = Math.max(0.05f, Math.min(0.5f, newPropertyPanelRatio));
                newCanvasRatio = Math.max(0.2f, 1f - newNodePanelRatio - newPropertyPanelRatio);

                // 画布触底时，回推属性面板，保持左侧面板不漂移
                if (newCanvasRatio <= 0.2f) {
                    newPropertyPanelRatio = 1f - newNodePanelRatio - 0.2f;
                    newPropertyPanelRatio = Math.max(0.05f, Math.min(0.5f, newPropertyPanelRatio));
                    newCanvasRatio = 1f - newNodePanelRatio - newPropertyPanelRatio;
                }
            }

            try {
                // 使用无验证方式创建布局配置，避免拖拽过程中的异常
                LayoutConfig newConfig = LayoutConfig.createWithoutValidation(
                        newNodePanelRatio,
                        newCanvasRatio,
                        newPropertyPanelRatio,
                        layoutConfig.minNodePanelWidth(),
                        layoutConfig.minCanvasWidth(),
                        layoutConfig.minPropertyPanelWidth()
                );

                // 更新布局配置
                this.layoutConfig = newConfig;

                // 重新计算布局
                layoutManager.calculateLayout(contentWidth, ImGui.getContentRegionAvailY(),
                        contentStartX, ImGui.getWindowContentRegionMinY(),
                        newConfig, showMenuBar);

                // 显示调试信息
                if (debugSplitter) {
                    NodeCraft.LOGGER.debug("布局更新: 节点面板={}, 画布={}, 属性面板={}, 拖拽delta={}, 比例变化={}",
                            newNodePanelRatio, newCanvasRatio, newPropertyPanelRatio, dragDelta, ratioChange);
                }
            } catch (Exception e) {
                NodeCraft.LOGGER.warn("拖拽分隔线时无法创建有效的布局配置: {}", e.getMessage());
            }

            return; // 正在拖拽中，不进行悬停检测
        }

        // 如果没有在拖拽中，检查是否悬停在分隔线上
        // 注意：我们会优先处理分隔线拖拽，确保它不会与窗口拖动冲突

        // 检查左侧分隔线
        if (showNodePanel && Math.abs(mouseX - nodePanelRightAbsolute) < SPLITTER_HOVER_WIDTH) {
            isHoveringLeftSplitter = true;
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);

            // 显示调试信息
            if (debugSplitter) {
                NodeCraft.LOGGER.debug("悬停在左侧分隔线: 鼠标X={}, 分隔线X={}, 差值={}",
                        mouseX, nodePanelRightAbsolute, Math.abs(mouseX - nodePanelRightAbsolute));
            }

            // 在分隔线区域内点击鼠标左键开始拖拽
            if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                isDraggingLeftSplitter = true;
                dragStartX = mouseX;
                initialNodePanelRatio = layoutConfig.nodePanelRatio();
                initialPropertyPanelRatio = layoutConfig.propertyPanelRatio();
                initialCanvasRatio = layoutConfig.canvasRatio();

                // 非常重要：强制捕获鼠标，阻止所有其他鼠标事件

                NodeCraft.LOGGER.info("开始拖拽左侧分隔线: 初始位置={}, 节点面板比例={}, 画布比例={}",
                        dragStartX, initialNodePanelRatio, initialCanvasRatio);
            }
        }
        // 检查右侧分隔线
        else if (showPropertyPanel && Math.abs(mouseX - canvasRightAbsolute) < SPLITTER_HOVER_WIDTH) {
            isHoveringRightSplitter = true;
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);

            // 显示调试信息
            if (debugSplitter) {
                NodeCraft.LOGGER.debug("悬停在右侧分隔线: 鼠标X={}, 分隔线X={}, 差值={}",
                        mouseX, canvasRightAbsolute, Math.abs(mouseX - canvasRightAbsolute));
            }

            // 在分隔线区域内点击鼠标左键开始拖拽
            if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
                isDraggingRightSplitter = true;
                dragStartX = mouseX;
                initialNodePanelRatio = layoutConfig.nodePanelRatio();
                initialPropertyPanelRatio = layoutConfig.propertyPanelRatio();
                initialCanvasRatio = layoutConfig.canvasRatio();

                // 非常重要：强制捕获鼠标，阻止所有其他鼠标事件

                NodeCraft.LOGGER.info("开始拖拽右侧分隔线: 初始位置={}, 属性面板比例={}, 画布比例={}",
                        dragStartX, initialPropertyPanelRatio, initialCanvasRatio);
            }
        }
    }

    /**
     * 渲染分隔线提示（只在鼠标悬停时显示）
     * @param contentStartX 内容区起始X坐标
     * @param contentStartY 内容区起始Y坐标
     * @param contentHeight 内容区高度
     */
    private void renderSplitters(float contentStartX, float contentStartY, float contentHeight) {
        // 是否显示节点库和属性面板
        boolean showNodePanel = layoutConfig.nodePanelRatio() > 0.001f;
        boolean showPropertyPanel = layoutConfig.propertyPanelRatio() > 0.001f;

        // 获取组件布局
        CanvasComponent canvasComponent = componentManager.getCanvasComponent();
        EditorComponent nodePanel = componentManager.getNodeLibraryComponent();
        EditorComponent propertyPanel = componentManager.getPropertyPanelComponent();

        if (canvasComponent == null) return;

        LayoutDimensions canvasDims = layoutManager.getComputedLayout(canvasComponent);
        if (canvasDims == null) return;

        float nodePanelRight = 0;
        float canvasLeft = canvasDims.x();
        float canvasRight = canvasLeft + canvasDims.width();

        // 获取ImGui窗口位置，用于绘制分隔线
        final float windowX = ImGui.getWindowPosX();
        final float windowY = ImGui.getWindowPosY();

        // 计算左侧分隔线位置
        if (showNodePanel && nodePanel != null) {
            LayoutDimensions nodePanelDims = layoutManager.getComputedLayout(nodePanel);
            if (nodePanelDims != null) {

                // 绘制左侧分隔线 - 不再绘制任何分隔线，仅在实际拖拽时设置光标样式
                if (isDraggingLeftSplitter || isHoveringLeftSplitter) {
                    // 不绘制任何视觉提示，仅在调试模式下记录
                    if (debugSplitter) {
                        NodeCraft.LOGGER.debug("左侧分隔线活跃: 拖拽={}, 悬停={}",
                                isDraggingLeftSplitter, isHoveringLeftSplitter);
                    }
                }
            }
        }

        // 绘制右侧分隔线
        if (showPropertyPanel && propertyPanel != null) {
            // 不再绘制任何分隔线，仅在实际拖拽时设置光标样式
            if (isDraggingRightSplitter || isHoveringRightSplitter) {
                // 不绘制任何视觉提示，仅在调试模式下记录
                if (debugSplitter) {
                    NodeCraft.LOGGER.debug("右侧分隔线活跃: 拖拽={}, 悬停={}",
                            isDraggingRightSplitter, isHoveringRightSplitter);
                }
            }
        }
    }

    /**
     * 渲染非画布组件
     * @param componentsToRender 组件列表
     * @param delta 渲染 delta
     */
    private void renderNonCanvasComponents(Map<String, EditorComponent> componentsToRender, float delta) {
        for (EditorComponent component : componentsToRender.values()) {
            if (component == null || component instanceof CanvasComponent) {
                continue; // 跳过空组件和画布组件
            }

            LayoutDimensions dims = layoutManager.getComputedLayout(component);
            if (dims != null && dims.width() > 0 && dims.height() > 0) {
                try {
                    ImGui.setCursorPos(dims.x(), dims.y());
                    // 为非画布组件创建Child窗口
                    String childId = "child_" + component.getComponentId();

                    // 判断是否是属性面板，决定是否有边框
                    boolean hasBorder = component instanceof PropertyPanelComponent;

                    boolean isPropertyPanel = component instanceof PropertyPanelComponent;
                    if (isPropertyPanel) {
                        float baseScrollbarSize = ImGui.getStyle().getScrollbarSize();
                        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ScrollbarSize, baseScrollbarSize * 0.5f);
                    }

                    // 非画布组件可以有滚动条，根据类型决定是否有边框
                    boolean childBegun;
                    try {
                        childBegun = ImGui.beginChild(childId, dims.width(), dims.height(), hasBorder);
                    } finally {
                        if (isPropertyPanel) {
                            ImGui.popStyleVar();
                        }
                    }

                    try {
                        if (!childBegun) {
                            NodeCraft.LOGGER.debug("Skipped child window render for component: {}",
                                    component.getComponentId());
                            continue;
                        }

                        logDetachedChildState(component.getComponentId(), dims);
                        component.render(0, 0, ImGui.getContentRegionAvailX(),
                                ImGui.getContentRegionAvailY(), 0, 0);
                    } finally {
                        ImGui.endChild();
                    }
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("渲染组件 {} 时出错: {}",
                            component.getComponentId(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 单独渲染画布组件，使其成为NodeCraft面板的核心
     * @param delta 渲染 delta
     */
    private void renderCanvasComponent(float delta) {
        CanvasComponent canvasComponent = componentManager.getCanvasComponent();
        if (canvasComponent == null || !canvasComponent.isVisible()) {
            return;
        }

        LayoutDimensions dims = layoutManager.getComputedLayout(canvasComponent);
        if (dims == null || dims.width() <= 0 || dims.height() <= 0) {
            NodeCraft.LOGGER.warn("画布组件布局无效或尺寸为零");
            return;
        }

        try {
            ImGui.setCursorPos(dims.x(), dims.y());

            // 为画布创建特殊的Child窗口
            String childId = "child_" + canvasComponent.getComponentId();

            // 画布专用窗口标志
            int canvasFlags = ImGuiWindowFlags.NoScrollbar |
                    ImGuiWindowFlags.NoScrollWithMouse |
                    ImGuiWindowFlags.NoCollapse;

            // 应用画布背景颜色（含透明度）
            float[] canvasBg = canvasComponent.getBackgroundColor();
            ImGui.pushStyleColor(ImGuiCol.ChildBg, canvasBg[0], canvasBg[1], canvasBg[2], canvasBg[3]);

            // 将画布边框设为 true
            boolean childBegun = ImGui.beginChild(childId, dims.width(), dims.height(), true, canvasFlags);

            try {
                if (!childBegun) {
                    NodeCraft.LOGGER.warn("Failed to begin canvas child window");
                    return;
                }

                // 使用全尺寸进行渲染
                logDetachedChildState(canvasComponent.getComponentId(), dims);
                canvasComponent.render(0, 0, ImGui.getContentRegionAvailX(),
                        ImGui.getContentRegionAvailY(), 0, 0);
            } finally {
                ImGui.endChild();
                ImGui.popStyleColor(); // 恢复 ChildBg
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染画布组件时出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 更新布局配置
     * @param newConfig 新的布局配置
     */
    public void updateLayoutConfig(LayoutConfig newConfig) {
        this.layoutConfig = newConfig;
        // 如果有父窗口内容区域，立即重新计算布局
        if (ImGui.isWindowAppearing()) {
            final float contentStartX = ImGui.getWindowContentRegionMinX();
            final float contentStartY = ImGui.getWindowContentRegionMinY();
            final float contentWidth = ImGui.getContentRegionAvailX();
            final float contentHeight = ImGui.getContentRegionAvailY();

            // 可用于布局计算的区域 (检查确保不为负)
            final float effectiveWidth = Math.max(0f, contentWidth);
            final float effectiveHeight = Math.max(0f, contentHeight);

            // 调用 LayoutManager 重新计算布局
            layoutManager.calculateLayout(effectiveWidth, effectiveHeight,
                    contentStartX, contentStartY,
                    this.layoutConfig, showMenuBar);
        }
        NodeCraft.LOGGER.info("更新布局配置: 节点面板比例={}, 画布比例={}, 属性面板比例={}",
                newConfig.nodePanelRatio(),
                newConfig.canvasRatio(),
                newConfig.propertyPanelRatio());
    }

    /**
     * 获取当前布局配置
     * @return 当前布局配置
     */
    public LayoutConfig getLayoutConfig() {
        return layoutConfig;
    }

    /**
     * 检查是否正在拖拽分隔线
     * @return 是否正在拖拽
     */
    public boolean isDraggingSplitter() {
        return isDraggingLeftSplitter || isDraggingRightSplitter;
    }

    /**
     * 检查是否正在悬停在分隔线上
     * @return 是否正在悬停
     */
    public boolean isHoveringSplitter() {
        return isHoveringLeftSplitter || isHoveringRightSplitter;
    }

    private void logDetachedLayoutState(
            String phase,
            float contentStartX,
            float contentStartY,
            float effectiveWidth,
            float effectiveHeight) {
        if (!ImGuiInputAdapter.isMouseDown(ImGuiMouseButton.Left)) {
            return;
        }

        NodeCraft.LOGGER.info(
                "Detached {}: windowPos=({}, {}), windowSize=({}, {}), contentStart=({}, {}), contentSize=({}, {}), hovered={}, anyHovered={}, anyPopupOpen={}",
                phase,
                ImGui.getWindowPosX(),
                ImGui.getWindowPosY(),
                ImGui.getWindowWidth(),
                ImGui.getWindowHeight(),
                contentStartX,
                contentStartY,
                effectiveWidth,
                effectiveHeight,
                ImGui.isWindowHovered(),
                ImGui.isAnyItemHovered(),
                ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId));
    }

    private void logDetachedChildState(String componentId, LayoutDimensions dims) {
        if (!ImGuiInputAdapter.isMouseDown(ImGuiMouseButton.Left)) {
            return;
        }

        NodeCraft.LOGGER.info(
                "Detached child {}: cursorPos=({}, {}), dims=({}, {}, {}, {}), childWindowPos=({}, {}), childWindowSize=({}, {}), hovered={}, anyHovered={}, anyActive={}, anyPopupOpen={}",
                componentId,
                ImGui.getCursorPosX(),
                ImGui.getCursorPosY(),
                dims.x(),
                dims.y(),
                dims.width(),
                dims.height(),
                ImGui.getWindowPosX(),
                ImGui.getWindowPosY(),
                ImGui.getWindowWidth(),
                ImGui.getWindowHeight(),
                ImGui.isWindowHovered(),
                ImGui.isAnyItemHovered(),
                ImGui.isAnyItemActive(),
                ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId));
    }
}
