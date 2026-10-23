package com.nodecraft.gui.components.customui;

import imgui.ImGui;
import imgui.type.ImBoolean;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Compact Preview Placements status panel: enabled toggle + Raw/Unique/Shown counts.
 * Counts must come from node outputs — never recompute PlacementSubmitPlan here.
 */
public final class PreviewStatusPanelWidget {

    public record Counts(int raw, int unique, int shown, boolean sampled, @Nullable String error) {
        public static Counts empty() {
            return new Counts(0, 0, 0, false, null);
        }
    }

    private PreviewStatusPanelWidget() {
    }

    public static float preferredHeight() {
        float line = CustomUIMetricsSafe.layoutTextLineHeight();
        float frame = CustomUIMetricsSafe.layoutFrameHeight();
        float gap = CustomUIMetricsSafe.layoutItemSpacingY();
        // checkbox + counts (one row) + status + error line (always reserved so Valid→error does not clip)
        return frame + gap + line + gap + line + gap + line;
    }

    /**
     * @return true when previewEnabled was toggled
     */
    public static boolean render(
            boolean previewEnabled,
            Counts counts,
            BooleanSupplier isEditable,
            Consumer<Boolean> onEnabledChanged
    ) {
        boolean changed = false;
        if (isEditable.getAsBoolean()) {
            ImBoolean enabled = new ImBoolean(previewEnabled);
            if (ImGui.checkbox("Enabled##preview_enabled", enabled)) {
                onEnabledChanged.accept(enabled.get());
                changed = true;
            }
        } else {
            ImGui.text(previewEnabled ? "Enabled" : "Disabled");
        }

        ImGui.text(formatCount(counts.raw()));
        ImGui.sameLine();
        ImGui.textDisabled("Raw");
        ImGui.sameLine(0.0f, 16.0f);
        ImGui.text(formatCount(counts.unique()));
        ImGui.sameLine();
        ImGui.textDisabled("Unique");
        ImGui.sameLine(0.0f, 16.0f);
        ImGui.text(formatCount(counts.shown()));
        ImGui.sameLine();
        ImGui.textDisabled("Shown");

        String status = counts.sampled() ? "GHOST preview · Sampled" : "GHOST preview";
        ImGui.textDisabled(status);

        String error = counts.error();
        if (error != null && !error.isBlank()) {
            ImGui.textColored(0.9f, 0.35f, 0.3f, 1.0f, truncate(error, 48));
        }
        return changed;
    }

    private static String formatCount(int value) {
        return String.format("%,d", Math.max(0, value));
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(1, max - 1)) + "…";
    }
}
