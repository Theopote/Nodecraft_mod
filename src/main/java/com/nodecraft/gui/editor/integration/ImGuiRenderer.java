package com.nodecraft.gui.editor.integration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL; // Changed to java.net.URL for clarity
import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.screens.NodecraftScreen;
import com.nodecraft.gui.window.DetachedEditorWindow;
import com.nodecraft.gui.window.WindowManager;
import com.nodecraft.gui.window.ImGuiWindowHandler;
import com.nodecraft.gui.window.ViewportCloseDetector;
import imgui.*;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiCol;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.internal.ImGuiContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.DoubleBuffer;

/**
 * ImGui渲染器类。
 * 负责ImGui上下文的创建、初始化、字体加载、帧生命周期管理以及与GLFW/OpenGL的集成。
 */
public class ImGuiRenderer {
        /**
         * 获取分离编辑器窗口实例。
         */
        public DetachedEditorWindow getDetachedEditorWindow() {
            return detachedEditorWindow;
        }
    
    private static ImGuiRenderer INSTANCE;
    
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
    private final DetachedEditorWindow detachedEditorWindow = new DetachedEditorWindow();
    private boolean initialized = false;
    private boolean drawDataReady = false;
    private ImGuiContext primaryContext;
    private long backendWindowHandle;
    
    // 依赖的管理器实例，通常在更高层级初始化并传递，但此处保持单例获取
    private final WindowManager windowManager = WindowManager.getInstance();
    private final ImGuiWindowHandler windowHandler = ImGuiWindowHandler.getInstance();
    private final ViewportCloseDetector closeDetector = ViewportCloseDetector.getInstance();
    
    private static final String FONT_RESOURCE_PATH = "assets/nodecraft/fonts/NotoSansSC-Regular.ttf";
    private static final float BASE_FONT_SIZE = 18.0f;
    private static final double BASE_MINECRAFT_GUI_SCALE = 2.0d;
    private static final float MIN_UI_SCALE = 0.85f;
    private static final float MAX_UI_SCALE = 2.25f;
    private static final float UI_SCALE_EPSILON = 0.05f;
    
    // 字体相关变量，用于存储加载后的字体数据
    private byte[] fontDataBytes; // 存储字体文件的字节数组
    private ImFontConfig baseFontConfig; // 基础字体配置
    private short[] chineseGlyphRanges; // 中文字符范围
    private float currentUiScale = 1.0f;

    /**
     * 私有构造函数，实现单例模式。
     */
    private ImGuiRenderer() {}
    
