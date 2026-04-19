package com.nodecraft.mixin;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.screens.NodecraftScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "shouldPause", at = @At("HEAD"), cancellable = true)
    private void onShouldPause(CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof NodecraftScreen) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
    
    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void onClose(CallbackInfo ci) {
        if ((Object) this instanceof NodecraftScreen) {
            NodeCraft.LOGGER.info("Screen.close()方法被调用，确保Nodecraft资源正确清理");
            MinecraftClient.getInstance().setScreen(null);
            ci.cancel();
        }
    }
}
