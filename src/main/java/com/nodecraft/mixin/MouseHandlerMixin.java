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
 * 1. 鼠标在Nodecraft窗口内 => 正常的UI交互，无视角移动
 * 2. 鼠标在窗口外 + 中键未按下 => 可以自由移动鼠标，无视角移动
 * 3. 鼠标在窗口外 + 中键按下 => 锁定鼠标，可以移动视角
 */
@Mixin(Mouse.class)
public class MouseHandlerMixin {
    @Shadow
    @Final
    private MinecraftClient client;
    
    @Unique
    private boolean isMiddleMouseButtonPressed = false;
    
    @Unique
    private double lastMouseX = 0.0;
    
    @Unique
    private double lastMouseY = 0.0;
    
    @Unique
    private boolean hasInitialPosition = false;

    /**
     * 处理鼠标移动事件。
     * 只有当鼠标在窗口外且中键按下时，才允许改变视角。
     */
    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void onCursorPos(long window, double x, double y, CallbackInfo ci) {
        if (this.client.currentScreen instanceof NodecraftScreen screen) {
            
            boolean isMouseOverGui = screen.isMouseOverNodecraftGui(x, y);
            
            if (isMouseOverGui) {
                // 鼠标在窗口内 => 处理UI交互
                if (this.client.mouse.isCursorLocked()) {
                    this.client.mouse.unlockCursor();
                }
                hasInitialPosition = false; // 重置初始位置标志
                ci.cancel(); // 阻止视角移动
            } else {
                // 鼠标在窗口外
                if (isMiddleMouseButtonPressed) {
                    // 按住中键 => 允许改变视角
                    if (!this.client.mouse.isCursorLocked()) {
                        this.client.mouse.lockCursor();
                    }
                    
                    // 直接更新视角
                    if (hasInitialPosition) {
                        double deltaX = x - lastMouseX;
                        double deltaY = y - lastMouseY;
                        updateCameraRotation(deltaX, deltaY);
                    } else {
                        // 第一次移动，记录初始位置
                        hasInitialPosition = true;
                    }
                    
                    lastMouseX = x;
                    lastMouseY = y;
                    
                    ci.cancel(); // 取消事件，因为我们已经手动处理了视角更新
                } else {
                    // 中键未按下 => 禁止视角移动
                    if (this.client.mouse.isCursorLocked()) {
                        this.client.mouse.unlockCursor();
                    }
                    hasInitialPosition = false; // 重置初始位置标志
                    ci.cancel(); // 阻止视角移动
                }
            }
        }
    }
    
    /**
     * 直接更新相机视角
     */
    @Unique
    private void updateCameraRotation(double deltaX, double deltaY) {
        if (this.client.player == null) {
            return;
        }
        
        try {
            // 获取鼠标灵敏度设置
            double sensitivity = this.client.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
            double mouseSensitivity = sensitivity * sensitivity * sensitivity * 8.0;
            
            // 计算视角变化
            float yawChange = (float) (deltaX * mouseSensitivity);
            float pitchChange = (float) (deltaY * mouseSensitivity);
            
            // 获取当前视角
            float currentYaw = this.client.player.getYaw();
            float currentPitch = this.client.player.getPitch();
            
            // 应用视角变化
            float newYaw = currentYaw + yawChange;
            float newPitch = Math.max(-90.0f, Math.min(90.0f, currentPitch + pitchChange));
            
            // 设置新的视角
            this.client.player.setYaw(newYaw);
            this.client.player.setPitch(newPitch);
                
        } catch (Exception e) {
            NodeCraft.LOGGER.error("更新视角时出错", e);
        }
    }
    
    /**
     * 监听鼠标按钮事件，跟踪中键状态。
     */
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, MouseInput mouseInput, int action, CallbackInfo ci) {
        if (this.client.currentScreen instanceof NodecraftScreen screen) {
            int button = mouseInput.button();
            
            // 中键处理（用于控制视角）
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                boolean isMouseOverGui = screen.isMouseOverNodecraftGui(
                        this.client.mouse.getX(), 
                        this.client.mouse.getY());
                
                if (action == GLFW.GLFW_PRESS) {
                    isMiddleMouseButtonPressed = true;
                    hasInitialPosition = false; // 重置初始位置标志
                    
                    // 如果按下时鼠标在窗口外，立即锁定光标
                    if (!isMouseOverGui && !this.client.mouse.isCursorLocked()) {
                        this.client.mouse.lockCursor();
                    }
                } else if (action == GLFW.GLFW_RELEASE) {
                    isMiddleMouseButtonPressed = false;
                    hasInitialPosition = false; // 重置初始位置标志
                    
                    // 释放中键时解锁光标
                    if (this.client.mouse.isCursorLocked()) {
                        this.client.mouse.unlockCursor();
                    }
                }
                
                // 只有在鼠标在GUI上时才拦截中键事件
                if (isMouseOverGui) {
                    ci.cancel(); // 拦截中键事件，防止游戏使用
                }
                return;
            }
            
            // 对于其他按钮，只拦截鼠标在GUI上的事件
            boolean isMouseOverGui = screen.isMouseOverNodecraftGui(
                    this.client.mouse.getX(), 
                    this.client.mouse.getY());
            
            if (isMouseOverGui) {
                ci.cancel();
            }
        }
    }
    
    /**
     * 监听鼠标滚轮事件。
     */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (this.client.currentScreen instanceof NodecraftScreen screen) {
            // 只拦截鼠标在GUI上的滚轮事件
            boolean isMouseOverGui = screen.isMouseOverNodecraftGui(
                    this.client.mouse.getX(), 
                    this.client.mouse.getY());
            
            if (isMouseOverGui) {
                ci.cancel();
            }
        }
    }
} 