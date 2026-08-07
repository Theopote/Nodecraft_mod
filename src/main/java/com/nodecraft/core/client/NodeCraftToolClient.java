package com.nodecraft.core.client;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.screens.NodecraftScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Client-only helpers for {@link com.nodecraft.core.item.NodeCraftToolItem}.
 * Kept out of the common item class so dedicated servers / GameTest can load items.
 */
@Environment(EnvType.CLIENT)
public final class NodeCraftToolClient {

    private NodeCraftToolClient() {
    }

    public static void openEditor() {
        try {
            MinecraftClient.getInstance().setScreen(new NodecraftScreen());
        } catch (NoClassDefFoundError e) {
            NodeCraft.LOGGER.error("NodeCraft 编辑器依赖类缺失: {}", e.getMessage());
            showError("NodeCraft 编辑器依赖缺失: " + e.getMessage());
        } catch (Exception e) {
            NodeCraft.LOGGER.error("无法创建 NodeCraft 编辑器界面: {}", e.getMessage(), e);
            showError("无法打开 NodeCraft 编辑器: " + e.getMessage());
        }
    }

    private static void showError(String message) {
        try {
            MinecraftClient.getInstance().setScreen(new ErrorScreen(
                Text.literal("NodeCraft 错误"),
                Text.literal(message)
            ));
        } catch (Exception e) {
            NodeCraft.LOGGER.error("无法显示错误屏幕: {}", e.getMessage());
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(
                    Text.literal("NodeCraft 错误: " + message),
                    false
                );
            }
        }
    }

    private static final class ErrorScreen extends Screen {
        private final Text errorMessage;

        private ErrorScreen(Text title, Text errorMessage) {
            super(title);
            this.errorMessage = errorMessage;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);

            context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.title,
                this.width / 2,
                this.height / 2 - 20,
                0xFFFFFF
            );
            context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.errorMessage,
                this.width / 2,
                this.height / 2,
                0xFF5555
            );
            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("按 ESC 键返回"),
                this.width / 2,
                this.height / 2 + 40,
                0xAAAAAA
            );

            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}
