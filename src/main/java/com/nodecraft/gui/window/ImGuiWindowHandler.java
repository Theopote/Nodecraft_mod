package com.nodecraft.gui.window;

import com.nodecraft.core.NodeCraft;
import imgui.ImGui;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiWindowFlags;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MinecraftClient;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ImGui窗口处理器
 * 专门处理ImGui窗口的层级管理和平台特定操作
 */
public class ImGuiWindowHandler {
    private static ImGuiWindowHandler INSTANCE;
    
    private final WindowManager windowManager;
    private boolean isViewportsEnabled = false;
    
    // 跟踪已知的窗口句柄
    private final Set<Long> knownWindowHandles = ConcurrentHashMap.newKeySet();
    
    // 窗口层级强制更新计数器
    private int windowLayeringCounter = 0;
    private static final int WINDOW_LAYERING_INTERVAL = 60; // 每60帧强制更新一次，减少干扰
    
    private ImGuiWindowHandler() {
        this.windowManager = WindowManager.getInstance();
    }
    
    public static ImGuiWindowHandler getInstance() {
        if (INSTANCE == null) {
            synchronized (ImGuiWindowHandler.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ImGuiWindowHandler();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * 初始化ImGui窗口处理器
     */
    public void initialize() {
        try {
            // 检查是否启用了ViewportsEnable
            isViewportsEnabled = ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable);
            
            if (isViewportsEnabled) {
                NodeCraft.LOGGER.info("ImGui ViewportsEnable已启用，将管理多窗口层级");
                setupViewportCallbacks();
                
                // 注册主窗口句柄
                registerMainWindow();
            } else {
                NodeCraft.LOGGER.info("ImGui ViewportsEnable未启用，使用单窗口模式");
            }
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("初始化ImGui窗口处理器失败", e);
        }
    }
    
    /**
     * 注册主窗口句柄
     */
    private void registerMainWindow() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getWindow() != null) {
                long mainWindowHandle = client.getWindow().getHandle();
                if (mainWindowHandle != 0) {
                    knownWindowHandles.add(mainWindowHandle);
                    windowManager.registerNodeCraftWindow(mainWindowHandle);
                    NodeCraft.LOGGER.debug("已注册主窗口句柄: {}", mainWindowHandle);
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("注册主窗口句柄失败", e);
        }
    }
    
    /**
     * 设置Viewport回调函数
     */
    private void setupViewportCallbacks() {
        try {
            // 由于Java ImGui绑定的限制，我们无法直接设置viewport回调
            // 但我们可以通过其他方式来检测和管理窗口
            NodeCraft.LOGGER.debug("设置Viewport回调函数（通过间接方式）");
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("设置Viewport回调函数失败", e);
        }
    }
    
    /**
     * 检测并注册新的ImGui窗口
     */
    public void detectAndRegisterNewWindows() {
        if (!isViewportsEnabled) {
            return;
        }
        
        try {
            // 由于Java ImGui绑定的限制，我们无法直接枚举所有viewport窗口
            // 但我们可以通过GLFW枚举所有窗口来检测新的ImGui窗口
            detectNewGLFWWindows();
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("检测新窗口时出错", e);
        }
    }
    
    /**
     * 通过GLFW检测新窗口
     */
    private void detectNewGLFWWindows() {
        try {
            // 这是一个简化的实现，实际上GLFW没有提供枚举所有窗口的API
            // 我们主要依赖窗口层级的强制更新来确保正确的显示顺序
            
            // 获取当前活动窗口
            long currentWindow = GLFW.glfwGetCurrentContext();
            if (currentWindow != 0 && !knownWindowHandles.contains(currentWindow)) {
                // 检查这是否是一个ImGui创建的窗口
                if (isImGuiWindow(currentWindow)) {
                    knownWindowHandles.add(currentWindow);
                    windowManager.registerNodeCraftWindow(currentWindow);
                    NodeCraft.LOGGER.debug("检测到新的ImGui窗口: {}", currentWindow);
                }
            }
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("通过GLFW检测新窗口时出错", e);
        }
    }
    
    /**
     * 检查窗口是否是ImGui创建的窗口
     * @param windowHandle 窗口句柄
     * @return 是否是ImGui窗口
     */
    private boolean isImGuiWindow(long windowHandle) {
        try {
            // 检查窗口标题是否包含NodeCraft相关内容
            String title = GLFW.glfwGetWindowTitle(windowHandle);
            if (title != null && (title.contains("NodeCraft") || title.contains("编辑器"))) {
                return true;
            }
            
            // 检查窗口是否有ImGui特征
            // 这里可以添加更多的检测逻辑
            
            return false;
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("检查窗口类型时出错: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 创建NodeCraft主窗口
     * @param title 窗口标题
     * @param windowFlags 窗口标志
     * @return 窗口是否成功创建
     */
    public boolean createMainWindow(String title, int windowFlags) {
        try {
            // 如果启用了ViewportsEnable，添加特定标志
            if (isViewportsEnabled) {
                // 允许窗口在主窗口外渲染
                windowFlags |= ImGuiWindowFlags.NoDocking;
                
                // 设置窗口标题以便识别
                title = title + " - 独立窗口模式";
            }
            
            // 设置窗口属性
            setupWindowAttributes();
            
            return true;
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("创建主窗口失败", e);
            return false;
        }
    }
    
    /**
     * 设置窗口属性
     */
    private void setupWindowAttributes() {
        try {
            if (!isViewportsEnabled) {
                return;
            }
            
            // 获取主窗口句柄
            long minecraftHandle = windowManager.getMinecraftWindowHandle();
            if (minecraftHandle == 0) {
                return;
            }
            
            // 确保主窗口不是总在最前面，让ImGui窗口能够显示在上面
            MinecraftClient.getInstance().execute(() -> {
                try {
                    GLFW.glfwSetWindowAttrib(minecraftHandle, GLFW.GLFW_FLOATING, GLFW.GLFW_FALSE);
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("设置主窗口属性时出错: {}", e.getMessage());
                }
            });
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("设置窗口属性失败", e);
        }
    }
    
    /**
     * 更新窗口层级
     * 在每帧渲染后调用，确保窗口层级正确
     */
    public void updateWindowLayering() {
        if (!isViewportsEnabled) {
            return;
        }
        
        try {
            // 检测新窗口
            detectAndRegisterNewWindows();
            
            // 定期强制更新窗口层级
            windowLayeringCounter++;
            if (windowLayeringCounter >= WINDOW_LAYERING_INTERVAL) {
                windowLayeringCounter = 0;
                ensureNodeCraftWindowsOnTop();
            }
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("更新窗口层级失败", e);
        }
    }
    
    /**
     * 确保NodeCraft窗口在顶层
     */
    private void ensureNodeCraftWindowsOnTop() {
        try {
            // 通过窗口管理器确保层级正确
            windowManager.ensureNodeCraftWindowOnTop();
            
            // 额外的层级确保措施
            ensureImGuiViewportsLayering();
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("确保窗口在顶层失败", e);
        }
    }
    
    /**
     * 确保ImGui Viewports的层级关系
     */
    private void ensureImGuiViewportsLayering() {
        try {
            // 获取主窗口句柄作为参考
            long minecraftHandle = windowManager.getMinecraftWindowHandle();
            if (minecraftHandle == 0) {
                return;
            }
            
            // 确保主窗口不会覆盖ImGui窗口
            MinecraftClient.getInstance().execute(() -> {
                try {
                    // 确保Minecraft窗口不是浮动窗口
                    GLFW.glfwSetWindowAttrib(minecraftHandle, GLFW.GLFW_FLOATING, GLFW.GLFW_FALSE);
                    
                    // 对所有已知的NodeCraft窗口设置正确的层级
                    for (Long windowHandle : knownWindowHandles) {
                        if (windowHandle != null && windowHandle != 0 && windowHandle != minecraftHandle) {
                            try {
                                if (!GLFW.glfwWindowShouldClose(windowHandle)) {
                                    // 不设置为浮动窗口，让ImGui自然管理层级
                                    // GLFW.glfwSetWindowAttrib(windowHandle, GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE);
                                    GLFW.glfwShowWindow(windowHandle);
                                }
                            } catch (Exception e) {
                                NodeCraft.LOGGER.debug("设置窗口层级时出错: {}", e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    NodeCraft.LOGGER.error("在主线程中设置窗口层级时出错: {}", e.getMessage());
                }
            });
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("确保ImGui Viewports层级失败", e);
        }
    }
    
    /**
     * 处理窗口关闭事件
     * @param windowTitle 窗口标题
     * @return 是否成功处理关闭事件
     */
    public boolean handleWindowClose(String windowTitle) {
        try {
            NodeCraft.LOGGER.info("处理窗口关闭事件: {}", windowTitle);
            
            // 在ViewportsEnable模式下，需要特殊处理
            if (isViewportsEnabled) {
                NodeCraft.LOGGER.info("ViewportsEnable模式下的窗口关闭，强制关闭所有相关窗口");
                
                // 强制关闭NodeCraft屏幕
                net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                    try {
                        net.minecraft.client.gui.screen.Screen currentScreen = 
                            net.minecraft.client.MinecraftClient.getInstance().currentScreen;
                        
                        if (currentScreen instanceof com.nodecraft.gui.screens.NodecraftScreen) {
                            NodeCraft.LOGGER.info("关闭NodeCraft屏幕");
                            ((com.nodecraft.gui.screens.NodecraftScreen) currentScreen).requestClose();
                        }
                    } catch (Exception e) {
                        NodeCraft.LOGGER.error("强制关闭NodeCraft屏幕时出错", e);
                    }
                });
                
                return true;
            } else {
                // 使用窗口管理器处理关闭逻辑
                return windowManager.handleNodeCraftWindowClose();
            }
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("处理窗口关闭事件失败", e);
            return false;
        }
    }
    
    /**
     * 设置窗口始终在顶层
     * @param enable 是否启用
     */
    public void setAlwaysOnTop(boolean enable) {
        try {
            if (!isViewportsEnabled) {
                return;
            }
            
            long minecraftHandle = windowManager.getMinecraftWindowHandle();
            if (minecraftHandle == 0) {
                return;
            }
            
            // 设置窗口属性
            int floating = enable ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE;
            GLFW.glfwSetWindowAttrib(minecraftHandle, GLFW.GLFW_FLOATING, floating);
            
            NodeCraft.LOGGER.debug("设置窗口始终在顶层: {}", enable);
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("设置窗口始终在顶层失败", e);
        }
    }
    
    /**
     * 获取窗口信息
     * @return 窗口信息字符串
     */
    public String getWindowInfo() {
        try {
            StringBuilder info = new StringBuilder();
            info.append("ImGui窗口处理器状态:\n");
            info.append("ViewportsEnable: ").append(isViewportsEnabled).append("\n");
            info.append("窗口管理器已初始化: ").append(windowManager.isInitialized()).append("\n");
            info.append("窗口已关联: ").append(windowManager.isWindowAssociated()).append("\n");
            info.append("Minecraft窗口句柄: ").append(windowManager.getMinecraftWindowHandle()).append("\n");
            
            return info.toString();
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("获取窗口信息失败", e);
            return "获取窗口信息失败: " + e.getMessage();
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        try {
            NodeCraft.LOGGER.info("清理ImGui窗口处理器");
            
            // 清理相关资源
            isViewportsEnabled = false;
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("清理ImGui窗口处理器失败", e);
        }
    }
    
    /**
     * 检查是否启用了ViewportsEnable
     */
    public boolean isViewportsEnabled() {
        return isViewportsEnabled;
    }
} 
