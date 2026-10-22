package com.nodecraft.gui.components.customui;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves approximate RGBA swatch colors for block ids (client registry when available).
 */
public final class BlockSwatchHelper {

    private static final int FALLBACK_RGBA = 0xFF888888;

    private BlockSwatchHelper() {
    }

    public static int rgbaForBlockId(@Nullable String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return FALLBACK_RGBA;
        }
        try {
            Identifier id = Identifier.tryParse(blockId.trim().toLowerCase());
            if (id == null || !Registries.BLOCK.containsId(id)) {
                return FALLBACK_RGBA;
            }
            Block block = Registries.BLOCK.get(id);
            BlockState state = block.getDefaultState();
            var client = MinecraftClient.getInstance();
            var world = client != null ? client.world : null;
            if (world != null) {
                int mapColor = state.getMapColor(world, BlockPos.ORIGIN).color;
                return 0xFF000000 | (mapColor & 0x00FFFFFF);
            }
        } catch (Throwable ignored) {
            // Headless / pre-bootstrap: fall back to neutral gray.
        }
        return FALLBACK_RGBA;
    }

    public static void drawSwatch(imgui.ImDrawList drawList, float x, float y, float size, @Nullable String blockId) {
        int rgba = rgbaForBlockId(blockId);
        int border = 0xFF404040;
        drawList.addRectFilled(x, y, x + size, y + size, rgba);
        drawList.addRect(x, y, x + size, y + size, border, 0.0f, 0, 1.0f);
    }
}
