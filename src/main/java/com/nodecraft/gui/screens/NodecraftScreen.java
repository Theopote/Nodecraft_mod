package com.nodecraft.gui.screens;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.core.event.EditorEvent;
import com.nodecraft.core.event.EditorEventBus;
import com.nodecraft.core.event.EditorUIEvent;
import com.nodecraft.gui.editor.base.INodeEditor;
import com.nodecraft.gui.editor.integration.ImGuiRenderer;

import com.nodecraft.gui.layout.LayoutManager;
import com.nodecraft.gui.layout.PanelVisibilityManager;
import com.nodecraft.gui.layout.LayoutConfig;

import com.nodecraft.minecraft.client.GhostCameraManager;

import imgui.ImGui;
import imgui.flag.ImGuiPopupFlags;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

/**
 * NodeCraft编辑器主屏幕
 * 重构后的版本 - 主要负责协调各个子系统
 * 实现"幽灵相机"模式，提供更直观的编辑体验
 */
public class NodecraftScreen extends Screen {

    // 核心组件
    private INodeEditor currentEditor;
    private final EditorEventBus eventBus = new EditorEventBus();
    
    // 窗口属性
    public float windowWidth = 800;
    public float windowHeight = 600;
    public float windowX = 100;
    public float windowY = 100;
    public boolean showMenuBar = true;
    public boolean closeRequested = false;
    
    // 管理器和渲染器
    private LayoutManager layoutManager;
    private ComponentManager componentManager;
    private LayoutRenderer layoutRenderer;
    private MenuBarRenderer menuBarRenderer;
    private NodecraftWindowRenderer windowRenderer;
    
    // 子系统管理器
    private final NodecraftLifecycleManager lifecycleManager;
    private final NodecraftInputHandler inputHandler;
    
    private GhostCameraManager ghostCameraManager = GhostCameraManager.getInstance();
    
    // 配置和状态
    private LayoutConfig layoutConfig = LayoutConfig.createDefault();
    private final PanelVisibilityManager panelVisibilityManager = new PanelVisibilityManager();
    private boolean initialized = false;
    
    // 用于跟踪分隔线拖拽状态，以优化布局更新
    private boolean wasDraggingSplitter = false;
    private boolean previousWorldLeftMouseDown = false;
    private boolean previousWorldRightMouseDown = false;

    /**
     * 构造Nodecraft编辑器界面
     */
    public NodecraftScreen() {
        super(Text.translatable("nodecraft.screen.editor"));
        this.lifecycleManager = new NodecraftLifecycleManager(this, this.ghostCameraManager);
        this.inputHandler = new NodecraftInputHandler(this, this.ghostCameraManager);
    }

    @Override
    protected void init() {
        if (initialized) return;
        
        super.init();
        
        try {
            // 检查 ImGuiRenderer 是否已正确初始化
            ImGuiRenderer imGuiRenderer = ImGuiRenderer.getInstance();
            if (!imGuiRenderer.isInitialized()) {
                imGuiRenderer.init();
                if (!imGuiRenderer.isInitialized()) {
                    NodeCraft.LOGGER.error("无法初始化 ImGui 渲染器，编辑器将无法启动");
                    closeRequested = true;
                    return;
                }
            }
            
            lifecycleManager.initialize();
            initialized = true;
        } catch (Exception e) {
            NodeCraft.LOGGER.error("初始化失败: {}", e.getMessage(), e);
            closeRequested = true;
        }
    }



