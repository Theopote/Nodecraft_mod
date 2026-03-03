package com.nodecraft.gui.editor.integration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL; // Changed to java.net.URL for clarity
import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.window.WindowManager;
import com.nodecraft.gui.window.ImGuiWindowHandler;
import com.nodecraft.gui.window.ViewportCloseDetector;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiCol;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * ImGui渲染器类。
 * 负责ImGui上下文的创建、初始化、字体加载、帧生命周期管理以及与GLFW/OpenGL的集成。
 */
public class ImGuiRenderer {
    
    private static ImGuiRenderer INSTANCE;
    
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
    private boolean initialized = false;
    private boolean drawDataReady = false;
    
    // 依赖的管理器实例，通常在更高层级初始化并传递，但此处保持单例获取
    private final WindowManager windowManager = WindowManager.getInstance();
    private final ImGuiWindowHandler windowHandler = ImGuiWindowHandler.getInstance();
    private final ViewportCloseDetector closeDetector = ViewportCloseDetector.getInstance();
    
    private static final String FONT_RESOURCE_PATH = "assets/nodecraft/fonts/NotoSansSC-Regular.ttf";
    
    // 字体相关变量，用于存储加载后的字体数据
    private byte[] fontDataBytes; // 存储字体文件的字节数组
    private ImFontConfig baseFontConfig; // 基础字体配置
    private short[] chineseGlyphRanges; // 中文字符范围

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

            // 5. 加载字体
            loadCustomFonts(io);
            
            // 6. 初始化 GLFW 和 OpenGL3 后端
            imGuiGlfw.init(windowHandle, true); // true表示安装回调
            imGuiGl3.init("#version 150"); // OpenGL 3.2+ Core Profile
            
            // 7. 设置 ImGui 样式
            setupMinecraftStyle();
            
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
            rangesBuilder.addRanges(ImGui.getIO().getFonts().getGlyphRangesChineseFull());
            rangesBuilder.addRanges(ImGui.getIO().getFonts().getGlyphRangesJapanese());
            rangesBuilder.addRanges(ImGui.getIO().getFonts().getGlyphRangesCyrillic());
            
            // 添加自定义常用UI文本，确保它们也被包含
            rangesBuilder.addText("节点编辑器属性面板画布工具栏状态栏保存打开新建编辑删除复制粘贴撤销重做缩放重置视图");
            rangesBuilder.addText("输入输出类型名称描述参数变量函数方法值配置设置选项开关按钮滑块下拉菜单颜色选择器");
            rangesBuilder.addText("错误警告信息成功失败加载保存网络连接断开重试取消确认");
            
            chineseGlyphRanges = rangesBuilder.buildRanges();

