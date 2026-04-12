package com.nodecraft.nodesystem.interaction;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.minecraft.client.MinecraftClientController;
import net.minecraft.client.MinecraftClient;

/**
 * 节点编辑器视图模式：鼠标锁定、NodeCraft 模式切换、编辑内中键视角拖拽。
 * <p>
 * 与具体拾取/交互状态解耦，由 {@link NodeEditorInteractionManager} 在进出编辑模式时调用。
 */
public final class EditorModeCameraService {

    private static final float CAMERA_SENSITIVITY = 0.3f;

    private boolean isInEditorMode = false;
    private boolean middleMousePressed = false;
    private float lastMouseX = 0;
    private float lastMouseY = 0;

    public EditorModeCameraService() {
    }

    public boolean isInEditorMode() {
        return isInEditorMode;
    }

    /**
     * 进入编辑模式：解锁鼠标并激活 NodeCraft 客户端模式。
     */
    public void enterEditorMode() {
        if (isInEditorMode) {
            NodeCraft.LOGGER.debug("已经在编辑模式中，跳过重复进入");
            return;
        }

        isInEditorMode = true;

        try {
            MinecraftClient client = MinecraftClient.getInstance();

            if (client.mouse != null) {
                client.mouse.unlockCursor();
            }

            MinecraftClientController controller = MinecraftClientController.getInstance();
            controller.activateNodeCraftMode();

            NodeCraft.LOGGER.info("进入编辑模式 - 鼠标已解锁，准星已隐藏");

        } catch (Exception e) {
            NodeCraft.LOGGER.error("进入编辑模式时出错", e);
            isInEditorMode = false;
        }
    }

    /**
     * 退出编辑模式：锁定鼠标并停用 NodeCraft 模式（不清理拾取/高亮，由编排方处理）。
     */
    public void exitEditorMode() {
        if (!isInEditorMode) {
            NodeCraft.LOGGER.debug("不在编辑模式中，跳过退出");
            return;
        }

        isInEditorMode = false;

        try {
            resetMiddleMouseCameraState();

            MinecraftClient client = MinecraftClient.getInstance();

            if (client.mouse != null) {
                client.mouse.lockCursor();
            }

            MinecraftClientController controller = MinecraftClientController.getInstance();
            controller.deactivateNodeCraftMode();

            NodeCraft.LOGGER.info("退出编辑模式 - 鼠标已锁定，准星已恢复");

        } catch (Exception e) {
            NodeCraft.LOGGER.error("退出编辑模式时出错", e);
        }
    }

    /**
     * 当鼠标离开世界交互区域（如 ImGui 遮挡）时应释放中键拖拽状态。
     */
    public void updateMiddleMouseCamera(float mouseX, float mouseY, boolean isMiddleMouseDown, boolean allowMiddleMouseDrag) {
        if (!allowMiddleMouseDrag) {
            if (middleMousePressed) {
                middleMousePressed = false;
            }
            return;
        }

        try {
            if (isMiddleMouseDown && !middleMousePressed) {
                middleMousePressed = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                NodeCraft.LOGGER.debug("开始视角控制 - 鼠标位置: ({}, {})", mouseX, mouseY);
            } else if (!isMiddleMouseDown && middleMousePressed) {
                middleMousePressed = false;
                NodeCraft.LOGGER.debug("结束视角控制");
            } else if (isMiddleMouseDown) {
                float deltaX = mouseX - lastMouseX;
                float deltaY = mouseY - lastMouseY;

                if (Math.abs(deltaX) > 0.5f || Math.abs(deltaY) > 0.5f) {
                    applyCameraDelta(deltaX, deltaY);
                    lastMouseX = mouseX;
                    lastMouseY = mouseY;

                    if (NodeCraft.LOGGER.isDebugEnabled()) {
                        NodeCraft.LOGGER.debug("更新视角 - 偏移: ({}, {})", deltaX, deltaY);
                    }
                }
            }
        } catch (Exception e) {
            NodeCraft.LOGGER.error("处理中键视角控制时出错", e);
        }
    }

    public boolean isMiddleMouseDragging() {
        return middleMousePressed;
    }

    public void resetMiddleMouseCameraState() {
        middleMousePressed = false;
        lastMouseX = 0;
        lastMouseY = 0;
    }

    private void applyCameraDelta(float deltaX, float deltaY) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                return;
            }

            float yawChange = deltaX * CAMERA_SENSITIVITY;
            float pitchChange = deltaY * CAMERA_SENSITIVITY;

            float currentYaw = client.player.getYaw();
            float currentPitch = client.player.getPitch();

            float newYaw = currentYaw + yawChange;
            float newPitch = Math.max(-90.0f, Math.min(90.0f, currentPitch + pitchChange));

            client.player.setYaw(newYaw);
            client.player.setPitch(newPitch);

        } catch (Exception e) {
            NodeCraft.LOGGER.error("更新相机视角时出错", e);
        }
    }
}
