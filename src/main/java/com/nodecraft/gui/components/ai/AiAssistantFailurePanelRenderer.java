package com.nodecraft.gui.components.ai;

import imgui.ImGui;

final class AiAssistantFailurePanelRenderer {

    private AiAssistantFailurePanelRenderer() {
    }

    record State(
            String errorCategory,
            int statusCode,
            int attempts,
            String errorMessage,
            boolean hasLastPrompt,
            boolean remotePlannerBusy,
            boolean remotePlannerEnabled
    ) {
    }

    interface Actions {
        void retryLastRequest();

        void increaseTimeoutSeconds(int deltaSeconds);

        void togglePlannerMode();

        void openAiSettingsPopup();

        void resaveSettings();
    }

    static void renderFailureSummaryCard(State state, Actions actions) {
        if (state == null) {
            ImGui.textDisabled("No remote failure summary available.");
            return;
        }

        String category = state.errorCategory();
        if (category == null || category.isBlank() || "none".equals(category)) {
            ImGui.textDisabled("No remote failure summary available.");
            return;
        }

        ImGui.text("Failure Summary");
        ImGui.separator();
        ImGui.textWrapped("Category: " + category
                + " | Status: " + state.statusCode()
                + " | Attempts: " + state.attempts());
        if (state.errorMessage() != null && !state.errorMessage().isBlank()) {
            ImGui.textWrapped("Message: " + state.errorMessage());
        }

        String suggestion = buildRemoteFailureSuggestion(category);
        if (!suggestion.isBlank()) {
            ImGui.textWrapped("Suggested action: " + suggestion);
        }

        ImGui.spacing();
        renderFailureRecoveryActions(state, actions);
    }

    private static void renderFailureRecoveryActions(State state, Actions actions) {
        String category = state.errorCategory();

        boolean allowTimeoutAction = "timeout".equals(category)
                || "server".equals(category)
                || "rate-limit".equals(category)
                || "network".equals(category)
                || "request".equals(category)
                || "response-format".equals(category);
        boolean allowSettingsAction = "auth".equals(category)
                || "request".equals(category)
                || "timeout".equals(category)
                || "response-format".equals(category);
        boolean allowModeAction = "network".equals(category)
                || "auth".equals(category)
                || "request".equals(category)
                || "server".equals(category);

        if (state.hasLastPrompt() && !state.remotePlannerBusy()) {
            if (ImGui.smallButton("Retry Last Request")) {
                actions.retryLastRequest();
            }
            ImGui.sameLine();
        }

        if (allowTimeoutAction) {
            if (ImGui.smallButton("Increase Timeout +30s")) {
                actions.increaseTimeoutSeconds(30);
            }
            ImGui.sameLine();
        }

        if (allowModeAction) {
            if (ImGui.smallButton(state.remotePlannerEnabled() ? "Use Local Planner" : "Use Remote Planner")) {
                actions.togglePlannerMode();
            }
            ImGui.sameLine();
        }

        if (allowSettingsAction) {
            if (ImGui.smallButton("Open AI Settings")) {
                actions.openAiSettingsPopup();
            }
            ImGui.sameLine();
        }

        if (ImGui.smallButton("Resave Settings")) {
            actions.resaveSettings();
        }
    }

    private static String buildRemoteFailureSuggestion(String category) {
        return switch (category) {
            case "auth" -> "Verify API key, organization/project scope, and endpoint authorization policy.";
            case "rate-limit" -> "Reduce request frequency, switch to a lower-cost model, or increase provider quota.";
            case "timeout" -> "Increase timeout in AI settings and reduce prompt/schema size for this request.";
            case "network" -> "Check connectivity, proxy/firewall rules, and whether the base URL is reachable.";
            case "server" -> "Retry later or switch provider/model temporarily if 5xx errors persist.";
            case "request" -> "Check model name, endpoint path, and payload constraints for this provider.";
            case "response-format" -> "Inspect raw response and tighten system prompt to force strict JSON output.";
            case "canceled" -> "No action required; resend request when ready.";
            default -> "Open raw response and request snapshot for manual diagnosis.";
        };
    }
}
