package com.nodecraft.gui.screens;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BooleanSupplier;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.panel.CanvasComponent;
import com.nodecraft.gui.components.panel.NodeLibraryComponent;
import com.nodecraft.gui.dialogs.ConfirmationDialog;
import com.nodecraft.gui.dialogs.FileDialogManager;
import com.nodecraft.gui.dialogs.MessageDialog;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.impl.ImGuiNodeIO;
import com.nodecraft.gui.style.MinecraftTheme;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.interaction.AreaPreviewStyleSettings;
import com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager;
import com.nodecraft.nodesystem.visual.SelectionVisualFeedback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import imgui.ImGui;
import imgui.ImVec2;

/**
 * 负责渲染 Nodecraft 编辑器的菜单栏。
 */
public class MenuBarRenderer {

    private final ComponentManager componentManager;
    private final Runnable closeAction;
    private final Runnable detachEditorAction;
    private final Runnable attachEditorAction;
    private final BooleanSupplier detachedStateSupplier;

    // 文件过滤器
    private static final String NODE_GRAPH_FILTER = "节点图文件 (*.nodecraft)";
    private static final String DEFAULT_EXTENSION = ".nodecraft";
    
    // 最近文件路径
    private Path lastSavedPath = null;
    
    // 执行器引用
    private NodeExecutor currentExecutor = null;
    private String executionStatus = null;
    private long executionStatusTime = 0;

    public MenuBarRenderer(
            ComponentManager componentManager,
            Runnable closeAction,
            Runnable detachEditorAction,
            Runnable attachEditorAction,
            BooleanSupplier detachedStateSupplier) {
        this.componentManager = componentManager;
        this.closeAction = closeAction;
        this.detachEditorAction = detachEditorAction;
        this.attachEditorAction = attachEditorAction;
        this.detachedStateSupplier = detachedStateSupplier;
    }

