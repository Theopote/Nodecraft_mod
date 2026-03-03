package com.nodecraft.mixin;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.screens.NodecraftScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "shouldCloseOnEsc", at = @At("HEAD"), cancellable = true)
    private void onShouldCloseOnEsc(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof NodecraftScreen screen) {
            NodeCraft.LOGGER.info("NodecraftScreen处理ESC键，设置为关闭窗口");
            
            screen.closeRequested = true;
            
            MinecraftClient.getInstance().execute(() -> {
                screen.cleanup();
                MinecraftClient.getInstance().setScreen(null);
            });
            
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "shouldPause", at = @At("HEAD"), cancellable = true)
    private void onShouldPause(CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof NodecraftScreen) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
    
    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void onClose(CallbackInfo ci) {
        if ((Object) this instanceof NodecraftScreen screen) {
            NodeCraft.LOGGER.info("Screen.close()方法被调用，确保Nodecraft资源正确清理");

            screen.cleanup();
            
            MinecraftClient.getInstance().setScreen(null);
            
            ci.cancel();
        }
    }
    
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyInput keyInput, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof NodecraftScreen screen) {
            int keyCode = keyInput.key();
            int scanCode = keyInput.scancode();

            if (keyCode == GLFW.GLFW_KEY_ESCAPE || scanCode == GLFW.GLFW_KEY_ESCAPE) {
                NodeCraft.LOGGER.info("ESC键被按下，关闭Nodecraft窗口");
                
                screen.cleanup();
                MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(null));
                
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }
} 