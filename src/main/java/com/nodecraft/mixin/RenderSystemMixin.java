package com.nodecraft.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nodecraft.gui.editor.integration.ImGuiRenderer;
import com.nodecraft.gui.screens.NodecraftScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.tracy.TracyFrameCapturer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class RenderSystemMixin {

    @Inject(method = "flipFrame", at = @At("HEAD"))
    private static void nodecraft$beforeFlipFrame(Window window, TracyFrameCapturer capturer, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !(client.currentScreen instanceof NodecraftScreen)) {
            return;
        }

        ImGuiRenderer renderer = ImGuiRenderer.getInstance();
        if (renderer.hasPendingDrawData()) {
            renderer.renderPendingDrawData();
        }
    }
}
