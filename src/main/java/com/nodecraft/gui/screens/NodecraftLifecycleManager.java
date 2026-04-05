package com.nodecraft.gui.screens;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.core.event.EditorEventListener;
import com.nodecraft.core.event.EditorUIEvent;
import com.nodecraft.gui.editor.NodeEditorFactory;
import com.nodecraft.gui.editor.base.INodeEditor;
import com.nodecraft.gui.layout.StandardLayoutManager;
import com.nodecraft.gui.window.ViewportCloseDetector;
import com.nodecraft.minecraft.client.GhostCameraManager;
import com.nodecraft.nodesystem.preview.TrackedPreviewPlacementService;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import net.minecraft.client.MinecraftClient;

/**
 * NodeCraft编辑器生命周期管理器
 * 负责编辑器的初始化和清理工作
 */
public class NodecraftLifecycleManager {
    
    private final NodecraftScreen parentScreen;
    private final GhostCameraManager ghostCameraManager;
    
    public NodecraftLifecycleManager(NodecraftScreen parentScreen, GhostCameraManager ghostCameraManager) {
        this.parentScreen = parentScreen;
        this.ghostCameraManager = ghostCameraManager;
    }
    
    /**
     * 执行完整的初始化流程
     */
    public void initialize() throws Exception {
        NodeCraft.LOGGER.info("开始初始化NodeCraft编辑器...");

        // 重置窗口关闭检测器状态，避免历史关闭标志影响重新打开
        ViewportCloseDetector.getInstance().reset();
        
        // 启用幽灵相机模式
        ghostCameraManager.enable();
        NodeCraft.LOGGER.info("幽灵相机模式已启用");
        
        // 计算窗口初始尺寸
        calculateInitialWindowSize();
        
        // 初始化所有组件
        initializeComponents();
        
        NodeCraft.LOGGER.info("NodeCraft编辑器初始化完成");
    }
    