    /**
     * 屏幕的主渲染循环，每帧调用一次。
     * <p>
     * 此方法负责：
     * <ul>
     *   <li>检查初始化状态</li>
     *   <li>处理关闭请求</li>
     *   <li>管理ImGui帧</li>
     *   <li>更新交互管理器</li>
     *   <li>协调ImGui和传统GUI渲染</li>
     * </ul>
     * 设计为幂等且故障安全，每次调用都会进行必要的状态检查。
     *
     * @param context 绘制上下文（用于传统GUI渲染）
     * @param mouseX  鼠标X坐标
     * @param mouseY  鼠标Y坐标
     * @param delta   帧时间增量
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 处理错误状态
        if (closeRequested && !initialized) {
            renderErrorMessage(context);
            return;
        }
        
        if (!initialized) {
            String loadingMessage = "NodeCraft 正在初始化...";
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(loadingMessage),
                this.width / 2, this.height / 2, EditorConstants.COLOR_TEXT_HINT);
            return;
        }
        
        // 缓存鼠标在GUI上的状态，供本帧的所有输入事件使用
        // 用于在每帧中缓存鼠标是否在GUI上方的状态
        boolean isMouseOverGuiCached = isMouseOverNodecraftGui(mouseX, mouseY);
        
        if (currentEditor == null) {
            String errorMessage = "无法加载编辑器 - 请检查日志";
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(errorMessage), 
                this.width / 2, this.height / 2, EditorConstants.COLOR_TEXT_ERROR);
            return;
        }
        
        // 处理关闭请求
        if (consumeCloseRequest()) {
            handleCloseRequest();
            return;
        }
        
        // 更新交互管理器
        updateInteractionManager();

        // 统一从当前活动 GLFW 窗口轮询业务快捷键，避免 detached 模式依赖 Screen 事件链
        inputHandler.pollEditorShortcuts();
        
        // 渲染主窗口
        if (windowRenderer != null) {
            windowRenderer.render(context, mouseX, mouseY, delta);
        }
        
        // 更新幽灵相机
        updateGhostCamera();
        
            // 更新布局配置
    updateLayoutConfigFromRenderer();
}

// 私有辅助方法

private void renderErrorMessage(DrawContext context) {
    String errorMessage = "无法初始化 ImGui 渲染器 - 请检查日志获取更多信息";
    context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(errorMessage),
            this.width / 2, this.height / 2, EditorConstants.COLOR_TEXT_ERROR);
    
    String buttonHint = "按 ESC 键返回主菜单";
    context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(buttonHint),
            this.width / 2, this.height / 2 + 20, EditorConstants.COLOR_TEXT_HINT);
}

private void handleCloseRequest() {
    NodeCraft.LOGGER.info("检测到closeRequested标志，立即关闭Nodecraft窗口");
    MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(null));
}

private void updateInteractionManager() {
    try {
        com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager interactionManager = 
            com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager.getInstance();
        if (interactionManager.isInEditorMode()) {
            // 获取鼠标状态
            float imGuiMouseX = ImGui.getIO().getMousePosX();
            float imGuiMouseY = ImGui.getIO().getMousePosY();
            boolean isMiddleMouseDown = ImGui.isMouseDown(2); // 2 = 中键
            boolean isLeftMouseClicked = interactionManager.isInInteractionMode()
                ? pollWorldMouseClicked(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                : ImGui.isMouseClicked(0); // 0 = 左键
            boolean isRightMouseClicked = interactionManager.isInInteractionMode()
                ? pollWorldMouseClicked(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                : ImGui.isMouseClicked(1); // 1 = 右键
            
            // 检查鼠标是否在ImGui界面上，但在交互模式下需要特殊处理
            boolean isMouseOverImGui = isMouseOverImGuiForInteraction(interactionManager);

            // 添加调试日志（仅在点击时或交互模式下）
            boolean isInInteractionMode = interactionManager.isInInteractionMode();
                if (isLeftMouseClicked || isRightMouseClicked || isInInteractionMode) {
                NodeCraft.LOGGER.info("交互管理器更新 - 编辑模式:{} 交互模式:{} 鼠标位置:({},{}) 左键点击:{} 鼠标在UI上:{}", 
                    interactionManager.isInEditorMode(), isInInteractionMode, 
                    imGuiMouseX, imGuiMouseY, isLeftMouseClicked, isMouseOverImGui);
            }

            // 调用更新方法
            interactionManager.update(
                imGuiMouseX, imGuiMouseY, 
                isMiddleMouseDown, isLeftMouseClicked, isRightMouseClicked,
                isMouseOverImGui
            );
        }
    } catch (Exception e) {
        NodeCraft.LOGGER.error("更新交互管理器时出错: {}", e.getMessage(), e);
    }
}

private boolean pollWorldMouseClicked(int button) {
    MinecraftClient client = MinecraftClient.getInstance();
    if (client == null || client.getWindow() == null) {
        previousWorldLeftMouseDown = false;
        previousWorldRightMouseDown = false;
        return false;
    }

    long windowHandle = client.getWindow().getHandle();
    if (windowHandle == 0L) {
        previousWorldLeftMouseDown = false;
        previousWorldRightMouseDown = false;
        return false;
    }

    boolean currentDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, button)
        == org.lwjgl.glfw.GLFW.GLFW_PRESS;

    if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
        boolean clicked = currentDown && !previousWorldLeftMouseDown;
        previousWorldLeftMouseDown = currentDown;
        return clicked;
    }

    if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
        boolean clicked = currentDown && !previousWorldRightMouseDown;
        previousWorldRightMouseDown = currentDown;
        return clicked;
    }

    return false;
}

/**
 * 为交互模式优化的鼠标状态检测
 * 在某些交互模式（如方块拾取）下，即使鼠标在UI上，也需要允许世界交互
 */
private boolean isMouseOverImGuiForInteraction(com.nodecraft.nodesystem.interaction.NodeEditorInteractionManager interactionManager) {
    // 安全检查：确保ImGui已初始化且有IO对象
    if (ImGui.getIO() == null) {
        return false;
    }
    
    boolean isMouseOverImGui;
    
    try {
        // 检查是否在交互模式下
        boolean isInInteractionMode = interactionManager.isInInteractionMode();
        
        // 如果在交互模式下，优先考虑世界交互
        if (isInInteractionMode) {
            // 在交互模式下，只有当鼠标明确在窗口边界内时才认为是在UI上
            float mouseX = ImGui.getIO().getMousePosX();
            float mouseY = ImGui.getIO().getMousePosY();
            
            boolean inWindowBounds = (mouseX >= windowX && mouseX <= windowX + windowWidth &&
                                    mouseY >= windowY && mouseY <= windowY + windowHeight);
            
            // 在交互模式下，如果鼠标在窗口外，肯定不在UI上
            if (!inWindowBounds) {
                return false;
            }
            
            // 如果在窗口内，使用基本的 WantCaptureMouse 检测
            // 移除 ImGui.isWindowHovered() 调用以避免断言失败
            isMouseOverImGui = ImGui.getIO().getWantCaptureMouse();
            
            // 在交互模式下，如果鼠标在窗口边界内但 WantCaptureMouse 为 false，
            // 倾向于认为不在UI上，优先处理世界交互
            if (!isMouseOverImGui) {
                return false;
            }
            
            // 只要 ImGui 明确要求捕获鼠标（例如菜单、弹窗、控件交互），
            // 就应稳定地视为在 UI 上，避免菜单在移动鼠标时被误关闭。
            return true;
            
        } else {
            // 非交互模式下，使用标准的检测逻辑
            isMouseOverImGui = ImGui.getIO().getWantCaptureMouse();
            
            // 作为备用方案，使用窗口边界检查
            if (!isMouseOverImGui && windowRenderer != null) {
                float mouseX = ImGui.getIO().getMousePosX();
                float mouseY = ImGui.getIO().getMousePosY();
                
                isMouseOverImGui = (mouseX >= windowX && mouseX <= windowX + windowWidth &&
                                   mouseY >= windowY && mouseY <= windowY + windowHeight);
            }
        }
        
    } catch (Exception e) {
        // 如果ImGui调用失败，记录警告但不崩溃
        NodeCraft.LOGGER.warn("检查ImGui鼠标状态时出错: {}", e.getMessage());
        return false;
    }

    // 检查是否有自定义UI处于激活状态（仅在非交互模式下考虑）
    boolean hasCustomUIActive = false;
    if (currentEditor != null && currentEditor instanceof com.nodecraft.gui.editor.impl.ICanvasEditor canvasEditor) {
        try {
            hasCustomUIActive = canvasEditor.getInteraction().isNodeCustomUIActive();
        } catch (Exception e) {
            NodeCraft.LOGGER.warn("检查自定义UI状态时出错: {}", e.getMessage());
        }
    }

    // 在交互模式下，不考虑自定义UI状态
    boolean isInInteractionMode = interactionManager.isInInteractionMode();
    
    if (isInInteractionMode) {
        return isMouseOverImGui; // 在交互模式下，不考虑自定义UI
    } else {
        return isMouseOverImGui || hasCustomUIActive; // 非交互模式下，考虑自定义UI
    }
}

private void updateGhostCamera() {
    if (ghostCameraManager.isEnabled()) {
        // Mouse-ray interaction already renders the only highlight we want.
    }
}

// 公共API方法
public boolean postEvent(EditorEvent event) {
    return eventBus.postEvent(event);
}

public EditorEventBus getEventBus() {
    return eventBus;
}

// Getter和Setter方法
public ComponentManager getComponentManager() { return componentManager; }
public INodeEditor getCurrentEditor() { return currentEditor; }
public LayoutConfig getLayoutConfig() { return layoutConfig; }
public LayoutRenderer getLayoutRenderer() { return layoutRenderer; }
public MenuBarRenderer getMenuBarRenderer() { return menuBarRenderer; }
public NodecraftWindowRenderer getWindowRenderer() { return windowRenderer; }

public void setCurrentEditor(INodeEditor editor) { this.currentEditor = editor; }
public void setLayoutManager(LayoutManager manager) { this.layoutManager = manager; }
public void setComponentManager(ComponentManager manager) { this.componentManager = manager; }
public void setLayoutRenderer(LayoutRenderer renderer) { this.layoutRenderer = renderer; }
public void setMenuBarRenderer(MenuBarRenderer renderer) { this.menuBarRenderer = renderer; }
public void setWindowRenderer(NodecraftWindowRenderer renderer) { this.windowRenderer = renderer; }

public boolean isShowMenuBar() { return showMenuBar; }
public boolean isEditorDetached() { return ImGuiRenderer.getInstance().isDetachedEditorOpen(this); }
public void detachEditorToExternalWindow() { ImGuiRenderer.getInstance().openDetachedEditorWindow(this); }
public void attachEditorToMainWindow() { ImGuiRenderer.getInstance().closeDetachedEditorWindow(this); }
public void requestClose() { this.closeRequested = true; }
public boolean isCloseRequested() { return this.closeRequested; }
public boolean consumeCloseRequest() {
    if (!this.closeRequested) {
        return false;
    }
    this.closeRequested = false;
    return true;
}

// 缺失的方法实现
public boolean isMouseOverNodecraftGui(double mouseX, double mouseY) {
    if (isEditorDetached()) {
        return false;
    }

    if (initialized && ImGui.getIO() != null) {
        // 菜单/子菜单/弹窗打开期间，始终视为鼠标在UI上，
        // 避免从一级菜单移动到二级菜单时被误判并导致菜单收起。
        if (ImGui.isPopupOpen("", ImGuiPopupFlags.AnyPopupId)) {
            return true;
        }

        boolean imguiWantsMouse = isImGuiWantCaptureMouse();
        boolean inWindowBounds = (mouseX >= windowX && mouseX <= windowX + windowWidth &&
                                mouseY >= windowY && mouseY <= windowY + windowHeight);
        return imguiWantsMouse || inWindowBounds;
    }
    
    return mouseX >= windowX && mouseX <= windowX + windowWidth &&
           mouseY >= windowY && mouseY <= windowY + windowHeight;
}

public boolean isImGuiWantCaptureMouse() {
    return isSplitterInputLocked()
        || (ImGui.getIO() != null && ImGui.getIO().getWantCaptureMouse());
}

public boolean isSplitterInputLocked() {
    return layoutRenderer != null && layoutRenderer.isDraggingSplitter();
}

public boolean isImGuiWantCaptureKeyboard() {
    return ImGui.getIO() != null && ImGui.getIO().getWantCaptureKeyboard();
}

