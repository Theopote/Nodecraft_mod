package com.nodecraft.gui.window;

import com.nodecraft.core.NodeCraft;
import imgui.ImGui;
import imgui.flag.ImGuiConfigFlags;
import imgui.type.ImBoolean;
import net.minecraft.client.MinecraftClient;

/**
 * Viewport关闭检测器
 * 专门用于在ViewportsEnable模式下检测窗口关闭事件
 */
public class ViewportCloseDetector {
    private static volatile ViewportCloseDetector INSTANCE;
    
    private boolean lastFrameHadNodeCraftWindow = false;
    private boolean isViewportsEnabled = false;
    private int consecutiveFramesWithoutWindow = 0;
    private static final int CLOSE_DETECTION_THRESHOLD = 2; // 减少到2帧提高响应速度
    
    // 用于检测ImGui窗口关闭的标志
    private final ImBoolean windowOpen = new ImBoolean(true);
    private boolean windowWasOpen = true;
    
    private ViewportCloseDetector() {}
    
    public static ViewportCloseDetector getInstance() {
        if (INSTANCE == null) {
            synchronized (ViewportCloseDetector.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ViewportCloseDetector();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * 初始化检测器
     */
    public void initialize() {
        try {
            isViewportsEnabled = ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable);
            windowOpen.set(true);
            windowWasOpen = true;
            NodeCraft.LOGGER.info("Viewport关闭检测器初始化，ViewportsEnable: {}", isViewportsEnabled);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("初始化Viewport关闭检测器失败", e);
            isViewportsEnabled = false;
        }
    }
    
    /**
     * 在每帧开始时调用，记录窗口状态
     */
    public void onFrameStart() {
        if (!isViewportsEnabled) {
            return;
        }
        
        try {
            // 检查当前是否有NodeCraft屏幕
            MinecraftClient client = MinecraftClient.getInstance();
            boolean hasNodeCraftScreen = client.currentScreen instanceof com.nodecraft.gui.screens.NodecraftScreen;
            
            if (hasNodeCraftScreen) {
                // 重置计数器
                consecutiveFramesWithoutWindow = 0;
                lastFrameHadNodeCraftWindow = true;
                
                // 确保窗口标志为打开状态
                if (!windowOpen.get()) {
                    windowOpen.set(true);
                    windowWasOpen = true;
                }
            } else if (lastFrameHadNodeCraftWindow) {
                // 上一帧有NodeCraft窗口，这一帧没有，开始计数
                consecutiveFramesWithoutWindow++;
                
                if (consecutiveFramesWithoutWindow >= CLOSE_DETECTION_THRESHOLD) {
                    // 连续多帧没有窗口，认为窗口被关闭了
                    onWindowClosed();
                    lastFrameHadNodeCraftWindow = false;
                    consecutiveFramesWithoutWindow = 0;
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("检测窗口状态时出错", e);
        }
    }
    
    /**
     * 在每帧结束时调用，检测窗口关闭
     */
    public void onFrameEnd() {
        if (!isViewportsEnabled) {
            return;
        }
        
        try {
            // 检查ImGui窗口关闭标志
            if (windowWasOpen && !windowOpen.get()) {
                NodeCraft.LOGGER.info("检测到ImGui窗口关闭标志，执行关闭");
                onImGuiWindowClosed();
                windowWasOpen = false;
                return;
            }
            
            // 检查是否有NodeCraft屏幕标记为需要关闭
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen instanceof com.nodecraft.gui.screens.NodecraftScreen nodecraftScreen) {

                if (nodecraftScreen.isCloseRequested()) {
                    NodeCraft.LOGGER.info("检测到NodeCraft屏幕关闭请求，执行关闭");
                    onWindowCloseRequested(nodecraftScreen);
                }
            }
            
            // 更新窗口状态
            windowWasOpen = windowOpen.get();
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("检测窗口关闭请求时出错", e);
        }
    }
    
    /**
     * 获取窗口打开状态标志，供ImGui窗口使用
     */
    public ImBoolean getWindowOpenFlag() {
        return windowOpen;
    }
    
    /**
     * 当检测到窗口被关闭时调用
     */
    private void onWindowClosed() {
        try {
            NodeCraft.LOGGER.info("检测到NodeCraft窗口被关闭");
            
            // 清理窗口管理器状态
            WindowManager windowManager = WindowManager.getInstance();
            if (windowManager.isWindowAssociated()) {
                windowManager.disassociateNodeCraftWindow();
            }
            
            // 确保屏幕被关闭
            MinecraftClient.getInstance().execute(() -> {
                try {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.currentScreen instanceof com.nodecraft.gui.screens.NodecraftScreen screen) {
                        screen.requestClose();
                    }
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("关闭NodeCraft屏幕时出错", e);
                }
            });
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("处理窗口关闭事件时出错", e);
        }
    }
    
    /**
     * 当检测到ImGui窗口关闭时调用
     */
    private void onImGuiWindowClosed() {
        try {
            NodeCraft.LOGGER.info("检测到ImGui窗口关闭");
            
            // 立即关闭NodeCraft屏幕
            MinecraftClient.getInstance().execute(() -> {
                try {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.currentScreen instanceof com.nodecraft.gui.screens.NodecraftScreen screen) {
                        screen.requestClose();
                        NodeCraft.LOGGER.info("NodeCraft屏幕已关闭");
                    }
                    
                    // 清理窗口管理器状态
                    WindowManager windowManager = WindowManager.getInstance();
                    if (windowManager.isWindowAssociated()) {
                        windowManager.disassociateNodeCraftWindow();
                    }
                    
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("关闭NodeCraft屏幕时出错", e);
                }
            });
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("处理ImGui窗口关闭时出错", e);
        }
    }
    
    /**
     * 当检测到窗口关闭请求时调用
     */
    private void onWindowCloseRequested(com.nodecraft.gui.screens.NodecraftScreen screen) {
        try {
            NodeCraft.LOGGER.info("处理NodeCraft窗口关闭请求");
            
            // 强制关闭屏幕
            MinecraftClient.getInstance().execute(() -> {
                try {
                    screen.requestClose();
                    NodeCraft.LOGGER.info("NodeCraft屏幕已强制关闭");
                    
                    // 清理窗口管理器状态
                    WindowManager windowManager = WindowManager.getInstance();
                    if (windowManager.isWindowAssociated()) {
                        windowManager.disassociateNodeCraftWindow();
                    }
                    
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("强制关闭NodeCraft屏幕时出错", e);
                }
            });
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("处理窗口关闭请求时出错", e);
        }
    }
    
    /**
     * 手动触发窗口关闭
     */
    public void forceCloseWindow() {
        try {
            NodeCraft.LOGGER.info("手动触发NodeCraft窗口关闭");
            
            // 设置窗口关闭标志
            windowOpen.set(false);
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen instanceof com.nodecraft.gui.screens.NodecraftScreen nodecraftScreen) {

                onWindowCloseRequested(nodecraftScreen);
            }
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("手动关闭窗口时出错", e);
        }
    }
    
    /**
     * 重置检测器状态
     */
    public void reset() {
        lastFrameHadNodeCraftWindow = false;
        consecutiveFramesWithoutWindow = 0;
        windowOpen.set(true);
        windowWasOpen = true;
        NodeCraft.LOGGER.debug("Viewport关闭检测器状态已重置");
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        reset();
        isViewportsEnabled = false;
        NodeCraft.LOGGER.info("Viewport关闭检测器已清理");
    }
} 
