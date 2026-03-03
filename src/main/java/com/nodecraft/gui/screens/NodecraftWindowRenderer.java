package com.nodecraft.gui.screens;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.editor.integration.ImGuiRenderer;
import com.nodecraft.gui.style.ImGuiStyleScope;
import com.nodecraft.gui.style.MinecraftTheme;
import com.nodecraft.gui.utils.ImGuiStyleVar;
import com.nodecraft.gui.window.ViewportCloseDetector;
import com.nodecraft.gui.window.WindowManager;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.gui.DrawContext;

/**
 * NodeCraft编辑器窗口渲染器
 * 负责管理ImGui窗口的渲染和样式
 */
public class NodecraftWindowRenderer {
    
    private final NodecraftScreen parentScreen;
    private final ImGuiRenderer imGuiRenderer;
    private final WindowManager windowManager;
    private final ViewportCloseDetector closeDetector;
    private final MinecraftTheme theme;
    private ImGuiStyleScope styleScope;
    
    public NodecraftWindowRenderer(NodecraftScreen parentScreen) {
        this.parentScreen = parentScreen;
        this.imGuiRenderer = ImGuiRenderer.getInstance();
        this.windowManager = WindowManager.getInstance();
        this.closeDetector = ViewportCloseDetector.getInstance();
        this.theme = new MinecraftTheme();
        this.styleScope = new ImGuiStyleScope();
    }
    
    /**
     * 渲染完整的窗口框架
     */
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        try {
            // 开始ImGui帧
            imGuiRenderer.beginFrame();
            
            try {
                // 渲染主窗口内容
                renderWithStyles(context, mouseX, mouseY, delta);
            } finally {
                // 确保结束ImGui帧
                imGuiRenderer.endFrame();
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染错误: {}", e.getMessage(), e);
            parentScreen.closeRequested = true;
        }
    }
    