    /**
     * 执行完整的清理流程
     */
    public void cleanup() {
        NodeCraft.LOGGER.info("开始清理NodeCraft编辑器资源...");
        
        try {
            // 确保幽灵相机模式被禁用
            ghostCameraManager.forceCleanup();
            
            // 清除所有预览渲染元素
            com.nodecraft.nodesystem.preview.PreviewRenderer.getInstance().clearAllPreviews();
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                TrackedPreviewPlacementService.getInstance().clearAllTrackedPreviews(client.world);
            }
            NodeCraft.LOGGER.info("所有预览元素已清除");
            
            // 退出编辑模式
            exitEditorMode();
            
            // 强制退出所有交互模式
            com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager interactionManager = 
                com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager.getInstance();
            interactionManager.forceExitAllInteractions();
            
            // 确保Minecraft客户端控制器状态被清理
            com.nodecraft.minecraft.client.MinecraftClientController.getInstance().forceCleanup();
            
            // 清理编辑器组件
            cleanupEditorComponents();
            
            // 重置窗口管理器关联
            cleanupWindowAssociation();
            
            NodeCraft.LOGGER.info("NodeCraft编辑器资源清理完成");
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("清理过程中发生意外错误: {}", e.getMessage(), e);
        }
    }
    
    private void calculateInitialWindowSize() {
        MinecraftClient client = MinecraftClient.getInstance();
        float baseWidth = parentScreen.width;
        float baseHeight = parentScreen.height;

        if (client != null && client.getWindow() != null) {
            baseWidth = client.getWindow().getWidth();
            baseHeight = client.getWindow().getHeight();
        }

        float screenWidth = Math.max(baseWidth, EditorConstants.MIN_WINDOW_WIDTH + EditorConstants.WINDOW_EDGE_MARGIN * 2);
        float screenHeight = Math.max(baseHeight, EditorConstants.MIN_WINDOW_HEIGHT + EditorConstants.WINDOW_EDGE_MARGIN * 2);
        
        float desiredWidth = screenWidth * EditorConstants.SCREEN_WIDTH_RATIO;
        float desiredHeight = screenHeight * EditorConstants.SCREEN_HEIGHT_RATIO;
        
        float effectiveScreenWidth = screenWidth - EditorConstants.WINDOW_EDGE_MARGIN * 2;
        float effectiveScreenHeight = screenHeight - EditorConstants.WINDOW_EDGE_MARGIN * 2;
        
        parentScreen.windowWidth = clamp(desiredWidth, EditorConstants.MIN_WINDOW_WIDTH,
                Math.min(EditorConstants.MAX_WINDOW_WIDTH, effectiveScreenWidth));
        parentScreen.windowHeight = clamp(desiredHeight, EditorConstants.MIN_WINDOW_HEIGHT,
                Math.min(EditorConstants.MAX_WINDOW_HEIGHT, effectiveScreenHeight));
        
        parentScreen.windowX = Math.max(EditorConstants.WINDOW_EDGE_MARGIN, (screenWidth - parentScreen.windowWidth) / 2);
        parentScreen.windowY = Math.max(EditorConstants.WINDOW_EDGE_MARGIN, (screenHeight - parentScreen.windowHeight) / 2);
        
        NodeCraft.LOGGER.debug("初始窗口尺寸: {}x{}, 位置: {},{}", 
            parentScreen.windowWidth, parentScreen.windowHeight, parentScreen.windowX, parentScreen.windowY);
    }
    
    private void initializeComponents() {
        // 1. 创建编辑器实例
        INodeEditor currentEditor = NodeEditorFactory.createEditor();
        if (currentEditor == null) {
            throw new IllegalStateException("无法创建节点编辑器实例");
        }
        
        parentScreen.setCurrentEditor(currentEditor);
        
        // 2. 注册编辑器作为事件监听器（如果它实现了该接口）
        if (currentEditor instanceof EditorEventListener) {
            parentScreen.getEventBus().registerListener((EditorEventListener) currentEditor);
        }
        currentEditor.open();
        
        // 3. 激活编辑模式
        activateEditorMode();
        
        // 4. 验证节点注册表
        validateNodeRegistry();
        
        // 5. 创建布局管理器
        StandardLayoutManager layoutManager = new StandardLayoutManager();
        layoutManager.setDefaultSpacing(EditorConstants.DEFAULT_LAYOUT_SPACING);
        parentScreen.setLayoutManager(layoutManager);
        NodeCraft.LOGGER.info("布局管理器初始化完成");
        
        // 6. 创建组件管理器
        ComponentManager componentManager = new ComponentManager(layoutManager, currentEditor);
        componentManager.setParentScreen(parentScreen);
        componentManager.initComponents();
        parentScreen.setComponentManager(componentManager);
        
        // 7. 创建渲染器
        createRenderers(layoutManager, componentManager);
        
        // 8. 将组件管理器注册为事件监听器（如果它实现了该接口）

        // 9. 注册主屏幕自身作为监听器，用于处理高级事件
        if (parentScreen instanceof EditorEventListener) {
            parentScreen.getEventBus().registerListener((EditorEventListener) parentScreen);
        }
        
        // 10. 发布初始化完成事件
        parentScreen.postEvent(new EditorUIEvent(EditorUIEvent.Type.LAYOUT_CHANGED, "main_screen"));
        
        NodeCraft.LOGGER.info("所有组件初始化完成");
    }
    
    private void activateEditorMode() {
        com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager interactionManager = 
            com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager.getInstance();
        interactionManager.enterEditorMode();
        NodeCraft.LOGGER.info("编辑器交互模式已激活");
    }
    
    private void validateNodeRegistry() {
        NodeRegistry registry = NodeRegistry.getInstance();
        if (!registry.isInitialized()) {
            NodeCraft.LOGGER.warn("警告: NodeRegistry 在进入 NodecraftScreen 时仍未初始化！");
        }
    }
    
    private void createRenderers(StandardLayoutManager layoutManager, ComponentManager componentManager) {
        // 创建布局渲染器
        LayoutRenderer layoutRenderer = new LayoutRenderer(
            layoutManager, componentManager, parentScreen.getLayoutConfig(), parentScreen.isShowMenuBar());
        layoutRenderer.setEmphasizeCanvas(true);
        parentScreen.setLayoutRenderer(layoutRenderer);
        
        // 创建菜单栏渲染器
        MenuBarRenderer menuBarRenderer = new MenuBarRenderer(
            componentManager,
            () -> parentScreen.closeRequested = true
        );
        parentScreen.setMenuBarRenderer(menuBarRenderer);
        
        // 创建窗口渲染器
        NodecraftWindowRenderer windowRenderer = new NodecraftWindowRenderer(parentScreen);
        parentScreen.setWindowRenderer(windowRenderer);
    }
    
    private void exitEditorMode() {
        com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager interactionManager = 
            com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager.getInstance();
        interactionManager.exitEditorMode();
        NodeCraft.LOGGER.info("编辑器交互模式已退出");
    }
    
    private void cleanupEditorComponents() {
        // 发布编辑器关闭事件
        parentScreen.postEvent(new EditorUIEvent(EditorUIEvent.Type.COMPONENT_HIDDEN, "editor_screen"));
        
        // 清理编辑器实例
        INodeEditor currentEditor = parentScreen.getCurrentEditor();
        if (currentEditor != null) {
            try {
                if (currentEditor instanceof EditorEventListener) {
                    parentScreen.getEventBus().unregisterListener((EditorEventListener) currentEditor);
                }
                currentEditor.close();
                NodeCraft.LOGGER.debug("编辑器实例已关闭");
            } catch (Exception e) {
                NodeCraft.LOGGER.error("关闭编辑器实例时出错: {}", e.getMessage(), e);
            } finally {
                parentScreen.setCurrentEditor(null);
            }
        }
        
        // 清理组件管理器
        ComponentManager componentManager = parentScreen.getComponentManager();
        if (componentManager != null) {
            try {
                if (componentManager instanceof EditorEventListener) {
                    parentScreen.getEventBus().unregisterListener((EditorEventListener) componentManager);
                }
                componentManager.cleanup();
                NodeCraft.LOGGER.debug("组件管理器已清理");
            } catch (Exception e) {
                NodeCraft.LOGGER.error("清理组件管理器时出错: {}", e.getMessage(), e);
            } finally {
                parentScreen.setComponentManager(null);
            }
        }
        
        // 清理渲染器
        NodecraftWindowRenderer windowRenderer = parentScreen.getWindowRenderer();
        if (windowRenderer != null) {
            windowRenderer.cleanup();
            parentScreen.setWindowRenderer(null);
        }
        
        // 清理其他渲染器引用
        parentScreen.setLayoutRenderer(null);
        parentScreen.setMenuBarRenderer(null);
        parentScreen.setLayoutManager(null);
    }
    
    private void cleanupWindowAssociation() {
        com.nodecraft.gui.window.WindowManager windowManager = 
            com.nodecraft.gui.window.WindowManager.getInstance();
        if (windowManager.isWindowAssociated()) {
            windowManager.disassociateNodeCraftWindow();
        }
    }
    
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
} 
