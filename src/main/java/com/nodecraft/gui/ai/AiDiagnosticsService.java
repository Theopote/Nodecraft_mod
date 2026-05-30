package com.nodecraft.gui.ai;

import com.nodecraft.gui.components.ai.AiAssistantComponent;
import com.nodecraft.gui.components.ai.AiAssistantComponent.RemotePlannerSnapshot;

public final class AiDiagnosticsService {

    private AiDiagnosticsService() {
    }

    public static boolean hasAiDebugData(AiAssistantComponent component) {
        if (component == null) {
            return false;
        }
        RemotePlannerSnapshot snapshot = component.getRemotePlannerSnapshot();
        return hasText(snapshot.rawResponse())
                || hasText(snapshot.modelText())
                || hasText(snapshot.requestSnapshot())
                || hasText(snapshot.errorMessage());
    }

    public static String buildAiDiagnosticsExportText(
            AiAssistantComponent component,
            String statusMessage,
            boolean includeFullPayloads
    ) {
        if (component == null) {
            return "[AI Debug Diagnostics]\n(empty)\n";
        }

        RemotePlannerSnapshot snapshot = component.getRemotePlannerSnapshot();

        return "[AI Debug Diagnostics]\n" +
            "category: " + nullToEmpty(snapshot.errorCategory()) + "\n" +
            "statusCode: " + snapshot.statusCode() + "\n" +
            "attempts: " + snapshot.attempts() + "\n" +
            "errorMessage: " + nullToEmpty(snapshot.errorMessage()) + "\n" +
                "statusMessage: " + nullToEmpty(statusMessage) + "\n\n" +
                "[Request Snapshot]\n" +
            formatDiagnosticsSection(snapshot.requestSnapshot(), includeFullPayloads) + "\n" +
                "[Model Text]\n" +
            formatDiagnosticsSection(snapshot.modelText(), includeFullPayloads) + "\n" +
                "[Raw Response]\n" +
            formatDiagnosticsSection(snapshot.rawResponse(), includeFullPayloads) + "\n";
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