    /**
     * 渲染菜单栏。
     */
    public void render() {
        if (ImGui.beginMenuBar()) {
            if (ImGui.beginMenu("文件")) {
                if (ImGui.menuItem("新建节点图", "Ctrl+N")) {
                    createNewNodeGraph();
                }
                if (ImGui.menuItem("打开节点图...", "Ctrl+O")) {
                    openNodeGraph();
                }
                if (ImGui.menuItem("保存节点图", "Ctrl+S")) {
                    saveNodeGraph(false);
                }
                if (ImGui.menuItem("另存为...", null)) {
                    saveNodeGraph(true);
                }
                ImGui.separator();

                if (ImGui.menuItem("关闭编辑器")) {
                    closeAction.run(); // 调用关闭回调
                }
                ImGui.endMenu();
            }
            if (ImGui.beginMenu("编辑")) {
                // 获取编辑器实例
                CanvasComponent canvas = componentManager.getCanvasComponent();
                ImGuiNodeEditor editor = null;
                
                if (canvas != null && canvas.getNodeEditor() instanceof ImGuiNodeEditor) {
                    editor = (ImGuiNodeEditor) canvas.getNodeEditor();
                }
                
                // 撤销
                boolean canUndo = editor != null && editor.getHistory().canUndo();
                if (ImGui.menuItem("撤销", "Ctrl+Z", false, canUndo)) {
                    if (editor != null) {
                        editor.undo();
                    }
                }
                
                // 重做
                boolean canRedo = editor != null && editor.getHistory().canRedo();
                if (ImGui.menuItem("重做", "Ctrl+Y", false, canRedo)) {
                    if (editor != null) {
                        editor.redo();
                    }
                }
                
                ImGui.separator();
                
                // 判断是否有选中的节点
                boolean hasSelection = editor != null && !editor.getSelectedNodeIds().isEmpty();
                
                // 剪切
                if (ImGui.menuItem("剪切", "Ctrl+X", false, hasSelection)) {
                    if (editor != null) {
                        editor.cutSelectedNodes();
                    }
                }
                
                // 复制
                if (ImGui.menuItem("复制", "Ctrl+C", false, hasSelection)) {
                    if (editor != null) {
                        editor.copySelectedNodes();
                    }
                }
                
                // 粘贴
                if (ImGui.menuItem("粘贴", "Ctrl+V")) {
                    if (editor != null) {
                        // 获取画布组件(复用之前的canvas变量)
                        // 使用画布组件的新方法获取准确的画布中心世界坐标
                        ImVec2 centerWorldPos = canvas.getCanvasCenterWorldPosition();

                        NodeCraft.LOGGER.info("尝试在画布中心世界坐标 ({}, {}) 粘贴节点", centerWorldPos.x, centerWorldPos.y);

                        // 使用准确的世界坐标粘贴
                        editor.pasteNodesAtPosition(centerWorldPos.x, centerWorldPos.y);
                    }
                }
                
                // 删除
                if (ImGui.menuItem("删除", "Del", false, hasSelection)) {
                    if (editor != null) {
                        editor.deleteSelectedNodes();
                    }
                }
                
                ImGui.separator();

                if (ImGui.menuItem("Create Subgraph From Selection", null, false, hasSelection)) {
                    if (editor != null && !editor.createSubgraphFromSelection()) {
                        new MessageDialog("Subgraph", "Failed to create subgraph from current selection.").show();
                    }
                }
                
                // 分组功能暂未实现
                if (ImGui.menuItem("组选择", null, false, false)) {
                    NodeCraft.LOGGER.info("组选择功能尚未实现");
                }
                
                if (ImGui.menuItem("解组选择", null, false, false)) {
                    NodeCraft.LOGGER.info("解组选择功能尚未实现");
                }
                
                ImGui.endMenu();
            }
            if (ImGui.beginMenu("视图")) {
                // 缩放选项
                CanvasComponent viewCanvas = componentManager != null ? componentManager.getCanvasComponent() : null;
                if (ImGui.menuItem("放大", "Ctrl++")) {
                    if (viewCanvas != null) {
                        viewCanvas.zoomIn();
                    }
                }
                if (ImGui.menuItem("缩小", "Ctrl+-")) {
                    if (viewCanvas != null) {
                        viewCanvas.zoomOut();
                    }
                }
                if (ImGui.menuItem("重置视图", "Ctrl+0")) {
                    if (viewCanvas != null) {
                        viewCanvas.resetCanvasView();
                    }
                }
                if (ImGui.menuItem("适应视图", "Ctrl+Home")) {
                    if (viewCanvas != null) {
                        viewCanvas.fitToContent();
                    }
                }
                
                ImGui.separator();
                
                // 面板显示选项
                if (componentManager != null) {
                    NodecraftScreen screen = getNodecraftScreen();
                    if (screen != null) {
                        // 节点库面板显示选项
                        boolean nodeLibVisible = screen.isNodePanelVisible();
                        if (ImGui.menuItem("显示节点库面板", null, nodeLibVisible)) {
                            screen.toggleNodePanel();
                        }
                        
                        // 属性面板显示选项
                        boolean propertyPanelVisible = screen.isPropertyPanelVisible();
                        if (ImGui.menuItem("显示属性面板", null, propertyPanelVisible)) {
                            screen.togglePropertyPanel();
                        }
                        
                        ImGui.separator();
                    }

                    NodeLibraryComponent nodeLibrary = componentManager.getNodeLibraryComponent();
                    if (nodeLibrary != null && ImGui.beginMenu("节点库显示方式")) {
                        NodeLibraryComponent.DisplayMode mode = nodeLibrary.getDisplayMode();
                        if (ImGui.menuItem("列表", null, mode == NodeLibraryComponent.DisplayMode.LIST)) {
                            nodeLibrary.setDisplayMode(NodeLibraryComponent.DisplayMode.LIST);
                        }
                        if (ImGui.menuItem("平铺", null, mode == NodeLibraryComponent.DisplayMode.GRID)) {
                            nodeLibrary.setDisplayMode(NodeLibraryComponent.DisplayMode.GRID);
                        }

                        ImGui.separator();
                        if (ImGui.beginMenu("平铺尺寸倍率")) {
                            float scale = nodeLibrary.getGridTileSizeScale();
                            if (ImGui.menuItem("1x", null, Math.abs(scale - 1.0f) < 0.001f)) {
                                nodeLibrary.setGridTileSizeScale(1.0f);
                            }
                            if (ImGui.menuItem("1.5x", null, Math.abs(scale - 1.5f) < 0.001f)) {
                                nodeLibrary.setGridTileSizeScale(1.5f);
                            }
                            if (ImGui.menuItem("2x", null, Math.abs(scale - 2.0f) < 0.001f)) {
                                nodeLibrary.setGridTileSizeScale(2.0f);
                            }
                            ImGui.endMenu();
                        }
                        ImGui.endMenu();
                    }

                    ImGui.separator();
                }
                
                // 网格显示选项
                CanvasComponent canvas = viewCanvas;
                boolean showGrid = canvas != null && canvas.isShowGrid();
                if (ImGui.menuItem("显示网格", null, showGrid)) {
                    if (canvas != null) {
                        canvas.toggleGrid();
                    } else {
                        NodeCraft.LOGGER.warn("CanvasComponent is null, cannot toggle grid");
                    }
                }
                
                // 节点显示模式子菜单
                if (ImGui.beginMenu("节点显示模式")) {
                    if (canvas != null) {
                        CanvasComponent.NodeDisplayMode currentMode = canvas.getNodeDisplayMode();
                        
                        if (ImGui.menuItem("完整模式", null, currentMode == CanvasComponent.NodeDisplayMode.FULL)) {
                            canvas.setNodeDisplayMode(CanvasComponent.NodeDisplayMode.FULL);
                        }
                        if (ImGui.menuItem("紧凑模式", null, currentMode == CanvasComponent.NodeDisplayMode.COMPACT)) {
                            canvas.setNodeDisplayMode(CanvasComponent.NodeDisplayMode.COMPACT);
                        }
                        if (ImGui.menuItem("仅图标", null, currentMode == CanvasComponent.NodeDisplayMode.ICON_ONLY)) {
                            canvas.setNodeDisplayMode(CanvasComponent.NodeDisplayMode.ICON_ONLY);
                        }
                        if (ImGui.menuItem("仅文本", null, currentMode == CanvasComponent.NodeDisplayMode.TEXT_ONLY)) {
                            canvas.setNodeDisplayMode(CanvasComponent.NodeDisplayMode.TEXT_ONLY);
                        }
                    } else {
                        ImGui.menuItem("无法获取显示模式", null, false, false);
                    }
                    ImGui.endMenu();
                }
                
                // 节点预览选项
                boolean showPreviews = canvas != null && canvas.isShowNodePreviews();
                if (ImGui.menuItem("显示节点预览", null, showPreviews)) {
                    if (canvas != null) {
                        canvas.toggleNodePreviews();
                    } else {
                        NodeCraft.LOGGER.warn("CanvasComponent is null, cannot toggle node previews");
                    }
                }

                if (ImGui.beginMenu("方块选中样式（单块/序列）")) {
                    SelectionVisualFeedback visualFeedback = SelectionVisualFeedback.getInstance();

                    boolean showFill = visualFeedback.isBlockHighlightShowFill();
                    if (ImGui.menuItem("显示面填充", null, showFill)) {
                        visualFeedback.setBlockHighlightShowFill(!showFill);
                    }

                    boolean showOutline = visualFeedback.isBlockHighlightShowOutline();
                    if (ImGui.menuItem("显示高亮边框", null, showOutline)) {
                        visualFeedback.setBlockHighlightShowOutline(!showOutline);
                    }

                    boolean enablePulse = visualFeedback.isBlockHighlightEnablePulse();
                    if (ImGui.menuItem("脉冲动画", null, enablePulse)) {
                        visualFeedback.setBlockHighlightEnablePulse(!enablePulse);
                    }

                    float[] lineWidth = new float[] { visualFeedback.getBlockHighlightLineWidth() };
                    if (ImGui.sliderFloat("边框线宽##picked_block_highlight", lineWidth, 0.5f, 8.0f, "%.1f")) {
                        visualFeedback.setBlockHighlightLineWidth(lineWidth[0]);
                    }

                    float[] opacityScale = new float[] { visualFeedback.getBlockHighlightOpacityScale() };
                    if (ImGui.sliderFloat("高亮透明度##picked_block_highlight", opacityScale, 0.1f, 1.0f, "%.2f")) {
                        visualFeedback.setBlockHighlightOpacityScale(opacityScale[0]);
                    }

                    float[] fillColor = visualFeedback.getBlockHighlightFillColor();
                    if (ImGui.colorEdit3("填充颜色##picked_block_fill_color", fillColor)) {
                        visualFeedback.setBlockHighlightFillColor(fillColor[0], fillColor[1], fillColor[2]);
                    }

                    if (ImGui.menuItem("恢复默认样式")) {
                        visualFeedback.resetBlockHighlightStyle();
                    }

                    ImGui.endMenu();
                }

                if (ImGui.beginMenu("区域选择样式")) {
                    AreaPreviewStyleSettings areaStyle =
                        NodeEditorInteractionManager.getInstance().getAreaPreviewStyle();

                    boolean areaShowFill = areaStyle.isShowFill();
                    if (ImGui.menuItem("显示面填充##area_selection", null, areaShowFill)) {
                        areaStyle.setShowFill(!areaShowFill);
                    }

                    boolean areaShowOutline = areaStyle.isShowOutline();
                    if (ImGui.menuItem("显示高亮边框##area_selection", null, areaShowOutline)) {
                        areaStyle.setShowOutline(!areaShowOutline);
                    }

                    boolean areaPulse = areaStyle.isEnablePulse();
                    if (ImGui.menuItem("脉冲动画##area_selection", null, areaPulse)) {
                        areaStyle.setEnablePulse(!areaPulse);
                    }

                    float[] areaLineWidth = new float[] { areaStyle.getLineWidth() };
                    if (ImGui.sliderFloat("边框线宽##area_selection", areaLineWidth, 0.5f, 8.0f, "%.1f")) {
                        areaStyle.setLineWidth(areaLineWidth[0]);
                    }

                    float[] areaOpacity = new float[] { areaStyle.getOpacity() };
                    if (ImGui.sliderFloat("高亮透明度##area_selection", areaOpacity, 0.05f, 1.0f, "%.2f")) {
                        areaStyle.setOpacity(areaOpacity[0]);
                    }

                    float[] areaOutlineColor = areaStyle.getOutlineColor();
                    if (ImGui.colorEdit3("边框颜色##area_selection", areaOutlineColor)) {
                        areaStyle.setOutlineColor(
                            areaOutlineColor[0],
                            areaOutlineColor[1],
                            areaOutlineColor[2]
                        );
                    }

                    float[] areaFillColor = areaStyle.getFillColor();
                    if (ImGui.colorEdit3("填充颜色##area_selection", areaFillColor)) {
                        areaStyle.setFillColor(
                            areaFillColor[0],
                            areaFillColor[1],
                            areaFillColor[2]
                        );
                    }

                    if (ImGui.menuItem("恢复默认样式##area_selection")) {
                        areaStyle.resetToDefaults();
                    }

                    ImGui.endMenu();
                }

                ImGui.separator();

                // 面板背景透明度滑动条
                float[] panelAlpha = new float[] { MinecraftTheme.getPanelAlpha() };
                if (ImGui.sliderFloat("面板背景透明度##panel_alpha", panelAlpha, 0.0f, 1.0f, "%.2f")) {
                    MinecraftTheme.setPanelAlpha(panelAlpha[0]);
                }

                // 画布透明度滑动条
                CanvasComponent alphaCanvas = componentManager != null ? componentManager.getCanvasComponent() : null;
                if (alphaCanvas != null) {
                    float[] canvasAlpha = new float[] { alphaCanvas.getBackgroundAlpha() };
                    if (ImGui.sliderFloat("画布透明度##canvas_alpha", canvasAlpha, 0.0f, 1.0f, "%.2f")) {
                        alphaCanvas.setBackgroundAlpha(canvasAlpha[0]);
                    }
                }

                ImGui.endMenu();
            }
            // === 执行菜单 ===
            if (ImGui.beginMenu("执行")) {
                // 获取编辑器和图
                CanvasComponent execCanvas = componentManager != null ? componentManager.getCanvasComponent() : null;
                ImGuiNodeEditor execEditor;
                NodeGraph execGraph = null;
                if (execCanvas != null && execCanvas.getNodeEditor() instanceof ImGuiNodeEditor) {
                    execEditor = (ImGuiNodeEditor) execCanvas.getNodeEditor();
                    execGraph = execEditor.getCurrentGraph();
                }
                
                boolean isExecuting = currentExecutor != null && currentExecutor.isExecuting();
                boolean hasGraph = execGraph != null && !execGraph.getNodes().isEmpty();
                
                // 执行节点图
                if (ImGui.menuItem("\u25b6 执行节点图", "F5", false, hasGraph && !isExecuting)) {
                    if (execGraph != null) {
                        executeNodeGraph(execGraph);
                    }
                }
                
                // 停止执行
                if (ImGui.menuItem("\u25a0 停止执行", "Shift+F5", false, isExecuting)) {
                    stopExecution();
                }
                
                ImGui.separator();
                
                // 显示执行状态
                if (isExecuting) {
                    ImGui.textColored(0.2f, 1.0f, 0.2f, 1.0f, "状态: 正在执行...");
                } else if (executionStatus != null) {
                    // 状态信息显示5秒后自动清除
                    if (System.currentTimeMillis() - executionStatusTime < 5000) {
                        if (executionStatus.startsWith("成功")) {
                            ImGui.textColored(0.2f, 1.0f, 0.2f, 1.0f, "状态: " + executionStatus);
                        } else {
                            ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "状态: " + executionStatus);
                        }
                    } else {
                        executionStatus = null;
                    }
                } else {
                    ImGui.textDisabled("状态: 就绪");
                }
                
                ImGui.endMenu();
            }
            if (ImGui.beginMenu("窗口")) {
                boolean isDetached = detachedStateSupplier != null && detachedStateSupplier.getAsBoolean();
                if (!isDetached && ImGui.menuItem("分离到独立窗口", null)) {
                    detachEditorAction.run();
                }
                if (isDetached && ImGui.menuItem("收回主窗口", null)) {
                    attachEditorAction.run();
                }
                ImGui.endMenu();
            }
            if (ImGui.beginMenu("帮助")) {
                if (ImGui.menuItem("关于 NodeCraft", null)) {
                    showAboutDialog();
                }
                if (ImGui.menuItem("节点参考", null)) {
                    openNodeReference();
                }
                
                ImGui.separator();
                
                // 调试选项
                if (ImGui.menuItem("调试历史记录", null)) {
                    debugHistoryFunction();
                }
                
                ImGui.endMenu();
            }


            // 菜单栏最右侧窗口控制按钮（最小化、最大化、关闭）
            if (false) {
            float buttonWidth = 24.0f;
            float spacing = 2.0f;
            float rightEdgeX = ImGui.getWindowWidth();
            float closeButtonPosX = rightEdgeX - buttonWidth;
            float maximizeButtonPosX = rightEdgeX - buttonWidth * 2 - spacing;
            float minimizeButtonPosX = rightEdgeX - buttonWidth * 3 - spacing * 2;


            // 获取主窗口句柄（支持分离窗口和主窗口）
            long windowHandle = 0;
            boolean isDetached = detachedStateSupplier != null && detachedStateSupplier.getAsBoolean();
            try {
                if (isDetached) {
                    // 分离窗口
                    com.nodecraft.gui.window.DetachedEditorWindow detached = com.nodecraft.gui.editor.integration.ImGuiRenderer.getInstance().getDetachedEditorWindow();
                    if (detached != null && detached.isOpen()) {
                        windowHandle = detached.getWindowHandle();
                    }
                } else {
                    // 主窗口
                    net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                    if (client != null && client.getWindow() != null) {
                        windowHandle = client.getWindow().getHandle();
                    }
                }
            } catch (Exception e) {
                NodeCraft.LOGGER.error("获取窗口句柄失败", e);
            }

            // 最小化按钮
            ImGui.setCursorPosX(minimizeButtonPosX);
            if (ImGui.button("_", buttonWidth, 0)) {
                if (windowHandle != 0) {
                    org.lwjgl.glfw.GLFW.glfwIconifyWindow(windowHandle);
                    NodeCraft.LOGGER.info("窗口已最小化: {}", windowHandle);
                } else {
                    NodeCraft.LOGGER.warn("未能获取窗口句柄，无法最小化");
                }
            }

            // 最大化按钮
            ImGui.setCursorPosX(maximizeButtonPosX);
            if (ImGui.button("□", buttonWidth, 0)) {
                if (windowHandle != 0) {
                    int maximized = org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MAXIMIZED);
                    if (maximized == org.lwjgl.glfw.GLFW.GLFW_TRUE) {
                        org.lwjgl.glfw.GLFW.glfwRestoreWindow(windowHandle);
                        NodeCraft.LOGGER.info("窗口已还原: {}", windowHandle);
                    } else {
                        org.lwjgl.glfw.GLFW.glfwMaximizeWindow(windowHandle);
                        NodeCraft.LOGGER.info("窗口已最大化: {}", windowHandle);
                    }
                } else {
                    NodeCraft.LOGGER.warn("未能获取窗口句柄，无法最大化/还原");
                }
            }

            // 关闭按钮
            ImGui.setCursorPosX(closeButtonPosX);
            if (ImGui.button("×", buttonWidth, 0)) {
                closeAction.run();
            }

            }
            ImGui.endMenuBar();
        }
    }
    
    /**
     * 显示关于NodeCraft的对话框
     */
    private void showAboutDialog() {
        String version = "1.0.0"; // 版本号
        String buildDate = "2023-05-01"; // 构建日期

        String content = "NodeCraft 版本: " + version + "\n" +
                "构建日期: " + buildDate + "\n\n" +
                "NodeCraft 是一个基于节点的可视化编程工具，\n" +
                "专为Minecraft模组开发设计。\n\n" +
                "使用 Java 和 ImGui 开发。\n" +
                "Copyright © 2023 NodeCraft 团队。保留所有权利。";
        
        new MessageDialog(
            "关于 NodeCraft",
                content
        ).show();
        
        NodeCraft.LOGGER.info("已显示关于对话框");
    }
    
    /**
     * 打开节点参考文档
     */
    private void openNodeReference() {
        try {
            // 检查文档是否存在
            java.io.File docsDir = new java.io.File("docs");
            java.io.File nodeRefFile = new java.io.File(docsDir, "node_reference.html");
            
            if (nodeRefFile.exists()) {
                // 如果文档存在，使用默认浏览器打开
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(nodeRefFile.toURI());
                    NodeCraft.LOGGER.info("已打开节点参考文档: {}", nodeRefFile.getAbsolutePath());
                } else {
                    showDocumentationError("您的系统不支持自动打开浏览器。请手动打开文档：" + nodeRefFile.getAbsolutePath());
                }
            } else {
                // 如果文档不存在，尝试打开在线文档
                String onlineDocUrl = "https://nodecraft.example.com/docs/reference";
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(java.net.URI.create(onlineDocUrl));
                    NodeCraft.LOGGER.info("已打开在线节点参考文档: {}", onlineDocUrl);
                } else {
                    showDocumentationError("找不到本地文档，且您的系统不支持自动打开浏览器。请访问: " + onlineDocUrl);
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("打开节点参考文档失败", e);
            showDocumentationError("打开文档失败: " + e.getMessage());
        }
    }
    
    /**
     * 显示文档错误对话框
     */
    private void showDocumentationError(String message) {
        new MessageDialog(
            "无法打开文档",
            message
        ).show();
    }
    
    /**
     * 创建新的节点图（包级可见，供快捷键调用）
     */
    void createNewNodeGraph() {
        try {
            CanvasComponent canvas = componentManager.getCanvasComponent();
            if (canvas != null && canvas.getNodeEditor() instanceof ImGuiNodeEditor editor) {
                // 检查当前节点图是否有未保存的更改
                if (editor.hasUnsavedChanges()) {
                    // 显示确认对话框，询问是否保存当前节点图
                    showSaveConfirmationDialog(() -> {
                        // 用户确认后的回调，创建新节点图
                        createNewNodeGraphImpl(editor);
                    });
                } else {
                    // 直接创建新节点图
                    createNewNodeGraphImpl(editor);
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("创建新节点图失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 实际创建新节点图的实现
     */
    private void createNewNodeGraphImpl(ImGuiNodeEditor editor) {
        // 创建新的空白节点图
        NodeGraph newGraph = new NodeGraph("新建节点图");
        editor.setCurrentGraph(newGraph);
        editor.clearNodePositions();
        
        // 重置视图
        editor.setCanvasView(1.0f, 0.0f, 0.0f);
        
        // 清除最近保存路径
        lastSavedPath = null;
        
        NodeCraft.LOGGER.info("已创建新的节点图");
    }
    
    /**
     * 显示保存确认对话框
     * @param onConfirm 用户确认后的回调
     */
    private void showSaveConfirmationDialog(Runnable onConfirm) {
        // "否"按钮 - 不保存，直接创建新节点图
        new ConfirmationDialog(
            "保存确认",
            "当前节点图有未保存的更改，是否保存？",
            () -> {
                // "是"按钮 - 保存当前文件，然后创建新节点图
                // 保存成功后创建新节点图
                saveNodeGraph(false, onConfirm);
            },
                onConfirm,
            null  // "取消"按钮 - 不做任何操作
        ).show();
    }
    
    /**
     * 打开节点图文件（包级可见，供快捷键调用）
     */
    void openNodeGraph() {
        try {
            CanvasComponent canvas = componentManager.getCanvasComponent();
            if (canvas != null && canvas.getNodeEditor() instanceof ImGuiNodeEditor editor) {
                // 检查当前节点图是否有未保存的更改
                if (editor.hasUnsavedChanges()) {
                    // 显示确认对话框，询问是否保存当前节点图
                    showSaveConfirmationDialog(() -> {
                        // 用户确认后的回调，打开新节点图
                        openNodeGraphImpl(editor);
                    });
                } else {
                    // 直接打开新节点图
                    openNodeGraphImpl(editor);
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("加载节点图失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 实际打开节点图的实现
     */
    private void openNodeGraphImpl(ImGuiNodeEditor editor) {
        // 使用文件对话框管理器的回调方式
        FileDialogManager.showFileDialog(
            "打开节点图",
            getDefaultDirectory(),
            NODE_GRAPH_FILTER,
            false, // 不是保存对话框
            null,  // 打开文件不需要默认扩展名
            selectedPath -> {
                if (selectedPath != null) {
                    // 获取IO组件并加载文件
                    ImGuiNodeIO io = editor.getNodeIO();
                    if (io != null) {
                        boolean loaded = io.loadGraph(selectedPath);
                        if (loaded) {
                            lastSavedPath = selectedPath;
                            String notice = io.getLastOperationError();
                            if (notice != null && !notice.isBlank()) {
                                new MessageDialog("打开节点图", notice).show();
                            }
                            NodeCraft.LOGGER.info("成功加载节点图: {}", selectedPath);
                        } else {
                            String error = io.getLastOperationError();
                            if (error == null || error.isBlank()) {
                                error = "打开失败：节点图格式无效或内容不兼容。";
                            }
                            new MessageDialog("打开节点图失败", error).show();
                            NodeCraft.LOGGER.warn("加载节点图失败: {} - {}", selectedPath, error);
                        }
                    } else {
                        NodeCraft.LOGGER.error("无法获取NodeIO组件");
                    }
                }
            }
        );
    }
    
    /**
     * 保存节点图（包级可见，供快捷键调用）
     * @param saveAs 是否另存为
     */
    void saveNodeGraph(boolean saveAs) {
        saveNodeGraph(saveAs, null);
    }
    
    /**
     * 保存节点图
     * @param saveAs 是否另存为
     * @param onSaveComplete 保存完成后的回调
     */
    private void saveNodeGraph(boolean saveAs, Runnable onSaveComplete) {
        try {
            CanvasComponent canvas = componentManager.getCanvasComponent();
            if (canvas != null && canvas.getNodeEditor() instanceof ImGuiNodeEditor editor) {
                ImGuiNodeIO io = editor.getNodeIO();
                if (io == null) {
                    NodeCraft.LOGGER.error("无法获取NodeIO组件");
                    return;
                }
                
                // 确定保存路径
                Path savePath = lastSavedPath;
                
                // 如果是另存为或者还没有保存过，显示保存对话框
                if (saveAs || savePath == null) {
                    // 使用文件对话框管理器的回调方式
                    FileDialogManager.showFileDialog(
                        "保存节点图",
                        getDefaultDirectory(),
                        NODE_GRAPH_FILTER,
                        true, // 是保存对话框
                        DEFAULT_EXTENSION,
                        selectedPath -> {
                            if (selectedPath != null) {
                                // 更新最近保存路径
                                // 保存文件
                                boolean saved = io.saveGraph(selectedPath);
                                if (saved) {
                                    lastSavedPath = selectedPath;
                                    NodeCraft.LOGGER.info("成功保存节点图: {}", selectedPath);
                                    if (onSaveComplete != null) {
                                        onSaveComplete.run();
                                    }
                                } else {
                                    String error = io.getLastOperationError();
                                    if (error == null || error.isBlank()) {
                                        error = "保存失败：请检查路径和写入权限。";
                                    }
                                    new MessageDialog("保存节点图失败", error).show();
                                    NodeCraft.LOGGER.warn("保存节点图失败: {} - {}", selectedPath, error);
                                }
                            }
                        }
                    );
                } else {
                    // 直接保存到已有路径
                    boolean saved = io.saveGraph(savePath);
                    if (saved) {
                        NodeCraft.LOGGER.info("成功保存节点图: {}", savePath);
                        if (onSaveComplete != null) {
                            onSaveComplete.run();
                        }
                    } else {
                        String error = io.getLastOperationError();
                        if (error == null || error.isBlank()) {
                            error = "保存失败：请检查路径和写入权限。";
                        }
                        new MessageDialog("保存节点图失败", error).show();
                        NodeCraft.LOGGER.warn("保存节点图失败: {} - {}", savePath, error);
                    }
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("保存节点图失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取默认目录
     */
    private Path getDefaultDirectory() {
        // 如果有最近保存的路径，使用其父目录
        if (lastSavedPath != null) {
            Path parent = lastSavedPath.getParent();
            if (parent != null) {
                return parent;
            }
        }
        
        // 否则使用当前工作目录
        return Paths.get("").toAbsolutePath();
    }

    /**
     * 获取NodecraftScreen实例，如果可能的话
     * @return NodecraftScreen实例，如果无法获取则返回null
     */
    private NodecraftScreen getNodecraftScreen() {
        if (componentManager == null) {
            return null;
        }
        
        // 通过组件管理器获取所属的NodecraftScreen
        // 此方法假设ComponentManager包含了获取屏幕的方法，或者可以通过其他方式获取
        if (componentManager.getParentScreen() instanceof NodecraftScreen) {
            return (NodecraftScreen) componentManager.getParentScreen();
        }
        
        return null;
    }

    /**
     * 调试历史记录功能
     */
    private void debugHistoryFunction() {
        try {
            CanvasComponent canvas = componentManager.getCanvasComponent();
            if (canvas != null && canvas.getNodeEditor() instanceof ImGuiNodeEditor editor) {
                ImGuiNodeHistory history = editor.getHistory();
                
                if (history != null) {
                    String testResult = history.testHistoryFunction();
                    NodeCraft.LOGGER.info("历史记录调试信息:\n{}", testResult);
                    
                    // 也在控制台输出
                    System.out.println("=== 历史记录调试信息 ===");
                    System.out.println(testResult);
                    System.out.println("========================");
                } else {
                    NodeCraft.LOGGER.error("历史记录组件为空");
                }
            } else {
                NodeCraft.LOGGER.error("无法获取编辑器实例");
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("调试历史记录功能时出错: {}", e.getMessage(), e);
        }
    }
    
    // === 节点图执行相关方法 ===
    
    /**
     * 从快捷键触发执行当前节点图
     */
    void executeCurrentGraph() {
        try {
            CanvasComponent execCanvas = componentManager != null ? componentManager.getCanvasComponent() : null;
            NodeGraph execGraph = null;
            if (execCanvas != null && execCanvas.getNodeEditor() instanceof ImGuiNodeEditor) {
                ImGuiNodeEditor execEditor = (ImGuiNodeEditor) execCanvas.getNodeEditor();
                execGraph = execEditor.getCurrentGraph();
            }
            boolean isExecuting = currentExecutor != null && currentExecutor.isExecuting();
            if (execGraph != null && !execGraph.getNodes().isEmpty() && !isExecuting) {
                executeNodeGraph(execGraph);
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("快捷键执行节点图失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查当前是否正在执行
     */
    boolean isExecuting() {
        return currentExecutor != null && currentExecutor.isExecuting();
    }

    /**
     * 执行节点图
     * @param graph 要执行的节点图
     */
    private void executeNodeGraph(NodeGraph graph) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) {
                executionStatus = "失败: 未进入世界";
                executionStatusTime = System.currentTimeMillis();
                NodeCraft.LOGGER.error("无法执行节点图: 客户端未进入世界");
                return;
            }
            
            // 尝试获取服务端世界和玩家（单人模式下）
            World world = client.world;
            ServerPlayerEntity serverPlayer = null;
            
            IntegratedServer integratedServer = client.getServer();
            if (integratedServer != null && client.player != null) {
                serverPlayer = integratedServer.getPlayerManager()
                    .getPlayer(client.player.getUuid());
                if (serverPlayer != null) {
                    // 使用服务端的主世界维度
                    world = integratedServer.getOverworld();
                }
            }
            
            // 创建执行上下文
            ExecutionContext context = new ExecutionContext(world, serverPlayer);
            
            // 创建执行器并异步执行
            currentExecutor = new NodeExecutor(graph, context);
            
            NodeCraft.LOGGER.info("开始执行节点图: {} 个节点", graph.getNodes().size());
            executionStatus = null;
            
            currentExecutor.executeAsync().thenAccept(result -> {
                if (result) {
                    executionStatus = "成功完成";
                    NodeCraft.LOGGER.info("节点图执行成功");
                } else {
                    executionStatus = "执行失败";
                    NodeCraft.LOGGER.warn("节点图执行失败");
                }
                executionStatusTime = System.currentTimeMillis();
                currentExecutor = null;
            }).exceptionally(throwable -> {
                executionStatus = "错误: " + throwable.getMessage();
                executionStatusTime = System.currentTimeMillis();
                NodeCraft.LOGGER.error("节点图执行时发生异常", throwable);
                currentExecutor = null;
                return null;
            });
            
        } catch (Exception e) {
            executionStatus = "错误: " + e.getMessage();
            executionStatusTime = System.currentTimeMillis();
            NodeCraft.LOGGER.error("启动节点图执行失败", e);
            currentExecutor = null;
        }
    }
    
    /**
     * 停止当前执行（包级可见，供快捷键调用）
     */
    void stopExecution() {
        if (currentExecutor != null) {
            currentExecutor.stop();
            executionStatus = "已取消";
            executionStatusTime = System.currentTimeMillis();
            NodeCraft.LOGGER.info("节点图执行已取消");
            currentExecutor = null;
        }
    }
}