    /**
     * 获取 ImGuiRenderer 的单例实例。
     * @return ImGuiRenderer 实例。
     */
    public static ImGuiRenderer getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ImGuiRenderer();
        }
        return INSTANCE;
    }
    
    /**
     * 初始化 ImGui 上下文和后端。
     * 确保只被调用一次。
     */
    public void init() {
        if (initialized) return;
        
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Window window = client.getWindow();
            long windowHandle = window.getHandle();
            
            // 1. 创建 ImGui 上下文
            ImGui.createContext();
            
            ImGuiIO io = ImGui.getIO();
            io.setIniFilename(null); // 禁用ini持久化，避免窗口恢复到屏幕外

            // 2. 配置 ImGui 标志 (使用 addConfigFlags 避免覆盖)
            io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard); // 启用键盘导航
            io.addConfigFlags(ImGuiConfigFlags.DockingEnable);    // 启用停靠功能

            // 3. 设置后端名称
            io.setBackendPlatformName("imgui_java_impl_glfw");
            io.setBackendRendererName("imgui_java_impl_lwjgl3");
            
            // 4. 预加载字体数据和字符范围
            if (!preloadFontData()) {
                NodeCraft.LOGGER.error("字体文件预加载失败，ImGui初始化中断。");
                initialized = false;
                return;
            }

            currentUiScale = resolveCurrentUiScale();

            // 5. 加载字体
            loadCustomFonts(io, currentUiScale);
            
            // 6. 初始化 GLFW 和 OpenGL3 后端
            imGuiGlfw.init(windowHandle, true); // true表示安装回调
            backendWindowHandle = windowHandle;
            resetPixelStoreStateForImGuiTextureUpload();
            imGuiGl3.init("#version 150"); // OpenGL 3.2+ Core Profile
            
            // 7. 设置 ImGui 样式
            applyEditorStyleForCurrentContext();
            
            // 8. 初始化依赖的管理器
            windowManager.initialize();
            windowHandler.initialize();
            closeDetector.initialize();
            
            initialized = true;
            NodeCraft.LOGGER.info("ImGui Renderer 初始化成功。");
        } catch (Exception e) {
            initialized = false;
            NodeCraft.LOGGER.error("ImGui 初始化失败", e);
        }
    }

    /**
     * 预加载字体数据和构建字符范围。
     * @return true 如果字体数据成功加载并构建字符范围，否则返回 false。
     */
    private boolean preloadFontData() {
        try {
            URL fontUrl = getClass().getClassLoader().getResource(FONT_RESOURCE_PATH);
            if (fontUrl == null) {
                NodeCraft.LOGGER.error("无法找到中文字体文件: {}", FONT_RESOURCE_PATH);
                return false;
            }
            
            try (InputStream is = fontUrl.openStream()) {
                if (is == null) {
                    NodeCraft.LOGGER.error("无法打开字体文件资源流: {}", FONT_RESOURCE_PATH);
                    return false;
                }
                fontDataBytes = is.readAllBytes();
                if (fontDataBytes.length < 1000000) { // 中文字体通常较大，检查文件大小
                    NodeCraft.LOGGER.warn("字体文件过小，可能不完整或损坏: {} ({} 字节)。预计中文字体至少几MB。", FONT_RESOURCE_PATH, fontDataBytes.length);
                    // 可以选择在这里返回false或继续，取决于对字体完整性的严格要求
                    // 为了健壮性，如果文件太小，我们认为它无法提供完整中文支持
                    return false; 
                }
                NodeCraft.LOGGER.info("成功预加载字体文件: {} ({} 字节)", FONT_RESOURCE_PATH, fontDataBytes.length);
            }

            // 构建所有需要的字符范围
            ImFontGlyphRangesBuilder rangesBuilder = new ImFontGlyphRangesBuilder();
            rangesBuilder.addRanges(ImGui.getIO().getFonts().getGlyphRangesDefault());
            rangesBuilder.addRanges(ImGui.getIO().getFonts().getGlyphRangesChineseSimplifiedCommon());
            
            // 添加自定义常用UI文本，确保它们也被包含
            rangesBuilder.addText("节点编辑器属性面板画布工具栏状态栏保存打开新建编辑删除复制粘贴撤销重做缩放重置视图");
            rangesBuilder.addText("输入输出类型名称描述参数变量函数方法值配置设置选项开关按钮滑块下拉菜单颜色选择器");
            rangesBuilder.addText("错误警告信息成功失败加载保存网络连接断开重试取消确认");
            
            chineseGlyphRanges = rangesBuilder.buildRanges();

            // 配置基础字体设置
            baseFontConfig = new ImFontConfig();
            baseFontConfig.setMergeMode(false);
            baseFontConfig.setPixelSnapH(true);
            baseFontConfig.setOversampleH(2);
            baseFontConfig.setOversampleV(2);
            baseFontConfig.setRasterizerMultiply(1.0f);

            return true;
        } catch (IOException e) {
            NodeCraft.LOGGER.error("预加载字体数据时发生IO错误: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("预加载字体数据时发生未知错误", e);
            return false;
        }
    }

    /**
     * 加载并构建自定义字体。
     * @param io ImGuiIO 实例。
     */
    private void loadCustomFonts(ImGuiIO io, float uiScale) {
        if (fontDataBytes == null || chineseGlyphRanges == null || baseFontConfig == null) {
            NodeCraft.LOGGER.error("字体数据未预加载或加载失败，无法加载自定义字体。");
            fallbackToDefaultFont(io, uiScale);
            return;
        }
        
        try {
            io.getFonts().clear();

            float fontSize = getScaledFontSize(uiScale);
            boolean loadedFromSystem = tryLoadSystemChineseFont(io, fontSize);
            if (!loadedFromSystem) {
                ImFontConfig fontConfig = getImFontConfig();
                io.getFonts().addFontFromMemoryTTF(fontDataBytes, fontSize, fontConfig, chineseGlyphRanges);
                NodeCraft.LOGGER.info("已加载内置中文字体。");
            }

            io.setFontGlobalScale(1.0f);

            // 构建字体图集
            if (!io.getFonts().build()) {
                NodeCraft.LOGGER.error("字体构建失败，尝试回退到默认字体。");
                fallbackToDefaultFont(io, uiScale);
            } else {
                NodeCraft.LOGGER.info("成功构建所有字体。");
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("加载自定义字体时出错", e);
            fallbackToDefaultFont(io, uiScale);
        }
    }

    private boolean tryLoadSystemChineseFont(ImGuiIO io, float fontSize) {
        String[] fontPaths = {
                "C:/Windows/Fonts/msyh.ttc",
                "C:/Windows/Fonts/msyhbd.ttc",
                "C:/Windows/Fonts/simhei.ttf"
        };

        for (String fontPath : fontPaths) {
            try {
                java.io.File file = new java.io.File(fontPath);
                if (!file.exists()) {
                    continue;
                }

                ImFontConfig config = getImFontConfig();
                config.setGlyphRanges(io.getFonts().getGlyphRangesChineseFull());
                io.getFonts().addFontFromFileTTF(fontPath, fontSize, config);
                NodeCraft.LOGGER.info("已加载系统字体: {}", fontPath);
                return true;
            } catch (Exception e) {
                NodeCraft.LOGGER.warn("加载系统字体失败: {}", fontPath, e);
            }
        }

        return false;
    }

    private @NotNull ImFontConfig getImFontConfig() {
        ImFontConfig fontConfig16 = new ImFontConfig();
        fontConfig16.setFontDataOwnedByAtlas(false); // 字体数据不被atlas拥有，因为它是一个共享的byte[]
        fontConfig16.setMergeMode(baseFontConfig.getMergeMode());
        fontConfig16.setPixelSnapH(baseFontConfig.getPixelSnapH());
        fontConfig16.setOversampleH(baseFontConfig.getOversampleH());
        fontConfig16.setOversampleV(baseFontConfig.getOversampleV());
        fontConfig16.setRasterizerMultiply(baseFontConfig.getRasterizerMultiply());
        return fontConfig16;
    }

    /**
     * 回退到默认字体。
     * @param io ImGuiIO 实例。
     */
    private void fallbackToDefaultFont(ImGuiIO io, float uiScale) {
        NodeCraft.LOGGER.warn("正在使用默认字体，中文可能无法正确显示。");
        try {
            io.getFonts().clear(); // 清除所有字体
            
            ImFontConfig defaultFontConfig = new ImFontConfig();
            defaultFontConfig.setPixelSnapH(true);
            defaultFontConfig.setOversampleH(3);
            defaultFontConfig.setOversampleV(3);
            
            // 直接添加默认字体，不指定字符范围，因为 addFontDefault 不支持
            io.getFonts().addFontDefault(defaultFontConfig);
            io.setFontGlobalScale(normalizeUiScale(uiScale));
            
            // 构建默认字体图集
            if (!io.getFonts().build()) {
                NodeCraft.LOGGER.error("构建默认字体失败，回退到ImGui内置最简字体。");
                io.getFonts().clear(); // 确保清除所有
                io.getFonts().addFontDefault(); // ImGui内置的最小字体
                io.setFontGlobalScale(normalizeUiScale(uiScale));
                io.getFonts().build();
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("设置备选字体时出错", e);
            NodeCraft.LOGGER.error("无法加载任何字体，ImGui显示可能异常！", e);
        }
    }
    
    private void updateUiScaleIfNeeded(ImGuiIO io) {
        float nextUiScale = resolveCurrentUiScale();
        if (Math.abs(nextUiScale - currentUiScale) < UI_SCALE_EPSILON) {
            return;
        }

        float previousUiScale = currentUiScale;
        currentUiScale = nextUiScale;

        loadCustomFonts(io, nextUiScale);
        resetPixelStoreStateForImGuiTextureUpload();
        imGuiGl3.updateFontsTexture();
        applyEditorStyleForCurrentContext();

        NodeCraft.LOGGER.info("ImGui UI scale changed from {} to {}", previousUiScale, nextUiScale);
    }

    private float resolveCurrentUiScale() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return 1.0f;
            }
            return scaleForMinecraftGuiScale(client.getWindow().getScaleFactor());
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("Unable to resolve Minecraft GUI scale for ImGui: {}", e.getMessage());
            return 1.0f;
        }
    }

    static float scaleForMinecraftGuiScale(double minecraftGuiScale) {
        if (!Double.isFinite(minecraftGuiScale) || minecraftGuiScale <= 0.0d) {
            return 1.0f;
        }
        return normalizeUiScale((float) (minecraftGuiScale / BASE_MINECRAFT_GUI_SCALE));
    }

    private static float getScaledFontSize(float uiScale) {
        return BASE_FONT_SIZE * normalizeUiScale(uiScale);
    }

    private static float normalizeUiScale(float uiScale) {
        return Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, uiScale));
    }

    /**
     * 开始 ImGui 帧渲染。
     * 必须在每次 ImGui 绘制前调用。
     */
    public void beginFrame() {
        if (!initialized) {
            // 在生产环境中，这里不应该发生，或者应该更优雅地处理
            // NodeCraft.LOGGER.warn("ImGui未初始化，无法开始帧渲染"); // Too verbose
            return;
        }
        
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Window window = client.getWindow();

            ImGuiIO io = ImGui.getIO();
            if (detachedEditorWindow.isOpen()) {
                detachedEditorWindow.prepareFrame(io);
            } else {
                io.setDisplaySize(window.getWidth(), window.getHeight());
                io.setDisplayFramebufferScale(
                    Math.max(1.0f, (float) window.getFramebufferWidth() / Math.max(1.0f, window.getWidth())),
                    Math.max(1.0f, (float) window.getFramebufferHeight() / Math.max(1.0f, window.getHeight()))
                );

                DoubleBuffer xBuffer = BufferUtils.createDoubleBuffer(1);
                DoubleBuffer yBuffer = BufferUtils.createDoubleBuffer(1);
                GLFW.glfwGetCursorPos(window.getHandle(), xBuffer, yBuffer);
                io.setMousePos((float) xBuffer.get(0), (float) yBuffer.get(0));
            }

            updateUiScaleIfNeeded(io);

            imGuiGlfw.newFrame();
            ImGui.newFrame();
            
            closeDetector.onFrameStart();
            
            // 注册 ImGui 创建的窗口句柄 (这一步通常在 ImGui.updatePlatformWindows() 内部由 ImGui 本身处理)
            // 这里留空，因为 ImGui 绑定通常会自动处理，除非有特殊需要手动注册。
            // registerImGuiWindows(); // 除非有特定问题，否则此方法不应在这里被调用

        } catch (Exception e) {
            NodeCraft.LOGGER.error("开始ImGui帧时出错", e);
        }
    }
    
    /**
     * 结束 ImGui 帧渲染。
     * 必须在每次 ImGui 绘制后调用。
     */
    public void endFrame() {
        if (!initialized) {
            return;
        }
        
        try {
            closeDetector.onFrameEnd(); // 在帧结束时调用关闭检测器
            
            ImGui.render();
            drawDataReady = true;
            
            // 更新窗口层级 (如果 WindowHandler 有额外的层级管理逻辑)
            windowHandler.updateWindowLayering();
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("结束ImGui帧时出错", e);
        }
    }

    public boolean hasPendingDrawData() {
        return initialized && drawDataReady;
    }

    public void renderPendingDrawData() {
        if (!initialized || !drawDataReady) {
            return;
        }

        try {
            com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();

            MinecraftClient client = MinecraftClient.getInstance();
            if (!detachedEditorWindow.isOpen() && client != null && client.getWindow() != null) {
                Window window = client.getWindow();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                GL11.glViewport(0, 0,
                        Math.max(1, window.getFramebufferWidth()),
                        Math.max(1, window.getFramebufferHeight()));
            }

            imgui.ImDrawData drawData = ImGui.getDrawData();
            if (!detachedEditorWindow.isOpen() && drawData != null && drawData.getCmdListsCount() > 0) {
                try (ImGuiGLStateGuard ignored = ImGuiGLStateGuard.enter()) {
                    imGuiGl3.renderDrawData(drawData);
                }
            }

            if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
                final long backupCurrentContext = GLFW.glfwGetCurrentContext();
                ImGuiGLStateGuard stateGuard = ImGuiGLStateGuard.enter();
                try {
                    ImGui.updatePlatformWindows();
                    ImGui.renderPlatformWindowsDefault();
                } finally {
                    GLFW.glfwMakeContextCurrent(backupCurrentContext);
                    stateGuard.close();
                }
                ensureWindowLayering();
            }

            if (detachedEditorWindow.isOpen()) {
                detachedEditorWindow.renderDrawData(drawData, imGuiGl3);
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染待提交ImGui数据时出错", e);
        } finally {
            drawDataReady = false;
        }
    }
    
    /**
     * 确保 ImGui 平台窗口和 Minecraft 主窗口的层级正确。
     * 防止 ImGui 窗口被 Minecraft 窗口覆盖。
     */
    private void ensureWindowLayering() {
        try {
            windowManager.ensureNodeCraftWindowOnTop();
            
        } catch (Exception e) {
            NodeCraft.LOGGER.error("确保窗口层级时出错", e);
        }
    }
    
    /**
     * 设置 ImGui 的样式，使其更贴近 Minecraft 的视觉风格。
     */
    public void applyEditorStyleForCurrentContext() {
        primaryContext = ImGui.getCurrentContext();
        setupMinecraftStyle(currentUiScale);
    }

    private void setupMinecraftStyle(float uiScale) {
        ImGui.styleColorsDark(); // 使用暗色主题作为基础

        var style = getImGuiStyle(uiScale);

        // 颜色主题
        style.setColor(ImGuiCol.WindowBg, 0.16f, 0.16f, 0.20f, 0.72f);
        style.setColor(ImGuiCol.Border, 0.40f, 0.40f, 0.50f, 0.7f);
        style.setColor(ImGuiCol.Header, 0.30f, 0.50f, 0.80f, 0.45f);
        style.setColor(ImGuiCol.HeaderHovered, 0.35f, 0.60f, 0.90f, 0.55f);
        style.setColor(ImGuiCol.HeaderActive, 0.40f, 0.65f, 0.95f, 0.65f);
        style.setColor(ImGuiCol.TitleBg, 0.25f, 0.25f, 0.40f, 0.90f);
        style.setColor(ImGuiCol.TitleBgActive, 0.32f, 0.32f, 0.63f, 1.00f);
        style.setColor(ImGuiCol.TitleBgCollapsed, 0.30f, 0.30f, 0.60f, 0.50f);
        style.setColor(ImGuiCol.ResizeGrip, 0.50f, 0.50f, 0.60f, 0.70f);
        style.setColor(ImGuiCol.ResizeGripHovered, 0.65f, 0.65f, 0.80f, 0.90f);
        style.setColor(ImGuiCol.ResizeGripActive, 0.75f, 0.75f, 0.95f, 1.00f);
        style.setColor(ImGuiCol.Button, 0.25f, 0.25f, 0.45f, 0.85f);
        style.setColor(ImGuiCol.ButtonHovered, 0.35f, 0.35f, 0.60f, 0.95f);
        style.setColor(ImGuiCol.ButtonActive, 0.40f, 0.40f, 0.70f, 1.00f);
        style.setColor(ImGuiCol.SliderGrab, 0.40f, 0.40f, 0.70f, 0.85f);
        style.setColor(ImGuiCol.SliderGrabActive, 0.50f, 0.50f, 0.80f, 0.95f);
        style.setColor(ImGuiCol.Text, 0.90f, 0.90f, 0.90f, 1.00f);
    }

    private static @NotNull ImGuiStyle getImGuiStyle(float uiScale) {
        var style = ImGui.getStyle();
        float scale = normalizeUiScale(uiScale);

        // 窗口圆角
        style.setWindowRounding(4.0f * scale);
        style.setFrameRounding(2.0f * scale);
        style.setTabRounding(2.0f * scale);
        style.setPopupRounding(2.0f * scale);
        style.setScrollbarRounding(3.0f * scale);
        style.setGrabRounding(2.0f * scale);

        // 间距和内边距
        style.setWindowPadding(10.0f * scale, 10.0f * scale);
        style.setFramePadding(6.0f * scale, 6.0f * scale);
        style.setItemSpacing(8.0f * scale, 8.0f * scale);
        style.setItemInnerSpacing(5.0f * scale, 5.0f * scale);

        // 边框和最小尺寸
        style.setWindowBorderSize(1.5f * scale);
        style.setFrameBorderSize(1.0f * scale);
        style.setWindowMinSize(400.0f * scale, 300.0f * scale);

        // 滚动条和抓取柄大小
        style.setGrabMinSize(12.0f * scale);
        style.setScrollbarSize(18.0f * scale);

        // 标题对齐
        style.setWindowTitleAlign(0.5f, 0.5f); // 居中对齐
        return style;
    }

    /**
     * 关闭 ImGui 上下文和后端，释放资源。
     */
    public void shutdown() {
        if (!initialized) return;
        
        try {
            detachedEditorWindow.cleanup();
            closeDetector.cleanup();
            windowHandler.cleanup();
            windowManager.cleanup();
            
            imGuiGl3.dispose();
            imGuiGlfw.dispose();
            initialized = false;
            backendWindowHandle = 0;
            NodeCraft.LOGGER.info("ImGui Renderer 关闭成功。");
        } catch (Exception e) {
            NodeCraft.LOGGER.error("关闭ImGui时出错", e);
        }
    }
    
    /**
     * 检查 ImGui 是否已初始化。
     * @return true 如果已初始化，否则返回false。
     */
    public boolean isInitialized() {
        return initialized;
    }

    public void openDetachedEditorWindow(final NodecraftScreen screen) {
        if (!initialized) {
            return;
        }
        detachedEditorWindow.open(screen);
    }

    public void closeDetachedEditorWindow(final NodecraftScreen screen) {
        detachedEditorWindow.close(screen);
    }

    public boolean isDetachedEditorOpen(final NodecraftScreen screen) {
        return detachedEditorWindow.isDetached(screen);
    }

    public long getActiveInputWindowHandle() {
        if (detachedEditorWindow.isOpen()) {
            return detachedEditorWindow.getWindowHandle();
        }

        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return 0L;
        }
        return client.getWindow().getHandle();
    }

    public boolean isMouseClicked(final int button) {
        if (detachedEditorWindow.isOpen()) {
            return detachedEditorWindow.isMouseClicked(button);
        }
        return ImGui.isMouseClicked(button);
    }

    public boolean isMouseDown(final int button) {
        if (detachedEditorWindow.isOpen()) {
            return detachedEditorWindow.isMouseDown(button);
        }
        return ImGui.isMouseDown(button);
    }

    public boolean isMouseReleased(final int button) {
        if (detachedEditorWindow.isOpen()) {
            return detachedEditorWindow.isMouseReleased(button);
        }
        return ImGui.isMouseReleased(button);
    }

    public void bindPlatformBackendToWindow(final long windowHandle) {
        if (!initialized || windowHandle == 0 || backendWindowHandle == windowHandle) {
            return;
        }

        try {
            imGuiGlfw.dispose();
        } catch (Exception e) {
            NodeCraft.LOGGER.debug("Rebinding ImGui GLFW backend dispose failed: {}", e.getMessage());
        }

        imGuiGlfw.init(windowHandle, true);
        backendWindowHandle = windowHandle;
    }

    public void restorePrimaryPlatformBackend() {
        if (!initialized) {
            return;
        }

        final MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }

        bindPlatformBackendToWindow(client.getWindow().getHandle());
    }

    /**
     * 更新平台后端状态（例如，处理窗口事件，更新计时器）。
     * 应在每个客户端 Tick 调用，以保持后端同步，即使没有渲染 ImGui 帧。
     * ImGui 的 `newFrame()` 也可能需要在这里被调用一次，以更新 ImGuiIO。
     */
    public void updatePlatformBackend() {
        if (!initialized) {
            return; 
        }
        try {
            // 调用 newFrame 以更新 IO 状态，但不立即开始 ImGui.newFrame() 的整个流程
            // 确保 ImGuiIO.getDeltaTime() 等信息在非渲染帧也能获取到最新值
            imGuiGlfw.newFrame(); // GLFW后端每帧更新，包括鼠标、键盘、时间等
            // ImGui.newFrame(); // 不在这里调用，因为它会开始一个新的UI帧，只在 beginFrame 处调用
            
            windowManager.updateWindowState(); // 更新窗口管理器状态
        } catch (Exception e) {
            NodeCraft.LOGGER.error("更新ImGui平台后端时出错", e);
        }
    }

    private void resetPixelStoreStateForImGuiTextureUpload() {
        try {
            com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();

            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0);

            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS, 0);
        } catch (Throwable throwable) {
            NodeCraft.LOGGER.warn("重置OpenGL像素存储状态失败（继续初始化）: {}", throwable.toString());
        }
    }
}
