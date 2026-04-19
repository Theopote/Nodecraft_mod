package com.nodecraft.gui.screens;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.components.CanvasComponent;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.minecraft.client.GhostCameraManager;
import imgui.ImGui;
import imgui.ImVec2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * NodeCraft editor input handler.
 *
 * <p>Mouse routing still goes through the Minecraft Screen integration layer.
 * Business shortcuts are polled directly from the active GLFW window so the
 * same path works for both attached and detached editor windows.
 */
public class NodecraftInputHandler {
    private static final int[] POLLED_SHORTCUT_KEYS = {
        GLFW.GLFW_KEY_DELETE,
        GLFW.GLFW_KEY_Z,
        GLFW.GLFW_KEY_Y,
        GLFW.GLFW_KEY_X,
        GLFW.GLFW_KEY_C,
        GLFW.GLFW_KEY_D,
        GLFW.GLFW_KEY_V,
        GLFW.GLFW_KEY_F1,
        GLFW.GLFW_KEY_N,
        GLFW.GLFW_KEY_O,
        GLFW.GLFW_KEY_S,
        GLFW.GLFW_KEY_F5,
        GLFW.GLFW_KEY_EQUAL,
        GLFW.GLFW_KEY_KP_ADD,
        GLFW.GLFW_KEY_MINUS,
        GLFW.GLFW_KEY_KP_SUBTRACT,
        GLFW.GLFW_KEY_0,
        GLFW.GLFW_KEY_KP_0,
        GLFW.GLFW_KEY_HOME
    };

    private final NodecraftScreen parentScreen;
    private final GhostCameraManager ghostCameraManager;
    private final java.util.Map<Integer, Boolean> previousShortcutKeyStates = new java.util.HashMap<>();

    public NodecraftInputHandler(NodecraftScreen parentScreen, GhostCameraManager ghostCameraManager) {
        this.parentScreen = parentScreen;
        this.ghostCameraManager = ghostCameraManager;
    }