            // 配置基础字体设置
            baseFontConfig = new ImFontConfig();
            baseFontConfig.setMergeMode(false);
            baseFontConfig.setPixelSnapH(true);
            baseFontConfig.setOversampleH(4); // 增加过采样，提高字体清晰度
            baseFontConfig.setOversampleV(4);
            baseFontConfig.setRasterizerMultiply(1.5f); // 增加光栅化乘数，使字体更清晰

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
    private void loadCustomFonts(ImGuiIO io) {
        if (fontDataBytes == null || chineseGlyphRanges == null || baseFontConfig == null) {
            NodeCraft.LOGGER.error("字体数据未预加载或加载失败，无法加载自定义字体。");
            fallbackToDefaultFont(io);
            return;
        }
        
        try {
            io.getFonts().clear(); // 清除现有字体（包括默认字体）

            // 添加16pt字体，并设置为默认字体
            // 克隆 fontConfig 以便为不同大小的字体进行调整
            ImFontConfig fontConfig16 = getImFontConfig();

            io.getFonts().addFontFromMemoryTTF(fontDataBytes, 16.0f, fontConfig16, chineseGlyphRanges);
            NodeCraft.LOGGER.info("已加载16pt中文字体。");

            // 添加20pt字体
            ImFontConfig fontConfig20 = new ImFontConfig();
            fontConfig20.setFontDataOwnedByAtlas(false);
            fontConfig20.setMergeMode(baseFontConfig.getMergeMode());
            fontConfig20.setPixelSnapH(baseFontConfig.getPixelSnapH());
            fontConfig20.setOversampleH(baseFontConfig.getOversampleH());
            fontConfig20.setOversampleV(baseFontConfig.getOversampleV());
            fontConfig20.setRasterizerMultiply(baseFontConfig.getRasterizerMultiply());

            io.getFonts().addFontFromMemoryTTF(fontDataBytes, 20.0f, fontConfig20, chineseGlyphRanges);
            NodeCraft.LOGGER.info("已加载20pt中文字体。");

            // 构建字体图集
            if (!io.getFonts().build()) {
                NodeCraft.LOGGER.error("字体构建失败，尝试回退到默认字体。");
                fallbackToDefaultFont(io);
            } else {
                NodeCraft.LOGGER.info("成功构建所有字体。");
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("加载自定义字体时出错", e);
            fallbackToDefaultFont(io);
        }
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
    private void fallbackToDefaultFont(ImGuiIO io) {
        NodeCraft.LOGGER.warn("正在使用默认字体，中文可能无法正确显示。");
        try {
            io.getFonts().clear(); // 清除所有字体
            
            ImFontConfig defaultFontConfig = new ImFontConfig();
            defaultFontConfig.setPixelSnapH(true);
            defaultFontConfig.setOversampleH(3);
            defaultFontConfig.setOversampleV(3);
            
            // 直接添加默认字体，不指定字符范围，因为 addFontDefault 不支持
            io.getFonts().addFontDefault(defaultFontConfig);
            
            // 构建默认字体图集
            if (!io.getFonts().build()) {
                NodeCraft.LOGGER.error("构建默认字体失败，回退到ImGui内置最简字体。");
                io.getFonts().clear(); // 确保清除所有
                io.getFonts().addFontDefault(); // ImGui内置的最小字体
                io.getFonts().build();
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("设置备选字体时出错", e);
            NodeCraft.LOGGER.error("无法加载任何字体，ImGui显示可能异常！", e);
        }
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
     * 注册 ImGui 创建的窗口句柄。
     * 此方法通常不直接在 beginFrame 中调用，因为 ImGui 的 ViewportsEnable 会自动处理。
     * 保留此方法仅作为示例或调试用途。
     */
    private void registerImGuiWindows() {
        // ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable) 检查已在 if 块外。
        // 在 Java ImGui 绑定中，我们无法直接迭代所有平台窗口句柄以手动注册。
        // ImGui.updatePlatformWindows() 会在内部处理。
        // 确保 WindowManager 和 ImGuiWindowHandler 能够以其他方式获取这些句柄（例如通过回调或轮询）。
        // 这里的逻辑通常是冗余的或需要更复杂的JNI/原生集成。
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
            imGuiGl3.renderDrawData(ImGui.getDrawData());

            if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
                final long backupCurrentContext = GLFW.glfwGetCurrentContext();
                ImGui.updatePlatformWindows();
                ImGui.renderPlatformWindowsDefault();
                GLFW.glfwMakeContextCurrent(backupCurrentContext);
                ensureWindowLayering();
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
    private void setupMinecraftStyle() {
        ImGui.styleColorsDark(); // 使用暗色主题作为基础
        
        var style = ImGui.getStyle();
        
        // 窗口圆角
        style.setWindowRounding(4.0f);
        style.setFrameRounding(2.0f);
        style.setTabRounding(2.0f);
        style.setPopupRounding(2.0f);
        style.setScrollbarRounding(3.0f);
        style.setGrabRounding(2.0f);
        
        // 间距和内边距
        style.setWindowPadding(10.0f, 10.0f);
        style.setFramePadding(6.0f, 6.0f);
        style.setItemSpacing(8.0f, 8.0f);
        style.setItemInnerSpacing(5.0f, 5.0f);
        
        // 边框和最小尺寸
        style.setWindowBorderSize(1.5f);
        style.setFrameBorderSize(1.0f);
        style.setWindowMinSize(400.0f, 300.0f);
        
        // 滚动条和抓取柄大小
        style.setGrabMinSize(12.0f);
        style.setScrollbarSize(18.0f);
        
        // 标题对齐
        style.setWindowTitleAlign(0.5f, 0.5f); // 居中对齐
        
        // 颜色主题
        style.setColor(ImGuiCol.WindowBg, 0.16f, 0.16f, 0.20f, 0.94f);
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
    
    /**
     * 关闭 ImGui 上下文和后端，释放资源。
     */
    public void shutdown() {
        if (!initialized) return;
        
        try {
            closeDetector.cleanup();
            windowHandler.cleanup();
            windowManager.cleanup();
            
            imGuiGl3.dispose();
            imGuiGlfw.dispose();
            initialized = false;
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
}