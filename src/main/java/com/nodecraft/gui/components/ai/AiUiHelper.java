package com.nodecraft.gui.components.ai;

import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.Locale;

final class AiUiHelper {

    /** Extra gap so content never sits under the vertical scrollbar thumb. */
    static final float SCROLLBAR_GUTTER = 6.0f;

    private AiUiHelper() {
    }

    /** Usable width inside the current window/child, excluding a scrollbar gutter. */
    static float usableContentWidth() {
        return Math.max(1.0f, ImGui.getContentRegionAvailX() - SCROLLBAR_GUTTER);
    }

    /** Wrap subsequent {@code textUnformatted}/{@code text} to the usable panel width. */
    static void pushPanelTextWrap() {
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + usableContentWidth());
    }

    static void popPanelTextWrap() {
        ImGui.popTextWrapPos();
    }

    static void textWrapped(String text) {
        pushPanelTextWrap();
        ImGui.textUnformatted(text == null ? "" : text);
        popPanelTextWrap();
    }

    static void textWrappedColored(float r, float g, float b, float a, String text) {
        pushPanelTextWrap();
        ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, a);
        ImGui.textUnformatted(text == null ? "" : text);
        ImGui.popStyleColor();
        popPanelTextWrap();
    }

    static void textWrappedDisabled(String text) {
        textWrappedColored(0.55f, 0.55f, 0.55f, 1.0f, text);
    }

    /**
     * Places {@code widgetWidth} on the current line when it fits; otherwise wraps to the next line.
     * Prevents buttons from being drawn under the right scrollbar.
     */
    static void sameLineIfFits(float widgetWidth) {
        float spacing = ImGui.getStyle().getItemSpacingX();
        if (ImGui.getContentRegionAvailX() >= widgetWidth + spacing + SCROLLBAR_GUTTER) {
            ImGui.sameLine();
        }
    }

    static void renderStatusMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        StatusTone tone = getStatusTone(message);
        switch (tone) {
            case ERROR -> textWrappedColored(0.96f, 0.35f, 0.35f, 1.0f, "[Error] " + message);
            case WARN -> textWrappedColored(0.95f, 0.74f, 0.30f, 1.0f, "[Warn] " + message);
            case SUCCESS -> textWrappedColored(0.45f, 0.82f, 0.54f, 1.0f, "[OK] " + message);
            default -> textWrapped(message);
        }
    }

    static StatusTone getStatusTone(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "failed", "error", "invalid", "exception", "aborted")) {
            return StatusTone.ERROR;
        }
        if (containsAny(lower, "warn", "retry", "canceled", "unavailable", "busy")) {
            return StatusTone.WARN;
        }
        if (containsAny(lower, "saved", "loaded", "validated", "completed", "successful", "submitted", "applied")) {
            return StatusTone.SUCCESS;
        }
        return StatusTone.INFO;
    }

    private static boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank() || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    enum StatusTone {
        INFO,
        SUCCESS,
        WARN,
        ERROR
    }
}
