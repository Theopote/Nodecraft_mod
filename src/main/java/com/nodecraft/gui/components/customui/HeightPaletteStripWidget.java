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

    private static final float LABEL_GAP = 4.0f;
    private static final float DEFAULT_STRIP_HEIGHT = 96.0f;

    private HeightPaletteStripWidget() {
    }

    public static float preferredWidth() {
        return 36.0f;
    }

    /** Strip body height only (excludes the bins label below). */
    public static float preferredStripHeight(float minHeight) {
        return Math.max(minHeight, DEFAULT_STRIP_HEIGHT);
    }

    /** Full widget height: strip + gap + bins label. */
    public static float preferredHeight(float minHeight) {
        return preferredStripHeight(minHeight)
            + LABEL_GAP
            + CustomUIMetricsSafe.layoutTextLineHeight();
    }

    public static void render(@Nullable BlockPaletteData palette, float widthPixels, float heightPixels, float zoom) {
        if (palette == null || palette.isEmpty()) {
            ImGui.textDisabled("No palette");
            return;
        }

        float labelGap = CustomUIMetricsSafe.scaleLogical(LABEL_GAP, zoom);
        float labelHeight = ImGui.getTextLineHeight();
        float stripHeight = Math.max(8.0f, heightPixels - labelGap - labelHeight);

        ImVec2 start = ImGui.getCursorScreenPos();
        float x0 = start.x;
        float y0 = start.y;
        float x1 = x0 + widthPixels;
        float y1 = y0 + stripHeight;

        ImGui.invisibleButton("##height_palette_strip", widthPixels, stripHeight);
        ImDrawList drawList = ImGui.getWindowDrawList();

        int count = palette.size();
        float binHeight = stripHeight / Math.max(1, count);
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
        ImGui.setCursorScreenPos(x0, y1 + labelGap);
        ImGui.text(String.format("%d bins", count));
    }
}
