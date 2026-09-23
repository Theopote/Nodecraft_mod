package com.nodecraft.mixin;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.screens.NodecraftScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "stop", at = @At("HEAD"))
    private void onStop(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        Screen currentScreen = client.currentScreen;

        if (currentScreen instanceof NodecraftScreen screen) {
            NodeCraft.LOGGER.info("Minecraft is stopping, requesting NodeCraft close");
            screen.requestClose();
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        Screen currentScreen = client.currentScreen;

        if (currentScreen instanceof NodecraftScreen nodecraftScreen
            && screen != null
            && screen != currentScreen) {
            NodeCraft.LOGGER.info("Switching away from NodeCraft, requesting close");
            nodecraftScreen.requestClose();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        Screen currentScreen = client.currentScreen;

        if (currentScreen instanceof NodecraftScreen screen && screen.consumeCloseRequest()) {
            NodeCraft.LOGGER.info("Consuming NodeCraft close request");
            client.execute(() -> client.setScreen(null));
        }
    }

    @Inject(method = "onResolutionChanged", at = @At("RETURN"))
    private void onResolutionChanged(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        Screen currentScreen = client.currentScreen;

        if (currentScreen instanceof NodecraftScreen screen) {
            NodeCraft.LOGGER.info("Resolution changed, rebuilding NodeCraft window");
            // 推迟到下一 tick，避免在 ImGui 帧渲染中途重建导致 BeginChild/EndChild 栈失衡
            client.execute(screen::publicClearAndInit);
        }
    }

    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    private void onHandleInputEvents(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        Screen currentScreen = client.currentScreen;

        if (currentScreen instanceof NodecraftScreen screen) {
            boolean mouseInWindow = screen.isMouseOverNodecraftGui(
                client.mouse.getX(),
                client.mouse.getY()
            );

            if (mouseInWindow) {
                ci.cancel();
            }
        }
    }
}