    // 输入事件委托给输入处理器
    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (!initialized) return super.mouseClicked(click, doubleClick);
        if (click.button() != org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                && !isImGuiWantCaptureMouse()
                && !isMouseOverNodecraftGui(click.x(), click.y())) {
            return false;
        }
        return inputHandler.handleMouseClicked(click.x(), click.y(), click.button());
    }
    
    @Override
    public boolean mouseReleased(Click click) {
        if (!initialized) return super.mouseReleased(click);
        return inputHandler.handleMouseReleased(click.x(), click.y(), click.button());
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!initialized) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        return inputHandler.handleMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    @Override
    public boolean mouseDragged(Click click, double mouseX, double mouseY) {
        if (!initialized) return super.mouseDragged(click, mouseX, mouseY);
        return inputHandler.handleMouseDragged(mouseX, mouseY, click.button(), mouseX - click.x(), mouseY - click.y());
    }
    
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!initialized) {
            super.mouseMoved(mouseX, mouseY);
            return;
        }
        
        if (!inputHandler.handleMouseMoved(mouseX, mouseY)) {
            super.mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (!initialized) {
            return super.keyPressed(keyInput);
        }
        
        // 委托给 inputHandler
        return isImGuiWantCaptureKeyboard()
            || isMouseOverNodecraftGui(
                MinecraftClient.getInstance().mouse.getX(),
                MinecraftClient.getInstance().mouse.getY()
            );
    }

    @Override
    public boolean keyReleased(KeyInput keyInput) {
        if (!initialized) {
            return super.keyReleased(keyInput);
        }
        
        // 委托给 inputHandler
        return isImGuiWantCaptureKeyboard()
            || isMouseOverNodecraftGui(
                MinecraftClient.getInstance().mouse.getX(),
                MinecraftClient.getInstance().mouse.getY()
            );
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        if (!initialized) {
            return super.charTyped(charInput);
        }
        // 只要 ImGui 想要捕获键盘事件，就阻止事件向下传递
        return isImGuiWantCaptureKeyboard();
    }

    @Override
    public void close() {
        NodeCraft.LOGGER.info("执行Nodecraft窗口关闭操作");
        super.close();
    }

    @Override
    public void removed() {
        cleanup();
        super.removed();
    }

    /**
     * 统一的资源清理方法，确保所有资源按正确顺序释放
     * 设计为幂等操作，可以被多次调用而不会产生副作用
     */
    public void cleanup() {
        if (!initialized) {
            return;
        }

        try {
            lifecycleManager.cleanup();
        } catch (Exception e) {
            NodeCraft.LOGGER.error("清理过程中发生意外错误: {}", e.getMessage(), e);
        } finally {
            initialized = false;
            closeRequested = false;
        }
    }

    /**
     * 切换节点库面板的可见性
     */
    public void toggleNodePanel() {
        LayoutConfig newConfig = panelVisibilityManager.toggleNodePanel(layoutConfig);

        // 验证配置是否有效
        if (!panelVisibilityManager.isValidConfig(newConfig)) {
            NodeCraft.LOGGER.error("切换节点库面板时生成的布局配置无效，比例总和不为1.0");
            return;
        }

        // 更新布局配置引用
        this.layoutConfig = newConfig;

        // 更新布局配置
        if (layoutRenderer != null) {
            layoutRenderer.updateLayoutConfig(newConfig);
        }
        // 发布事件
        postEvent(new EditorUIEvent(EditorUIEvent.Type.LAYOUT_CHANGED, "main_screen"));
    }

    /**
     * 切换属性面板的可见性
     */
    public void togglePropertyPanel() {
        LayoutConfig newConfig = panelVisibilityManager.togglePropertyPanel(layoutConfig);

        // 验证配置是否有效
        if (!panelVisibilityManager.isValidConfig(newConfig)) {
            NodeCraft.LOGGER.error("切换属性面板时生成的布局配置无效，比例总和不为1.0");
            return;
        }

        // 更新布局配置引用
        this.layoutConfig = newConfig;

        // 更新布局配置
        if (layoutRenderer != null) {
            layoutRenderer.updateLayoutConfig(newConfig);
        }
        // 发布事件
        postEvent(new EditorUIEvent(EditorUIEvent.Type.LAYOUT_CHANGED, "main_screen"));
    }

    /**
     * 检查节点库面板是否可见
     * @return 节点库面板是否可见
     */
    public boolean isNodePanelVisible() {
        return panelVisibilityManager.isNodePanelVisible();
    }

    /**
     * 检查属性面板是否可见
     * @return 属性面板是否可见
     */
    public boolean isPropertyPanelVisible() {
        return panelVisibilityManager.isPropertyPanelVisible();
    }

    /**
     * 更新内部的布局配置状态（从LayoutRenderer获取最新配置）
     * 此方法应在每帧渲染结束时调用，以同步拖拽分隔线导致的布局更改
     */
    public void updateLayoutConfigFromRenderer() {
        if (layoutRenderer == null) {
            return;
        }

        boolean isCurrentlyDragging = layoutRenderer.isDraggingSplitter();

        // 当拖拽操作刚刚结束时，执行更新
        if (this.wasDraggingSplitter && !isCurrentlyDragging) {
            LayoutConfig rendererConfig = layoutRenderer.getLayoutConfig();

            // 只有当配置实际发生变化时才更新
            if (rendererConfig != null && !rendererConfig.equals(this.layoutConfig)) {

                // 记录旧配置以供日志记录
                float oldNodePanel = this.layoutConfig.nodePanelRatio();
                float oldCanvas = this.layoutConfig.canvasRatio();
                float oldPropertyPanel = this.layoutConfig.propertyPanelRatio();

                // 更新布局配置
                this.layoutConfig = rendererConfig;

                // 记录更新
                NodeCraft.LOGGER.info("布局比例已通过拖拽更新: 节点面板 {}->{}, 画布 {}->{}, 属性面板 {}->{}",
                        String.format("%.2f", oldNodePanel), String.format("%.2f", rendererConfig.nodePanelRatio()),
                        String.format("%.2f", oldCanvas), String.format("%.2f", rendererConfig.canvasRatio()),
                        String.format("%.2f", oldPropertyPanel), String.format("%.2f", rendererConfig.propertyPanelRatio()));

                // 发布布局变更事件
                postEvent(new EditorUIEvent(EditorUIEvent.Type.LAYOUT_CHANGED, "main_screen_drag_end"));
            }
        }

        // 为下一帧更新拖拽状态
        this.wasDraggingSplitter = isCurrentlyDragging;
    }

    /**
     * 控制当打开NodeCraft编辑器时游戏是否暂停
     * 默认返回true (暂停游戏)，但会被Mixin覆盖为false
     */
    @Override
    public boolean shouldPause() {
        // 非常重要：编辑器打开时不暂停游戏，使幽灵相机模式可以工作
        return false;
    }

    @Override
    protected void clearAndInit() {
        super.clearAndInit();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // 禁用原版半透明模糊背景，保持游戏画面可见
    }

    /**
     * 公开的clearAndInit方法，允许外部调用
     */
    public void publicClearAndInit() {
        this.clearAndInit();
    }
}
