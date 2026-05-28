package com.nodecraft.gui.components.ai;

import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.nio.file.Path;

final class AiAssistantSettingsPopupRenderer {

    private AiAssistantSettingsPopupRenderer() {
    }

    record State(
            ImBoolean enableRemotePlanner,
            ImString apiBaseUrl,
            ImString apiKey,
            ImString model,
            String detectedProviderLabel,
            String[] suggestedModels,
            ImInt providerStrategyIndex,
            ImString systemPrompt,
            ImInt maxOutputTokens,
            ImInt requestTimeoutSeconds,
            ImInt conversationHistoryTurns,
            ImBoolean showApiKey,
            ImBoolean autoLayoutBeforeApply,
            Path settingsPath
    ) {
    }

    interface Actions {
        void onValidateLocal();

        void onTestRemoteConnection();

        void onSaveSettings();

        void onReloadSettings();
    }

    static void renderSettingsPopup(State state, Actions actions) {
        int flags = ImGuiWindowFlags.AlwaysAutoResize;
        if (!ImGui.beginPopupModal("AI Settings", flags)) {
            return;
        }

        ImGui.text("Remote Planner Connection");
        ImGui.separator();

        ImGui.checkbox("Enable remote planner", state.enableRemotePlanner());

        ImGui.text("API Base URL");
        ImGui.pushItemWidth(420.0f);
        ImGui.inputText("##ai_api_base_url", state.apiBaseUrl());
        ImGui.popItemWidth();

        ImGui.text("API Key");
        int keyFlags = state.showApiKey().get() ? ImGuiInputTextFlags.None : ImGuiInputTextFlags.Password;
        ImGui.pushItemWidth(420.0f);
        ImGui.inputText("##ai_api_key", state.apiKey(), keyFlags);
        ImGui.popItemWidth();
        ImGui.checkbox("Show API key", state.showApiKey());

        ImGui.text("Model");
        ImGui.pushItemWidth(240.0f);
        ImGui.inputText("##ai_model", state.model());
        ImGui.popItemWidth();

        if (state.detectedProviderLabel() != null && !state.detectedProviderLabel().isBlank()) {
            ImGui.textDisabled("Detected provider: " + state.detectedProviderLabel());
        }

        if (state.suggestedModels() != null && state.suggestedModels().length > 0) {
            int selectedModelIndex = indexOfModel(state.suggestedModels(), state.model().get());
            ImInt modelIndex = new ImInt(Math.max(0, selectedModelIndex));

            ImGui.text("Suggested Models");
            ImGui.pushItemWidth(300.0f);
            if (ImGui.combo("##ai_suggested_model_combo", modelIndex, state.suggestedModels())) {
                int index = Math.max(0, Math.min(state.suggestedModels().length - 1, modelIndex.get()));
                state.model().set(state.suggestedModels()[index]);
            }
            ImGui.popItemWidth();
        }

        ImGui.text("Provider Strategy");
        ImGui.pushItemWidth(240.0f);
        ImGui.combo("##ai_provider_strategy", state.providerStrategyIndex(), new String[]{"AUTO", "OPENAI_COMPAT", "ANTHROPIC"});
        ImGui.popItemWidth();

        ImGui.text("Request Timeout (seconds)");
        ImGui.pushItemWidth(120.0f);
        if (ImGui.inputInt("##ai_timeout_seconds", state.requestTimeoutSeconds())) {
            state.requestTimeoutSeconds().set(Math.max(5, Math.min(600, state.requestTimeoutSeconds().get())));
        }
        ImGui.popItemWidth();

        ImGui.text("Max Output Tokens");
        ImGui.pushItemWidth(120.0f);
        if (ImGui.inputInt("##ai_max_output_tokens", state.maxOutputTokens())) {
            state.maxOutputTokens().set(Math.max(512, Math.min(4096, state.maxOutputTokens().get())));
        }
        ImGui.popItemWidth();

        ImGui.text("Conversation History Turns");
        ImGui.pushItemWidth(120.0f);
        if (ImGui.inputInt("##ai_conversation_history_turns", state.conversationHistoryTurns())) {
            state.conversationHistoryTurns().set(Math.max(1, Math.min(20, state.conversationHistoryTurns().get())));
        }
        ImGui.popItemWidth();

        ImGui.checkbox("Auto layout before apply", state.autoLayoutBeforeApply());

        ImGui.text("System Prompt");
        ImGui.pushItemWidth(420.0f);
        ImGui.inputTextMultiline("##ai_system_prompt", state.systemPrompt(), 420.0f, 100.0f);
        ImGui.popItemWidth();

        ImGui.separator();
        if (ImGui.button("Validate (Local)")) {
            actions.onValidateLocal();
        }
        ImGui.sameLine();
        if (ImGui.button("Test Remote API")) {
            actions.onTestRemoteConnection();
        }
        ImGui.sameLine();
        if (ImGui.button("Save Settings")) {
            actions.onSaveSettings();
        }
        ImGui.sameLine();
        if (ImGui.button("Reload Settings")) {
            actions.onReloadSettings();
        }
        ImGui.sameLine();
        if (ImGui.button("Close")) {
            ImGui.closeCurrentPopup();
        }

        ImGui.textDisabled("Config file: " + state.settingsPath().toAbsolutePath());
        ImGui.textDisabled("Settings are persisted to disk and loaded on startup.");

        ImGui.endPopup();
    }

    private static int indexOfModel(String[] options, String model) {
        if (options == null || options.length == 0 || model == null || model.isBlank()) {
            return 0;
        }
        for (int i = 0; i < options.length; i++) {
            if (model.equalsIgnoreCase(options[i])) {
                return i;
            }
        }
        return 0;
    }
}
