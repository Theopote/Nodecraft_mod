package com.nodecraft.mixin;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.screens.NodecraftScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;

import java.nio.DoubleBuffer;
import org.lwjgl.BufferUtils;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onMovementTick(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.currentScreen instanceof NodecraftScreen screen) {
            // 使用GLFW直接获取鼠标位置
            DoubleBuffer xBuffer = BufferUtils.createDoubleBuffer(1);
            DoubleBuffer yBuffer = BufferUtils.createDoubleBuffer(1);
            GLFW.glfwGetCursorPos(client.getWindow().getHandle(), xBuffer, yBuffer);
            double mouseX = xBuffer.get(0);
            double mouseY = yBuffer.get(0);
            
            // 检查鼠标是否在nodecraft窗口范围内
            boolean mouseInWindow = screen.isMouseOverNodecraftGui(mouseX, mouseY);
            
            if (mouseInWindow) {
                // 鼠标在nodecraft窗口内：禁用所有移动输入
                this.playerInput = new PlayerInput(false, false, false, false, false, false, false);
                
                ci.cancel(); // 阻止原版处理
            }
        }
        // 如果不在NodecraftScreen中，让原版处理
    }
} 