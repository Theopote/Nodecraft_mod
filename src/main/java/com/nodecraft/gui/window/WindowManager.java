package com.nodecraft.gui.window;

import com.nodecraft.core.NodeCraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NodeCraft窗口管理器
 * 负责管理NodeCraft窗口与Minecraft主窗口的关联关系
 * 确保NodeCraft窗口始终保持在正确的层级位置
 */
public class WindowManager {
    private static WindowManager INSTANCE;
    
    private final AtomicLong minecraftWindowHandle = new AtomicLong(0);
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isWindowAssociated = new AtomicBoolean(false);
    
    // 窗口状态监控
    private boolean lastMinecraftVisible = true;
    private boolean lastMinecraftFocused = true;
    
    // 跟踪所有NodeCraft相关的窗口句柄
    private final Set<Long> nodecraftWindowHandles = ConcurrentHashMap.newKeySet();
    
    // 窗口层级强制更新计数器
    private int layeringUpdateCounter = 0;
    private static final int LAYERING_UPDATE_INTERVAL = 30; // 每30帧强制更新一次层级
    
    private WindowManager() {}
    
    public static WindowManager getInstance() {
        if (INSTANCE == null) {
            synchronized (WindowManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new WindowManager();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * 初始化窗口管理器
     */
    public void initialize() {
        if (isInitialized.get()) {
            return;
        }
        
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                NodeCraft.LOGGER.warn("Minecraft客户端未初始化，无法设置窗口关联");
                return;
            }
            
            Window window = client.getWindow();
            if (window == null) {
                NodeCraft.LOGGER.warn("Minecraft窗口未初始化，无法设置窗口关联");
                return;
            }
            
            long windowHandle = window.getHandle();
            if (windowHandle == 0) {
                NodeCraft.LOGGER.warn("无法获取Minecraft窗口句柄");
                return;
            }
            
            minecraftWindowHandle.set(windowHandle);
            setupWindowCallbacks();
            
            isInitialized.set(true);
            NodeCraft.LOGGER.info("窗口管理器初始化成功，Minecraft窗口句柄: {}", windowHandle);
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("初始化窗口管理器失败", e);
        }
    }
    
    /**
     * 设置窗口回调函数
     */
    private void setupWindowCallbacks() {
        long handle = minecraftWindowHandle.get();
        if (handle == 0) {
            return;
        }
        
        // 设置窗口焦点回调
        GLFW.glfwSetWindowFocusCallback(handle, (window, focused) -> {
            lastMinecraftFocused = focused;
            NodeCraft.LOGGER.debug("Minecraft窗口焦点变化: {}", focused);
            
            if (focused) {
                // Minecraft获得焦点时，确保NodeCraft窗口在正确位置
                ensureNodeCraftWindowOnTop();
            }
        });
        
        // 设置窗口图标化回调（最小化）
        GLFW.glfwSetWindowIconifyCallback(handle, (window, iconified) -> {
            lastMinecraftVisible = !iconified;
            NodeCraft.LOGGER.debug("Minecraft窗口可见性变化: {}", !iconified);
            
            if (iconified) {
                // Minecraft最小化时隐藏NodeCraft窗口
                hideNodeCraftWindows();
            } else {
                // Minecraft恢复时显示NodeCraft窗口
                showNodeCraftWindows();
            }
        });
        
        // 设置窗口位置回调
        GLFW.glfwSetWindowPosCallback(handle, (window, xpos, ypos) -> {
            // Minecraft窗口移动时，确保NodeCraft窗口层级正确
            ensureNodeCraftWindowOnTop();
        });
    }
    
    /**
     * 注册NodeCraft窗口句柄
     * @param windowHandle 窗口句柄
     */
    public void registerNodeCraftWindow(long windowHandle) {
        if (windowHandle != 0) {
            // 检查是否是Minecraft主窗口
            long minecraftHandle = minecraftWindowHandle.get();
            if (windowHandle == minecraftHandle) {
                NodeCraft.LOGGER.debug("跳过注册Minecraft主窗口句柄: {}", windowHandle);
                return;
            }
            
            nodecraftWindowHandles.add(windowHandle);
            NodeCraft.LOGGER.debug("注册NodeCraft窗口句柄: {}", windowHandle);
            
            // 不立即设置层级，让ImGui自然管理
        }
    }
    
    /**
     * 注销NodeCraft窗口句柄
     * @param windowHandle 窗口句柄
     */
    public void unregisterNodeCraftWindow(long windowHandle) {
        if (nodecraftWindowHandles.remove(windowHandle)) {
            NodeCraft.LOGGER.debug("注销NodeCraft窗口句柄: {}", windowHandle);
        }
    }
    
    /**
     * 确保NodeCraft窗口在Minecraft窗口之上
     */
    public void ensureNodeCraftWindowOnTop() {
        if (!isInitialized.get()) {
            return;
        }
        
        try {
            long minecraftHandle = minecraftWindowHandle.get();
            if (minecraftHandle == 0) {
                return;
            }
            
            // 温和的层级管理 - 不使用FLOATING模式
            // 只确保Minecraft窗口不是总在最前面
            MinecraftClient.getInstance().execute(() -> {
                try {
                    if (!GLFW.glfwWindowShouldClose(minecraftHandle)) {
                        // 确保Minecraft窗口不是浮动窗口
                        GLFW.glfwSetWindowAttrib(minecraftHandle, GLFW.GLFW_FLOATING, GLFW.GLFW_FALSE);
                    }
                } catch (Exception e) {
                    NodeCraft.LOGGER.debug("设置Minecraft窗口属性时出错: {}", e.getMessage());
                }
            });
            
            NodeCraft.LOGGER.debug("已更新窗口层级（温和模式），NodeCraft窗口数量: {}", nodecraftWindowHandles.size());
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("设置窗口层级时出错", e);
        }
    }
    
    /**
     * 设置窗口总在最前面
     * @param windowHandle 窗口句柄
     * @param alwaysOnTop 是否总在最前面
     */
    private void setWindowAlwaysOnTop(long windowHandle, boolean alwaysOnTop) {
        try {
            // 在主线程中执行窗口操作
            MinecraftClient.getInstance().execute(() -> {
                try {
                    // 检查窗口是否仍然有效
                    if (GLFW.glfwWindowShouldClose(windowHandle)) {
                        return;
                    }
                    
                    // 设置窗口属性
                    GLFW.glfwSetWindowAttrib(windowHandle, GLFW.GLFW_FLOATING, alwaysOnTop ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
                    
                    // 如果设置为总在最前面，还要确保窗口可见
                    if (alwaysOnTop) {
                        GLFW.glfwShowWindow(windowHandle);
                        GLFW.glfwFocusWindow(windowHandle);
                    }
                    
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("设置窗口属性时出错: {}", e.getMessage());
                }
            });
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("执行窗口属性设置时出错", e);
        }
    }
    
    /**
     * 设置窗口的所有者关系
     * @param childWindow 子窗口句柄
     * @param parentWindow 父窗口句柄
     */
    private void setWindowOwner(long childWindow, long parentWindow) {
        try {
            // 在Windows平台上，可以通过JNI调用SetWindowLongPtr设置所有者
            // 但为了跨平台兼容性，我们主要依赖GLFW的层级管理
            
            // 确保子窗口不会被父窗口遮挡
            MinecraftClient.getInstance().execute(() -> {
                try {
                    if (!GLFW.glfwWindowShouldClose(childWindow) && !GLFW.glfwWindowShouldClose(parentWindow)) {
                        // 将子窗口置于前台
                        GLFW.glfwShowWindow(childWindow);
                    }
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("设置窗口所有者关系时出错: {}", e.getMessage());
                }
            });
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("设置窗口所有者关系失败", e);
        }
    }
    
    /**
     * 隐藏NodeCraft窗口
     */
    private void hideNodeCraftWindows() {
        NodeCraft.LOGGER.debug("隐藏NodeCraft窗口");
        
        for (Long windowHandle : nodecraftWindowHandles) {
            if (windowHandle != null && windowHandle != 0) {
                try {
                    MinecraftClient.getInstance().execute(() -> {
                        try {
                            if (!GLFW.glfwWindowShouldClose(windowHandle)) {
                                GLFW.glfwHideWindow(windowHandle);
                            }
                        } catch (Exception e) {
                            NodeCraft.LOGGER.error("隐藏窗口时出错: {}", e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("执行隐藏窗口操作时出错", e);
                }
            }
        }
    }
    
    /**
     * 显示NodeCraft窗口
     */
    private void showNodeCraftWindows() {
        NodeCraft.LOGGER.debug("显示NodeCraft窗口");
        
        for (Long windowHandle : nodecraftWindowHandles) {
            if (windowHandle != null && windowHandle != 0) {
                try {
                    MinecraftClient.getInstance().execute(() -> {
                        try {
                            if (!GLFW.glfwWindowShouldClose(windowHandle)) {
                                GLFW.glfwShowWindow(windowHandle);
                                setWindowAlwaysOnTop(windowHandle, true);
                            }
                        } catch (Exception e) {
                            NodeCraft.LOGGER.error("显示窗口时出错: {}", e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("执行显示窗口操作时出错", e);
                }
            }
        }
        
        ensureNodeCraftWindowOnTop();
    }
    
    /**
     * 更新窗口状态
     * 应该在每个客户端tick中调用
     */
    public void updateWindowState() {
        if (!isInitialized.get()) {
            return;
        }
        
        long handle = minecraftWindowHandle.get();
        if (handle == 0) {
            return;
        }
        
        try {
            // 检查Minecraft窗口状态
            boolean currentVisible = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_FALSE;
            boolean currentFocused = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
            
            // 如果状态发生变化，更新NodeCraft窗口
            if (currentVisible != lastMinecraftVisible) {
                lastMinecraftVisible = currentVisible;
                if (currentVisible) {
                    showNodeCraftWindows();
                } else {
                    hideNodeCraftWindows();
                }
            }
            
            if (currentFocused != lastMinecraftFocused) {
                lastMinecraftFocused = currentFocused;
                if (currentFocused) {
                    ensureNodeCraftWindowOnTop();
                }
            }
            
            // 减少频繁的层级更新，只在必要时更新
            layeringUpdateCounter++;
            if (layeringUpdateCounter >= LAYERING_UPDATE_INTERVAL * 3) { // 延长到90帧
                layeringUpdateCounter = 0;
                // 只在Minecraft窗口获得焦点时才更新层级
                if (currentFocused) {
                    ensureNodeCraftWindowOnTop();
                }
            }
            
            // 清理无效的窗口句柄
            cleanupInvalidWindows();
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("更新窗口状态时出错", e);
        }
    }
    
    /**
     * 清理无效的窗口句柄
     */
    private void cleanupInvalidWindows() {
        nodecraftWindowHandles.removeIf(windowHandle -> {
            if (windowHandle == null || windowHandle == 0) {
                return true;
            }
            
            try {
                // 检查窗口是否应该关闭
                return GLFW.glfwWindowShouldClose(windowHandle);
            } catch (Exception e) {
                // 如果检查失败，认为窗口无效
                NodeCraft.LOGGER.debug("检查窗口状态时出错，移除窗口句柄: {}", windowHandle);
                return true;
            }
        });
    }
    
    /**
     * 更新NodeCraft窗口位置
     * @param x 新的X坐标（屏幕绝对坐标）
     * @param y 新的Y坐标（屏幕绝对坐标）
     */
    public void updateNodeCraftWindowPosition(float x, float y) {
        if (!isInitialized.get()) {
            NodeCraft.LOGGER.warn("WindowManager未初始化，无法更新窗口位置");
            return;
        }
        
        NodeCraft.LOGGER.info("尝试更新NodeCraft窗口位置到: ({}, {})", x, y);
        
        // 遍历所有已注册的NodeCraft窗口
        for (Long windowHandle : nodecraftWindowHandles) {
            if (windowHandle != null && windowHandle != 0) {
                try {
                    MinecraftClient.getInstance().execute(() -> {
                        try {
                            if (!GLFW.glfwWindowShouldClose(windowHandle)) {
                                // 设置窗口位置
                                GLFW.glfwSetWindowPos(windowHandle, (int)x, (int)y);
                                NodeCraft.LOGGER.info("已更新窗口 {} 的位置到: ({}, {})", windowHandle, x, y);
                            }
                        } catch (Exception e) {
                            NodeCraft.LOGGER.error("更新窗口位置时出错: {}", e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("执行窗口位置更新时出错", e);
                }
            }
        }
    }
    
    /**
     * 设置NodeCraft窗口与Minecraft的关联关系
     * 这个方法应该在ImGui窗口创建后调用
     */
    public void associateNodeCraftWindow() {
        if (!isInitialized.get()) {
            initialize();
        }
        
        if (!isInitialized.get()) {
            return;
        }
        
        try {
            // 标记窗口已关联
            isWindowAssociated.set(true);
            
            // 确保窗口层级正确
            ensureNodeCraftWindowOnTop();
            
            NodeCraft.LOGGER.debug("NodeCraft窗口已与Minecraft关联");
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("关联NodeCraft窗口时出错", e);
        }
    }
    
    /**
     * 取消NodeCraft窗口与Minecraft的关联关系
     */
    public void disassociateNodeCraftWindow() {
        isWindowAssociated.set(false);
        NodeCraft.LOGGER.debug("NodeCraft窗口已取消与Minecraft的关联");
    }
    
    /**
     * 处理NodeCraft窗口关闭事件
     * 确保正确处理关闭逻辑，避免游戏崩溃
     */
    public boolean handleNodeCraftWindowClose() {
        try {
            NodeCraft.LOGGER.info("处理NodeCraft窗口关闭事件");
            
            // 检查是否启用了ViewportsEnable
            boolean viewportsEnabled = false;
            try {
                viewportsEnabled = imgui.ImGui.getIO().hasConfigFlags(imgui.flag.ImGuiConfigFlags.ViewportsEnable);
            } catch (Exception e) {
                NodeCraft.LOGGER.debug("无法检查ViewportsEnable状态: {}", e.getMessage());
            }
            
            if (viewportsEnabled) {
                NodeCraft.LOGGER.info("ViewportsEnable模式下的窗口关闭，需要特殊处理");
                
                // 在ViewportsEnable模式下，需要强制关闭整个屏幕
                net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                    try {
                        net.minecraft.client.gui.screen.Screen currentScreen = 
                            net.minecraft.client.MinecraftClient.getInstance().currentScreen;
                        
                        if (currentScreen instanceof com.nodecraft.gui.screens.NodecraftScreen) {
                            NodeCraft.LOGGER.info("强制关闭NodeCraft屏幕 (ViewportsEnable模式)");
                            ((com.nodecraft.gui.screens.NodecraftScreen) currentScreen).requestClose();
                        }
                    } catch (Exception e) {
                        NodeCraft.LOGGER.error("强制关闭NodeCraft屏幕时出错", e);
                    }
                });
                
                // 取消窗口关联
                disassociateNodeCraftWindow();
                return true;
            } else {
                // 在普通模式下，只是隐藏窗口
                disassociateNodeCraftWindow();
                return true;
            }
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("处理窗口关闭事件时出错", e);
            return false;
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        try {
            long handle = minecraftWindowHandle.get();
            if (handle != 0) {
                // 清理回调函数
                GLFW.glfwSetWindowFocusCallback(handle, null);
                GLFW.glfwSetWindowIconifyCallback(handle, null);
            }
            
            isInitialized.set(false);
            isWindowAssociated.set(false);
            minecraftWindowHandle.set(0);
            
            NodeCraft.LOGGER.info("窗口管理器已清理");
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("清理窗口管理器时出错", e);
        }
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return isInitialized.get();
    }
    
    /**
     * 检查窗口是否已关联
     */
    public boolean isWindowAssociated() {
        return isWindowAssociated.get();
    }
    
    /**
     * 获取Minecraft窗口句柄
     */
    public long getMinecraftWindowHandle() {
        return minecraftWindowHandle.get();
    }
} 
