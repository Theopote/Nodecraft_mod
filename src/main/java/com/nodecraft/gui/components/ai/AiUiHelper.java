package com.nodecraft.gui.components.ai;

import imgui.ImGui;

import java.util.Locale;

final class AiUiHelper {

    private AiUiHelper() {
    }

    static void renderStatusMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        StatusTone tone = getStatusTone(message);
        switch (tone) {
            case ERROR -> ImGui.textColored(0.96f, 0.35f, 0.35f, 1.0f, "[Error] " + message);
            case WARN -> ImGui.textColored(0.95f, 0.74f, 0.30f, 1.0f, "[Warn] " + message);
            case SUCCESS -> ImGui.textColored(0.45f, 0.82f, 0.54f, 1.0f, "[OK] " + message);
            default -> ImGui.textWrapped(message);
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