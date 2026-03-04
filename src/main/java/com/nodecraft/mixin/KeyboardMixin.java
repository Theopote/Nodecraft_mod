package com.nodecraft.mixin;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.screens.NodecraftScreen;
import imgui.ImGui;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KeyboardMixin
 * 在 Keyboard 类级别拦截键盘事件。
 * 当鼠标在 NodeCraft UI 上方时，阻止 Minecraft 处理键盘输入。
 * ImGui 通过 GLFW 回调直接接收输入，不需要手动转发。
 */
@Mixin(Keyboard.class)
public class KeyboardMixin {

    /**
     * 拦截键盘按键事件。
     * 当鼠标在 UI 上时，阻止 Minecraft 处理键盘事件（ImGui 已通过 GLFW 回调接收）。
     * 当鼠标在 UI 外时，允许 Minecraft 正常处理键盘事件。
     */
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int scanCode, KeyInput input, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.currentScreen instanceof NodecraftScreen screen) {
            // 检查鼠标是否在 UI 上
            boolean mouseOverGui = screen.isMouseOverNodecraftGui(client.mouse.getX(), client.mouse.getY());

            if (mouseOverGui) {
                // 鼠标在 UI 上：检查 ImGui 是否想要捕获键盘
                boolean wantCapture = false;
                try {
                    wantCapture = ImGui.getIO() != null && ImGui.getIO().getWantCaptureKeyboard();
                } catch (Exception ignored) {}

                if (wantCapture) {
                    // ImGui 想要捕获键盘，阻止 Minecraft 处理
                    // ImGui 已经通过 GLFW 回调接收到了输入
                    ci.cancel();
                    return;
                }

                // ImGui 不想捕获键盘（例如没有激活的输入框），
                // 但鼠标在 UI 上，仍然拦截大部分按键避免误操作
                // 只允许 ESC 键通过（用于关闭界面）
                int keyCode = input.key();
                if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                    ci.cancel();
                }
            }
            // 鼠标在 UI 外：不拦截，让 Minecraft 正常处理按键
        }
    }

    /**
     * 拦截字符输入事件。
     * 当鼠标在 UI 上且 ImGui 想要捕获键盘时，阻止 Minecraft 处理。
     */
    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void onChar(long window, CharInput input, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.currentScreen instanceof NodecraftScreen screen) {
            boolean mouseOverGui = screen.isMouseOverNodecraftGui(client.mouse.getX(), client.mouse.getY());

            if (mouseOverGui) {
                // 鼠标在 UI 上：阻止字符输入传递给 Minecraft
                ci.cancel();
            }
        }
    }
}
