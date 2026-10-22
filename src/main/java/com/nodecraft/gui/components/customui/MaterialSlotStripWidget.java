package com.nodecraft.gui.components.customui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import org.jetbrains.annotations.Nullable;

/**
 * Compact three-slot material preview strip (TOP / SIDE / BOTTOM).
 */
public final class MaterialSlotStripWidget {

    private static final float SWATCH_SIZE = 28.0f;
    private static final float SLOT_GAP = 8.0f;
    private static final float LABEL_GAP = 2.0f;

    public record Slot(String roleLabel, @Nullable String blockId, @Nullable String statusHint) {
        public static Slot missing(String roleLabel) {
            return new Slot(roleLabel, null, "Not connected");
        }

        public static Slot invalid(String roleLabel, String error) {
            return new Slot(roleLabel, null, error == null || error.isBlank() ? "Invalid" : error);
        }

        public static Slot known(String roleLabel, String blockId) {
            return new Slot(roleLabel, blockId, null);
        }
    }

    private MaterialSlotStripWidget() {
    }

    public static float preferredHeight() {
        float line = CustomUIMetricsSafe.layoutTextLineHeight();
        return SWATCH_SIZE + LABEL_GAP + line + LABEL_GAP + line + CustomUIMetricsSafe.FALLBACK_ITEM_SPACING_Y;
    }

    public static float preferredWidth() {
        return 3.0f * SWATCH_SIZE + 2.0f * SLOT_GAP + 48.0f;
    }

    public static void render(Slot top, Slot side, Slot bottom, float widthPixels, float zoom) {
        float swatch = CustomUIMetricsSafe.scaleLogical(SWATCH_SIZE, zoom);
        float gap = CustomUIMetricsSafe.scaleLogical(SLOT_GAP, zoom);
        float labelGap = CustomUIMetricsSafe.scaleLogical(LABEL_GAP, zoom);
        float slotWidth = Math.max(swatch, (widthPixels - 2.0f * gap) / 3.0f);

        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();

        renderSlot(drawList, origin.x, origin.y, slotWidth, swatch, labelGap, zoom, top);
        renderSlot(drawList, origin.x + slotWidth + gap, origin.y, slotWidth, swatch, labelGap, zoom, side);
        renderSlot(drawList, origin.x + 2.0f * (slotWidth + gap), origin.y, slotWidth, swatch, labelGap, zoom, bottom);

        float totalHeight = preferredHeight() * Math.max(0.001f, zoom);
        ImGui.setCursorScreenPos(origin.x, origin.y + totalHeight);
        ImGui.dummy(widthPixels, 0.1f);
    }

    private static void renderSlot(
            ImDrawList drawList,
            float x,
            float y,
            float slotWidth,
            float swatch,
            float labelGap,
            float zoom,
            Slot slot
    ) {
        float swatchX = x + Math.max(0.0f, (slotWidth - swatch) * 0.5f);
        BlockSwatchHelper.drawSwatch(drawList, swatchX, y, swatch, slot.blockId());

        float textY = y + swatch + labelGap;
        ImGui.setCursorScreenPos(x, textY);
        ImGui.pushTextWrapPos(x + slotWidth);
        ImGui.textDisabled(slot.roleLabel());
        String summary = summarizeBlockId(slot.blockId());
        if (summary != null) {
            ImGui.setCursorScreenPos(x, ImGui.getCursorScreenPos().y);
            ImGui.text(summary);
        } else {
            ImGui.setCursorScreenPos(x, ImGui.getCursorScreenPos().y);
            ImGui.textDisabled(slot.statusHint() == null ? "—" : truncate(slot.statusHint(), 18));
        }
        ImGui.popTextWrapPos();
    }

    private static @Nullable String summarizeBlockId(@Nullable String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        String id = blockId.trim();
        int colon = id.indexOf(':');
        String name = colon >= 0 && colon + 1 < id.length() ? id.substring(colon + 1) : id;
        return truncate(name, 14);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, Math.max(1, max - 1)) + "…";
    }
}