    public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        boolean isMouseOverGui = parentScreen.isMouseOverNodecraftGui(mouseX, mouseY);

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return isMouseOverGui;
        }

        if (parentScreen.isImGuiWantCaptureMouse() || isMouseOverGui) {
            return true;
        }

        return handleGhostCameraBlockSelection(button);
    }

    public boolean handleMouseReleased(double mouseX, double mouseY, int button) {
        boolean isMouseOverGui = parentScreen.isMouseOverNodecraftGui(mouseX, mouseY);

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return isMouseOverGui;
        }

        return parentScreen.isImGuiWantCaptureMouse() || isMouseOverGui;
    }

    public boolean handleMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (parentScreen.isImGuiWantCaptureMouse()) {
            return true;
        }

        if (parentScreen.getCurrentEditor() instanceof com.nodecraft.gui.editor.impl.NativeNodeEditor nativeEditor) {
            return nativeEditor.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        return false;
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        boolean isMouseOverGui = parentScreen.isMouseOverNodecraftGui(mouseX, mouseY);

        if (parentScreen.isImGuiWantCaptureMouse() || isMouseOverGui) {
            return true;
        }

        return false;
    }

    public boolean handleMouseMoved(double mouseX, double mouseY) {
        return false;
    }

    public void pollEditorShortcuts() {
        final long windowHandle =
            com.nodecraft.gui.editor.integration.ImGuiRenderer.getInstance().getActiveInputWindowHandle();
        if (windowHandle == 0L) {
            return;
        }

        final boolean ctrlPressed =
            GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        final boolean shiftPressed =
            GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        int modifiers = 0;
        if (ctrlPressed) {
            modifiers |= GLFW.GLFW_MOD_CONTROL;
        }
        if (shiftPressed) {
            modifiers |= GLFW.GLFW_MOD_SHIFT;
        }

        for (int keyCode : POLLED_SHORTCUT_KEYS) {
            final boolean isDown = GLFW.glfwGetKey(windowHandle, keyCode) == GLFW.GLFW_PRESS;
            final boolean wasDown = previousShortcutKeyStates.getOrDefault(keyCode, false);

            if (isDown && !wasDown) {
                handleEditorShortcuts(keyCode, modifiers);
            }

            previousShortcutKeyStates.put(keyCode, isDown);
        }
    }

    private boolean handleEditorShortcuts(int keyCode, int modifiers) {
        boolean isCtrlPressed = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        ComponentManager componentManager = parentScreen.getComponentManager();

        if (componentManager == null) {
            return false;
        }

        CanvasComponent canvas = componentManager.getCanvasComponent();
        if (canvas == null || !(canvas.getNodeEditor() instanceof ImGuiNodeEditor editor)) {
            return false;
        }

        if (NodeCraft.LOGGER.isDebugEnabled()) {
            NodeCraft.LOGGER.debug("编辑器状态 - componentManager: {}, canvas: {}, editor: {}", true, true, true);
        }

        final boolean textInputActive = ImGui.getIO() != null
            && (ImGui.getIO().getWantTextInput() || ImGui.isAnyItemActive());

        if (handleGlobalShortcuts(editor, keyCode, isCtrlPressed, textInputActive)) {
            return true;
        }

        boolean wantCapture = ImGui.getIO() != null && ImGui.getIO().getWantCaptureKeyboard();
        if (wantCapture) {
            if (NodeCraft.LOGGER.isDebugEnabled()) {
                NodeCraft.LOGGER.debug("ImGui wants keyboard capture, skipping remaining business shortcuts");
            }
            return true;
        }

        return handleOtherShortcuts(editor, canvas, keyCode, modifiers);
    }

    private boolean handleGlobalShortcuts(
        ImGuiNodeEditor editor,
        int keyCode,
        boolean isCtrlPressed,
        boolean textInputActive
    ) {
        if (isCtrlPressed && keyCode == GLFW.GLFW_KEY_Z) {
            if (textInputActive) {
                return true;
            }
            NodeCraft.LOGGER.info("触发撤销快捷键: Ctrl+Z");
            if (editor.getHistory().canUndo()) {
                editor.undo();
                return true;
            }
            if (NodeCraft.LOGGER.isDebugEnabled()) {
                NodeCraft.LOGGER.debug("无法撤销: 历史记录为空");
            }
            return true;
        }

        if (isCtrlPressed && keyCode == GLFW.GLFW_KEY_Y) {
            if (textInputActive) {
                return true;
            }
            NodeCraft.LOGGER.info("触发重做快捷键: Ctrl+Y");
            if (editor.getHistory().canRedo()) {
                editor.redo();
                return true;
            }
            if (NodeCraft.LOGGER.isDebugEnabled()) {
                NodeCraft.LOGGER.debug("无法重做: 重做栈为空");
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (textInputActive) {
                return true;
            }
            boolean hasSelection = !editor.getSelectedNodeIds().isEmpty();
            if (hasSelection) {
                NodeCraft.LOGGER.info("触发删除快捷键: Delete");
                editor.deleteSelectedNodes();
            } else if (NodeCraft.LOGGER.isDebugEnabled()) {
                NodeCraft.LOGGER.debug("无法删除: 没有选中的节点");
            }
            return true;
        }

        return false;
    }

    private boolean handleOtherShortcuts(ImGuiNodeEditor editor, CanvasComponent canvas, int keyCode, int modifiers) {
        boolean isCtrlPressed = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean isShiftPressed = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean hasSelection = !editor.getSelectedNodeIds().isEmpty();

        if (hasSelection) {
            if (isCtrlPressed && keyCode == GLFW.GLFW_KEY_X) {
                NodeCraft.LOGGER.info("触发剪切快捷键: Ctrl+X");
                editor.cutSelectedNodes();
                return true;
            }

            if (isCtrlPressed && keyCode == GLFW.GLFW_KEY_C) {
                NodeCraft.LOGGER.info("触发复制快捷键: Ctrl+C");
                editor.copySelectedNodes();
                return true;
            }

            if (isCtrlPressed && keyCode == GLFW.GLFW_KEY_D) {
                NodeCraft.LOGGER.info("触发复制节点快捷键: Ctrl+D");
                editor.duplicateSelectedNode();
                return true;
            }
        }

        if (isCtrlPressed && keyCode == GLFW.GLFW_KEY_V) {
            NodeCraft.LOGGER.info("触发粘贴快捷键: Ctrl+V");
            ImVec2 centerWorldPos = canvas.getCanvasCenterWorldPosition();
            editor.pasteNodesAtPosition(centerWorldPos.x, centerWorldPos.y);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F1) {
            ghostCameraManager.toggle();
            NodeCraft.LOGGER.info("幽灵相机模式已切换: {}", ghostCameraManager.isEnabled() ? "开启" : "关闭");
            return true;
        }

        MenuBarRenderer menuBar = parentScreen.getMenuBarRenderer();
        if (menuBar != null) {
            if (isCtrlPressed && keyCode == GLFW.GLFW_KEY_N) {
                NodeCraft.LOGGER.info("触发新建快捷键: Ctrl+N");
                menuBar.createNewNodeGraph();
                return true;
            }

            if (isCtrlPressed && keyCode == GLFW.GLFW_KEY_O) {
                NodeCraft.LOGGER.info("触发打开快捷键: Ctrl+O");
                menuBar.openNodeGraph();
                return true;
            }

            if (isCtrlPressed && keyCode == GLFW.GLFW_KEY_S) {
                NodeCraft.LOGGER.info("触发保存快捷键: Ctrl+S");
                menuBar.saveNodeGraph(false);
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_F5 && !isShiftPressed) {
                NodeCraft.LOGGER.info("触发执行快捷键: F5");
                menuBar.executeCurrentGraph();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_F5) {
                NodeCraft.LOGGER.info("触发停止执行快捷键: Shift+F5");
                menuBar.stopExecution();
                return true;
            }
        }

        if (isCtrlPressed) {
            if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
                NodeCraft.LOGGER.info("触发放大快捷键: Ctrl++");
                canvas.zoomIn();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
                NodeCraft.LOGGER.info("触发缩小快捷键: Ctrl+-");
                canvas.zoomOut();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_0 || keyCode == GLFW.GLFW_KEY_KP_0) {
                NodeCraft.LOGGER.info("触发重置视图快捷键: Ctrl+0");
                canvas.resetCanvasView();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_HOME) {
                NodeCraft.LOGGER.info("触发适应视图快捷键: Ctrl+Home");
                canvas.fitToContent();
                return true;
            }
        }

        return false;
    }

    private boolean handleGhostCameraBlockSelection(int button) {
        if (!ghostCameraManager.isEnabled()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        BlockHitResult blockHit = (BlockHitResult) client.crosshairTarget;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && parentScreen.getCurrentEditor() != null) {
            NodeCraft.LOGGER.info("选中方块: {}", blockHit.getBlockPos());
            return true;
        }

        return false;
    }
}
