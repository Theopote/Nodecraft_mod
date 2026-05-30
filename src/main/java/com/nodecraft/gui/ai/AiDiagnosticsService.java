package com.nodecraft.gui.ai;

import com.nodecraft.gui.components.ai.AiAssistantComponent;

public final class AiDiagnosticsService {

    private AiDiagnosticsService() {
    }

    public static boolean hasAiDebugData(AiAssistantComponent component) {
        if (component == null) {
            return false;
        }
        return hasText(component.getLastRemoteRawResponse())
                || hasText(component.getLastRemoteModelText())
                || hasText(component.getLastRemoteRequestSnapshot())
                || hasText(component.getLastRemoteErrorMessage());
    }

    public static String buildAiDiagnosticsExportText(
            AiAssistantComponent component,
            String statusMessage,
            boolean includeFullPayloads
    ) {
        if (component == null) {
            return "[AI Debug Diagnostics]\n(empty)\n";
        }

        return "[AI Debug Diagnostics]\n" +
                "category: " + nullToEmpty(component.getLastRemoteErrorCategory()) + "\n" +
                "statusCode: " + component.getLastRemoteStatusCode() + "\n" +
                "attempts: " + component.getLastRemoteAttempts() + "\n" +
                "errorMessage: " + nullToEmpty(component.getLastRemoteErrorMessage()) + "\n" +
                "statusMessage: " + nullToEmpty(statusMessage) + "\n\n" +
                "[Request Snapshot]\n" +
                formatDiagnosticsSection(component.getLastRemoteRequestSnapshot(), includeFullPayloads) + "\n" +
                "[Model Text]\n" +
                formatDiagnosticsSection(component.getLastRemoteModelText(), includeFullPayloads) + "\n" +
                "[Raw Response]\n" +
                formatDiagnosticsSection(component.getLastRemoteRawResponse(), includeFullPayloads) + "\n";
    }

    public static String formatDiagnosticsSection(String value, boolean includeFullPayloads) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        if (includeFullPayloads) {
            return value;
        }
        return truncateForDiagnostics(value, 900);
    }

    public static String truncateForDiagnostics(String value, int maxChars) {
        if (value == null) {
            return "(empty)";
        }
        String normalized = value.trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...[truncated]";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
