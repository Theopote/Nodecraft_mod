package com.nodecraft.mixin;

import com.nodecraft.gui.screens.NodecraftScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.input.Input;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;

/**
 * KeyboardInputMixin
 * 处理WASD等移动键，当鼠标在UI外时允许玩家通过直接轮询GLFW按键状态来移动。
 * 当鼠标在UI内时禁用所有移动输入。
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onMovementTick(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.currentScreen instanceof NodecraftScreen screen) {
            // 直接检查鼠标是否在UI上
            boolean mouseOverGui = screen.isMouseOverNodecraftGui(client.mouse.getX(), client.mouse.getY());

            if (!mouseOverGui) {
                // 鼠标在UI外：通过直接轮询GLFW按键状态允许移动
                var window = client.getWindow();

                boolean forward = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_W);
                boolean backward = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_S);
                boolean left = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_A);
                boolean right = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_D);
                boolean jump = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_SPACE);
                boolean sneak = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT);
                boolean sprint = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL);

                // 更新 PlayerInput
                this.playerInput = new PlayerInput(forward, backward, left, right, jump, sneak, sprint);

                // 计算移动向量
                float forwardAmount = getMovementMultiplier(forward, backward);
                float sidewaysAmount = getMovementMultiplier(left, right);

                // 潜行时减速
                if (sneak) {
                    forwardAmount *= 0.3F;
                    sidewaysAmount *= 0.3F;
                }

                this.movementVector = new Vec2f(sidewaysAmount, forwardAmount);

                ci.cancel(); // 阻止原版处理
            } else {
                // 鼠标在UI内：禁用所有移动输入
                this.playerInput = new PlayerInput(false, false, false, false, false, false, false);
                this.movementVector = Vec2f.ZERO;
                ci.cancel();
            }
        }
        // 如果不在NodecraftScreen中，让原版处理
    }

    @Unique
    private static float getMovementMultiplier(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        }
        return positive ? 1.0F : -1.0F;
    }
} 