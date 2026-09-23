package com.nodecraft.gui.screens;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.panel.CanvasComponent;
import com.nodecraft.gui.editor.integration.ImGuiRenderer;
import com.nodecraft.gui.layout.ImGuiChildScope;
import com.nodecraft.gui.style.ImGuiStyleScope;
import com.nodecraft.gui.style.MinecraftTheme;
import com.nodecraft.gui.utils.ImGuiStyleVar;
import com.nodecraft.gui.window.ViewportCloseDetector;
import com.nodecraft.gui.window.WindowManager;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Renders the NodeCraft editor window and its ImGui frame structure.
 */
public class NodecraftWindowRenderer {

    private final NodecraftScreen parentScreen;
    private final ImGuiRenderer imGuiRenderer;
    private final WindowManager windowManager;
    private final ViewportCloseDetector closeDetector;
    private final MinecraftTheme theme;
    private ImGuiStyleScope styleScope;
    private float lastKnownScreenWidth = -1f;
    private float lastKnownScreenHeight = -1f;

    public NodecraftWindowRenderer(NodecraftScreen parentScreen) {
        this.parentScreen = parentScreen;
        this.imGuiRenderer = ImGuiRenderer.getInstance();
        this.windowManager = WindowManager.getInstance();
        this.closeDetector = ViewportCloseDetector.getInstance();
        this.theme = new MinecraftTheme();
        this.styleScope = new ImGuiStyleScope();
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        try {
            imGuiRenderer.beginFrame();
            try {
                renderWithStyles(context, mouseX, mouseY, delta);
            } finally {
                imGuiRenderer.endFrame();
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染错误: {}", e.getMessage(), e);
            parentScreen.requestClose();
        }
    }

    private void renderWithStyles(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean stylesApplied = false;
        try {
            applyStyles();
            stylesApplied = true;

            if (parentScreen.isEditorDetached()) {
                renderDetachedWindow();
            } else {
                renderMainWindow(context, mouseX, mouseY, delta);

                if (!parentScreen.isCloseRequested()) {
                    renderDialogs();
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染 ImGui 界面错误: {}", e.getMessage(), e);
        } finally {
            if (stylesApplied) {
                popStyles();
            }
        }
    }

    private void renderMainWindow(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean viewportsEnabled = ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable);

        MinecraftClient client = MinecraftClient.getInstance();
        float screenWidth = client.getWindow().getWidth();
        float screenHeight = client.getWindow().getHeight();

        float minVisibleWidth = 320.0f;
        float minVisibleHeight = 180.0f;

        float maxX = Math.max(0.0f, screenWidth - minVisibleWidth);
        float maxY = Math.max(0.0f, screenHeight - minVisibleHeight);

        parentScreen.windowX = Math.max(0.0f, Math.min(parentScreen.windowX, maxX));
        parentScreen.windowY = Math.max(0.0f, Math.min(parentScreen.windowY, maxY));

        float minWidth = EditorConstants.MIN_WINDOW_WIDTH;
        float minHeight = EditorConstants.MIN_WINDOW_HEIGHT;
        parentScreen.windowWidth = Math.max(minWidth, Math.min(parentScreen.windowWidth, screenWidth));
        parentScreen.windowHeight = Math.max(minHeight, Math.min(parentScreen.windowHeight, screenHeight));

        LayoutRenderer layoutRenderer = parentScreen.getLayoutRenderer();
        if (layoutRenderer != null && lastKnownScreenWidth >= 0f) {
            if (Math.abs(screenWidth - lastKnownScreenWidth) > 0.5f
                    || Math.abs(screenHeight - lastKnownScreenHeight) > 0.5f) {
                layoutRenderer.deferNextLayoutFrame();
            }
        }
        lastKnownScreenWidth = screenWidth;
        lastKnownScreenHeight = screenHeight;

        ImGui.setNextWindowPos(parentScreen.windowX, parentScreen.windowY, ImGuiCond.Appearing);
        ImGui.setNextWindowSize(parentScreen.windowWidth, parentScreen.windowHeight, ImGuiCond.Appearing);
        ImGui.setNextWindowCollapsed(false, ImGuiCond.Appearing);

        int windowFlags = getWindowFlags(viewportsEnabled);

        String windowTitle = viewportsEnabled
            ? "NodeCraft 编辑器 - 独立窗口模式"
            : "NodeCraft 编辑器";

        boolean windowOpened;
        try {
            if (viewportsEnabled) {
                windowOpened = ImGui.begin(windowTitle, closeDetector.getWindowOpenFlag(), windowFlags);
            } else {
                windowOpened = ImGui.begin(windowTitle, windowFlags | ImGuiWindowFlags.NoSavedSettings);
            }

            if (viewportsEnabled && !closeDetector.getWindowOpenFlag().get()) {
                parentScreen.requestClose();
                return;
            }

            if (windowOpened) {
                handleWindowAssociation();
                updateWindowDimensions();

                boolean skipLayout = layoutRenderer != null && layoutRenderer.peekLayoutRenderSkip();
                renderWindowContent();
                if (!skipLayout) {
                    renderLayoutContent(context, mouseX, mouseY, delta);
                } else if (layoutRenderer != null) {
                    layoutRenderer.consumeLayoutRenderSkip();
                }
            }
        } finally {
            ImGuiChildScope.unwindAll();
            ImGui.end();
        }
    }

    private int getWindowFlags(boolean viewportsEnabled) {
        LayoutRenderer layoutRenderer = parentScreen.getLayoutRenderer();
        boolean lockWindowMoveForSplitter = layoutRenderer != null
            && (layoutRenderer.isDraggingSplitter() || layoutRenderer.isHoveringSplitter());
        boolean lockWindowMoveForCanvas = false;
        if (parentScreen.getComponentManager() != null) {
            CanvasComponent canvas = parentScreen.getComponentManager().getCanvasComponent();
            lockWindowMoveForCanvas = canvas != null && canvas.requestsParentWindowNoMove();
        }
        return createWindowFlags(viewportsEnabled, lockWindowMoveForSplitter || lockWindowMoveForCanvas);
    }

    private int createWindowFlags(boolean viewportsEnabled, boolean lockWindowMove) {
        int windowFlags = ImGuiWindowFlags.MenuBar | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoTitleBar;

        if (lockWindowMove) {
            windowFlags |= ImGuiWindowFlags.NoMove;
        }

        if (viewportsEnabled) {
            windowFlags |= ImGuiWindowFlags.NoDocking;
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

    private void renderLayoutContent(DrawContext context, int mouseX, int mouseY, float delta) {
        LayoutRenderer layoutRenderer = parentScreen.getLayoutRenderer();
        if (layoutRenderer == null) {
            return;
        }
        try {
            layoutRenderer.render(context, mouseX, mouseY, delta);
        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染布局时出错: {}", e.getMessage(), e);
        }
    }

    private void renderWindowContent() {
        MenuBarRenderer menuBarRenderer = parentScreen.getMenuBarRenderer();

        if (parentScreen.showMenuBar && menuBarRenderer != null) {
            try {
                menuBarRenderer.render();
            } catch (Exception e) {
                NodeCraft.LOGGER.error("渲染菜单栏时出错: {}", e.getMessage(), e);
            }
        }
    }

    private void renderDetachedWindow() {
        try {
            ImGui.setNextWindowPos(0.0f, 0.0f, ImGuiCond.Always);
            ImGui.setNextWindowSize(ImGui.getIO().getDisplaySizeX(), ImGui.getIO().getDisplaySizeY(), ImGuiCond.Always);

            final int windowFlags = ImGuiWindowFlags.MenuBar
                | ImGuiWindowFlags.NoTitleBar
                | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoCollapse
                | ImGuiWindowFlags.NoSavedSettings;

            LayoutRenderer layoutRenderer = parentScreen.getLayoutRenderer();
            boolean skipLayout = layoutRenderer != null && layoutRenderer.peekLayoutRenderSkip();
            if (ImGui.begin("NodeCraft Detached Editor", windowFlags)) {
                renderWindowContent();
                if (!skipLayout && layoutRenderer != null) {
                    layoutRenderer.renderImGuiOnly(0.0f);
                } else if (skipLayout && layoutRenderer != null) {
                    layoutRenderer.consumeLayoutRenderSkip();
                }
            }
            ImGuiChildScope.unwindAll();
            ImGui.end();

            if (!parentScreen.isCloseRequested()) {
                renderDialogs();
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("渲染独立编辑器窗口时出错: {}", e.getMessage(), e);
            parentScreen.requestClose();
        }
    }

    private void renderDialogs() {
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
        styleScope.pushStyleVar(
            ImGuiStyleVar.WindowPadding,
            EditorConstants.DEFAULT_WINDOW_PADDING,
            EditorConstants.DEFAULT_WINDOW_PADDING
        );
        styleScope.pushStyleVar(
            ImGuiStyleVar.ItemSpacing,
            EditorConstants.DEFAULT_ITEM_SPACING,
            EditorConstants.DEFAULT_ITEM_SPACING
        );
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