    /**
     * 应用样式并渲染内容
     */
    private void renderWithStyles(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean stylesApplied = false;
        try {
            applyStyles();
            stylesApplied = true;
            
            renderMainWindow(context, mouseX, mouseY, delta);
            
            if (!parentScreen.closeRequested) {
                renderDialogs();
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染ImGui界面错误: {}", e.getMessage(), e);
        } finally {
            if (stylesApplied) {
                popStyles();
            }
        }
    }
    
    /**
     * 渲染主编辑器窗口
     */
    private void renderMainWindow(DrawContext context, int mouseX, int mouseY, float delta) {
        // 检查是否启用了 ViewportsEnable
        boolean viewportsEnabled = ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable);
        
        // 设置窗口位置和大小
        ImGui.setNextWindowPos(parentScreen.windowX, parentScreen.windowY, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(parentScreen.windowWidth, parentScreen.windowHeight, ImGuiCond.FirstUseEver);
        
        // 创建窗口标志
        int windowFlags = createWindowFlags(viewportsEnabled);
        
        // 自定义窗口标题
        String windowTitle = viewportsEnabled ?
                "NodeCraft 编辑器 - 独立窗口模式" : "NodeCraft 编辑器";
        
        boolean windowOpened = false;
        try {
            windowOpened = ImGui.begin(windowTitle, closeDetector.getWindowOpenFlag(), windowFlags);
            
            // 检查窗口关闭请求
            if (!closeDetector.getWindowOpenFlag().get()) {
                parentScreen.closeRequested = true;
                return;
            }
            
            if (windowOpened) {
                handleWindowAssociation();
                updateWindowDimensions();
                renderWindowContent(context, mouseX, mouseY, delta);
            }
        } finally {
            ImGui.end();
        }
    }
    
    private int createWindowFlags(boolean viewportsEnabled) {
        int windowFlags = ImGuiWindowFlags.MenuBar | ImGuiWindowFlags.NoCollapse;
        
        if (viewportsEnabled) {
            windowFlags |= ImGuiWindowFlags.NoDocking;
        }
        
        // 根据交互状态调整窗口标志
        LayoutRenderer layoutRenderer = parentScreen.getLayoutRenderer();
        if (layoutRenderer != null) {
            boolean isDraggingSplitter = layoutRenderer.isDraggingSplitter();
            boolean isHoveringSplitter = layoutRenderer.isHoveringSplitter();
            
            // 检查是否有自定义UI处于激活状态
            boolean hasCustomUIActive = false;
            if (parentScreen.getCurrentEditor() != null && parentScreen.getCurrentEditor() instanceof com.nodecraft.gui.editor.impl.ICanvasEditor canvasEditor) {
                hasCustomUIActive = canvasEditor.getInteraction().isNodeCustomUIActive();
            }
            
            if (isDraggingSplitter || isHoveringSplitter || hasCustomUIActive) {
                // 添加NoMove标志，完全禁止窗口移动
                windowFlags |= ImGuiWindowFlags.NoMove;

                if (isDraggingSplitter) {
                    // 拖拽分隔线时还要禁用标题栏，防止通过标题栏拖动
                    windowFlags |= ImGuiWindowFlags.NoTitleBar;
                    if (NodeCraft.LOGGER.isDebugEnabled()) {
                        NodeCraft.LOGGER.debug("正在拖拽分隔线，已禁用窗口移动和标题栏");
                    }
                } else if (hasCustomUIActive) {
                    if (NodeCraft.LOGGER.isDebugEnabled()) {
                        NodeCraft.LOGGER.debug("自定义UI激活，已禁用窗口移动");
                    }
                } else {
                    if (NodeCraft.LOGGER.isDebugEnabled()) {
                        NodeCraft.LOGGER.debug("悬停在分隔线上，已禁用窗口移动");
                    }
                }
            }
        }
        
        return windowFlags;
    }
    
    private void handleWindowAssociation() {
        if (!windowManager.isWindowAssociated()) {
            windowManager.associateNodeCraftWindow();
        }
    }
    
    private void updateWindowDimensions() {
        parentScreen.windowWidth = ImGui.getWindowWidth();
        parentScreen.windowHeight = ImGui.getWindowHeight();
        parentScreen.windowX = ImGui.getWindowPosX();
        parentScreen.windowY = ImGui.getWindowPosY();
    }
    
    private void renderWindowContent(DrawContext context, int mouseX, int mouseY, float delta) {
        // 渲染菜单栏
        MenuBarRenderer menuBarRenderer = parentScreen.getMenuBarRenderer();
        LayoutRenderer layoutRenderer = parentScreen.getLayoutRenderer();
        
        boolean isDraggingSplitter = layoutRenderer != null && layoutRenderer.isDraggingSplitter();
        boolean isHoveringSplitter = layoutRenderer != null && layoutRenderer.isHoveringSplitter();
        
        if (parentScreen.showMenuBar && !(isDraggingSplitter || isHoveringSplitter)) {
            if (menuBarRenderer != null) {
                try {
                    menuBarRenderer.render();
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("渲染菜单栏时出错: {}", e.getMessage(), e);
                }
            }
        }
        
        // 渲染主布局
        if (layoutRenderer != null) {
            try {
                layoutRenderer.render(context, mouseX, mouseY, delta);
            } catch (Exception e) {
                NodeCraft.LOGGER.error("渲染布局时出错: {}", e.getMessage(), e);
            }
        }
    }
    
    private void renderDialogs() {
        // 渲染各种对话框
        try {
            com.nodecraft.gui.dialogs.FileDialogManager.renderFileDialogs();
            com.nodecraft.gui.dialogs.ConfirmationDialog.renderDialog();
            com.nodecraft.gui.dialogs.MessageDialog.renderDialog();
        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染对话框时出错: {}", e.getMessage(), e);
        }
    }
    
    private void applyStyles() {
        theme.apply(styleScope);
        styleScope.pushStyleVar(ImGuiStyleVar.WindowPadding, 
            EditorConstants.DEFAULT_WINDOW_PADDING, EditorConstants.DEFAULT_WINDOW_PADDING);
        styleScope.pushStyleVar(ImGuiStyleVar.ItemSpacing, 
            EditorConstants.DEFAULT_ITEM_SPACING, EditorConstants.DEFAULT_ITEM_SPACING);
    }
    
    private void popStyles() {
        if (styleScope != null) {
            try {
                styleScope.popAll();
            } catch (Exception e) {
                NodeCraft.LOGGER.error("弹出样式时出错: {}", e.getMessage());
            }
        }
    }
    
    public void cleanup() {
        if (styleScope != null) {
            try {
                styleScope.close();
            } catch (Exception e) {
                NodeCraft.LOGGER.error("清理样式作用域时出错: {}", e.getMessage());
            } finally {
                styleScope = null;
            }
        }
    }
} 