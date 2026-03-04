package com.nodecraft.mixin;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.screens.NodecraftScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MouseHandlerMixin
 * 实现以下交互模式：
 * 1. 鼠标在Nodecraft窗口内 => 正常的UI交互，阻止Minecraft处理鼠标事件
 * 2. 鼠标在窗口外 + 中键未按下 => 可以自由移动鼠标，无视角移动
 * 3. 鼠标在窗口外 + 中键按下 => 使用 changeLookDirection 移动视角
 */
@Mixin(Mouse.class)
public class MouseHandlerMixin {
    @Shadow @Final private MinecraftClient client;
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;

    @Unique
    private boolean nodecraft$isDragging = false;

    @Unique
    private double nodecraft$lastX = 0.0;

    @Unique
    private double nodecraft$lastY = 0.0;

    /** 视角灵敏度倍率 */
    @Unique
    private static final double LOOK_SENSITIVITY = 2.5;

    /**
     * 判断鼠标是否在 Nodecraft UI 区域内（使用 GLFW 像素坐标）
     */
    @Unique
    private boolean isHoveringUI(NodecraftScreen screen) {
        if (client == null || client.getWindow() == null) return false;
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(client.getWindow().getHandle(), x, y);
        return screen.isMouseOverNodecraftGui(x[0], y[0]);
    }

    /**
     * 处理鼠标移动事件。
     * 在 @At("TAIL") 阶段，cursorDelta 已经由原版计算完毕。
     * 只有当鼠标在窗口外且中键按下时，才通过 changeLookDirection 移动视角。
     */
    @Inject(method = "onCursorPos", at = @At("TAIL"))
    private void onCursorPos(long window, double x, double y, CallbackInfo ci) {
        if (!(client.currentScreen instanceof NodecraftScreen screen)) return;

        boolean hover = isHoveringUI(screen);

        // 计算位移
        double dx = x - nodecraft$lastX;
        double dy = y - nodecraft$lastY;
        nodecraft$lastX = x;
        nodecraft$lastY = y;

        // 鼠标在 UI 外 + 中键拖拽 => 移动视角
        if (!hover && nodecraft$isDragging && client.player != null) {
            double baseSensitivity = client.options.getMouseSensitivity().getValue();
            double scale = baseSensitivity * LOOK_SENSITIVITY;
            client.player.changeLookDirection(dx * scale, dy * scale);
        }
    }

    /**
     * 监听鼠标按钮事件，跟踪中键状态并拦截 UI 区域内的事件。
     */
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseInput mouseInput, int action, CallbackInfo ci) {
        if (!(client.currentScreen instanceof NodecraftScreen screen)) return;

        int button = mouseInput.button();

        // 中键：切换视角拖拽模式
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            nodecraft$isDragging = (action == GLFW.GLFW_PRESS);
            return; // 中键不拦截，让 ImGui 也能接收
        }

        // 其他按钮：鼠标在 UI 上时拦截，防止穿透到 Minecraft
        if (isHoveringUI(screen)) {
            ci.cancel();
        }
    }

    /**
     * 监听鼠标滚轮事件。
     * 鼠标在 UI 上时拦截滚轮事件。
     */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!(client.currentScreen instanceof NodecraftScreen screen)) return;

        if (isHoveringUI(screen)) {
            ci.cancel();
        }
    }
} 