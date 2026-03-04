package com.nodecraft.mixin;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.screens.NodecraftScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "stop", at = @At("HEAD"))
    private void onStop(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient)(Object)this;
        Screen currentScreen = client.currentScreen;
        
        // 如果当前屏幕是NodecraftScreen，在Minecraft关闭前关闭它
        if (currentScreen instanceof NodecraftScreen) {
            NodeCraft.LOGGER.info("Minecraft正在关闭，自动关闭NodeCraft窗口");
            ((NodecraftScreen) currentScreen).cleanup();
        }
    }
    
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        // 如果正在切换到新屏幕，检查是否需要关闭NodecraftScreen
        MinecraftClient client = (MinecraftClient)(Object)this;
        Screen currentScreen = client.currentScreen;
        
        if (currentScreen instanceof NodecraftScreen && screen != null && screen != currentScreen) {
            // 当从NodecraftScreen切换到其他屏幕时，确保它被正确关闭
            NodeCraft.LOGGER.info("从NodeCraft切换到其他屏幕，确保NodeCraft资源释放");
            ((NodecraftScreen) currentScreen).cleanup();
        }
    }
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient)(Object)this;
        Screen currentScreen = client.currentScreen;
        
        // 如果当前屏幕是NodecraftScreen，检查closeRequested标志
        if (currentScreen instanceof NodecraftScreen screen) {

            // 如果closeRequested标志被设置，立即关闭窗口
            if (screen.closeRequested) {
                NodeCraft.LOGGER.info("检测到closeRequested标志，立即关闭Nodecraft窗口");
                
                // 执行资源清理并关闭窗口
                screen.cleanup();
                
                // 使用非常直接的方式设置屏幕为null
                client.execute(() -> client.setScreen(null));
                
                // 避免此标志被再次处理
                screen.closeRequested = false;
            }
        }
    }
    
    // 拦截分辨率变化事件，更新Nodecraft窗口
    @Inject(method = "onResolutionChanged", at = @At("RETURN"))
    private void onResolutionChanged(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient)(Object)this;
        Screen currentScreen = client.currentScreen;
        
        // 如果当前屏幕是NodecraftScreen，确保窗口尺寸和位置更新
        if (currentScreen instanceof NodecraftScreen) {
            NodeCraft.LOGGER.info("检测到分辨率变化，重新初始化Nodecraft窗口");
            ((NodecraftScreen) currentScreen).publicClearAndInit();
        }
    }
    
    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    private void onHandleInputEvents(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient)(Object)this;
        Screen currentScreen = client.currentScreen;
        
        // 如果当前屏幕是NodecraftScreen，根据鼠标位置决定输入处理
        if (currentScreen instanceof NodecraftScreen screen) {

            // 检查ESC键是否被按下 - ESC键始终关闭nodecraft界面
            if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
                NodeCraft.LOGGER.info("ESC键被按下，关闭Nodecraft窗口");
                screen.closeRequested = true;
                screen.cleanup();
                client.execute(() -> client.setScreen(null));
                ci.cancel();
                return;
            }
            
            // 检查鼠标是否在nodecraft窗口范围内
            boolean mouseInWindow = screen.isMouseOverNodecraftGui(
                    client.mouse.getX(), client.mouse.getY());
            
            if (mouseInWindow) {
                // 鼠标在nodecraft窗口内：阻止游戏输入事件处理
                ci.cancel();
            }
            // 鼠标在窗口外：不拦截，让Minecraft正常处理输入事件（WASD移动等）
        }
    }
} 