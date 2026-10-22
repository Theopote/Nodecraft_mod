package com.nodecraft.gui.components.customui;

import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPaletteEntry;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import org.jetbrains.annotations.Nullable;

/**
 * Vertical height-bin palette strip (low → high).
 */
public final class HeightPaletteStripWidget {

    private HeightPaletteStripWidget() {
    }

    public static float preferredWidth() {
        return 36.0f;
    }

    public static float preferredHeight(float minHeight) {
        return Math.max(minHeight, 96.0f);
    }

    public static void render(@Nullable BlockPaletteData palette, float widthPixels, float heightPixels, float zoom) {
        if (palette == null || palette.isEmpty()) {
            ImGui.textDisabled("No palette");
            return;
        }

        ImVec2 start = ImGui.getCursorScreenPos();
        float x0 = start.x;
        float y0 = start.y;
        float x1 = x0 + widthPixels;
        float y1 = y0 + heightPixels;

        ImGui.invisibleButton("##height_palette_strip", widthPixels, heightPixels);
        ImDrawList drawList = ImGui.getWindowDrawList();

        int count = palette.size();
        float binHeight = heightPixels / Math.max(1, count);
        float borderWidth = Math.max(1.0f, CustomUIMetricsSafe.scaleLogical(1.0f, zoom));
        for (int i = 0; i < count; i++) {
            BlockPaletteEntry entry = palette.entries().get(i);
            float by0 = y1 - (i + 1) * binHeight;
            float by1 = y1 - i * binHeight;
            int rgba = BlockSwatchHelper.rgbaForBlockId(entry.blockId());
            drawList.addRectFilled(x0, by0, x1, by1, rgba);
            drawList.addRect(x0, by0, x1, by1, 0xFF303030, 0.0f, 0, borderWidth);
        }

        drawList.addRect(x0, y0, x1, y1, 0xFF505050, 0.0f, 0, borderWidth);
        ImGui.setCursorScreenPos(x0, y1 + CustomUIMetricsSafe.scaleLogical(4.0f, zoom));
        ImGui.text(String.format("%d bins", count));
    }
}
