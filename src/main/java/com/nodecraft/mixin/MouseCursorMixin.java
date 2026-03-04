package com.nodecraft.mixin;

import com.nodecraft.gui.screens.NodecraftScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 鼠标光标Mixin
 * 当NodecraftScreen打开时：
 * - 完全阻止原版 lockCursor，让GLFW光标保持NORMAL
 * - 确保ImGui可以完全接管鼠标输入
 */
@Mixin(Mouse.class)
public class MouseCursorMixin {
    
    @Shadow @Final private MinecraftClient client;

    /**
     * 完全阻止原版鼠标锁定。
     * 强制设置光标为NORMAL模式，使鼠标可以自由移动并与UI交互。
     */
    @Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
    private void onLockCursor(CallbackInfo ci) {
        if (client.currentScreen instanceof NodecraftScreen) {
            long window = client.getWindow().getHandle();
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            ci.cancel();
        }
    }

    /**
     * 确保鼠标解锁时光标处于正常状态。
     */
    @Inject(method = "unlockCursor", at = @At("HEAD"))
    private void onUnlockCursor(CallbackInfo ci) {
        if (client.currentScreen instanceof NodecraftScreen) {
            long window = client.getWindow().getHandle();
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }
} 