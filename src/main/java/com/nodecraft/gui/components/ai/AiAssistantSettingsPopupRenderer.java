package com.nodecraft.gui.components.ai;

import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import com.nodecraft.gui.ai.AiSettingsStore;

import java.nio.file.Path;

final class AiAssistantSettingsPopupRenderer {

    private AiAssistantSettingsPopupRenderer() {
    }

    record State(
            ImBoolean enableRemotePlanner,
            ImString apiBaseUrl,
            ImString apiKey,
            ImString model,
            ImInt providerPresetIndex,
            String[] providerPresetLabels,
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
        void onProviderPresetSelected(int index);

        void onValidateLocal();

        void onTestRemoteConnection();

        void onSaveSettings();

        void onReloadSettings();
    }

    static void renderSettingsPopup(State state, Actions actions) {
        int flags = 0;
        if (!ImGui.beginPopupModal("AI Settings", flags)) {
            return;
        }

        float availableWidth = Math.max(260.0f, ImGui.getContentRegionAvailX());
        float wideFieldWidth = Math.max(260.0f, Math.min(520.0f, availableWidth));
        float mediumFieldWidth = Math.max(200.0f, Math.min(320.0f, availableWidth));
        boolean compactActions = availableWidth < 560.0f;

        ImGui.text("Remote Planner Connection");
        ImGui.separator();

        ImGui.checkbox("Enable remote planner", state.enableRemotePlanner());

        if (state.providerPresetLabels() != null && state.providerPresetLabels().length > 0) {
            ImGui.text("Provider Preset");
            ImGui.pushItemWidth(mediumFieldWidth);
            if (ImGui.combo("##ai_provider_preset", state.providerPresetIndex(), state.providerPresetLabels())) {
                int index = Math.max(0, Math.min(state.providerPresetLabels().length - 1, state.providerPresetIndex().get()));
                actions.onProviderPresetSelected(index);
            }
            ImGui.popItemWidth();
        }

        ImGui.text("API Base URL");
        ImGui.pushItemWidth(wideFieldWidth);
        ImGui.inputText("##ai_api_base_url", state.apiBaseUrl());
        ImGui.popItemWidth();

        ImGui.text("API Key");
        int keyFlags = state.showApiKey().get() ? ImGuiInputTextFlags.None : ImGuiInputTextFlags.Password;
        ImGui.pushItemWidth(wideFieldWidth);
        ImGui.inputText("##ai_api_key", state.apiKey(), keyFlags);
        ImGui.popItemWidth();
        ImGui.checkbox("Show API key", state.showApiKey());
        ImGui.textDisabled("Leave empty to use env: " + AiSettingsStore.API_KEY_ENV
                + ", " + AiSettingsStore.OPENAI_API_KEY_ENV
                + ", or " + AiSettingsStore.ANTHROPIC_API_KEY_ENV + ".");
        if (state.apiKey().get() != null && !state.apiKey().get().isBlank()) {
            ImGui.textColored(0.95f, 0.72f, 0.22f, 1.0f,
                    "Saved API keys are stored as plain text in this config file.");
        }

        ImGui.text("Model");
        ImGui.pushItemWidth(mediumFieldWidth);
        ImGui.inputText("##ai_model", state.model());
        ImGui.popItemWidth();

        if (state.detectedProviderLabel() != null && !state.detectedProviderLabel().isBlank()) {
            ImGui.textDisabled("Detected provider: " + state.detectedProviderLabel());
        }

        if (state.suggestedModels() != null && state.suggestedModels().length > 0) {
            int selectedModelIndex = indexOfModel(state.suggestedModels(), state.model().get());
            ImInt modelIndex = new ImInt(Math.max(0, selectedModelIndex));

            ImGui.text("Suggested Models");
            ImGui.pushItemWidth(mediumFieldWidth);
            if (ImGui.combo("##ai_suggested_model_combo", modelIndex, state.suggestedModels())) {
                int index = Math.max(0, Math.min(state.suggestedModels().length - 1, modelIndex.get()));
                state.model().set(state.suggestedModels()[index]);
            }
            ImGui.popItemWidth();
        }

        ImGui.text("Request Timeout (seconds)");
        ImGui.pushItemWidth(120.0f);
        if (ImGui.inputInt("##ai_timeout_seconds", state.requestTimeoutSeconds())) {
            state.requestTimeoutSeconds().set(Math.max(5, Math.min(600, state.requestTimeoutSeconds().get())));
        }
        ImGui.popItemWidth();

        ImGui.checkbox("Auto layout before apply", state.autoLayoutBeforeApply());

        if (ImGui.treeNode("Advanced request options")) {
            ImGui.text("Provider Strategy");
            ImGui.pushItemWidth(mediumFieldWidth);
            ImGui.combo("##ai_provider_strategy", state.providerStrategyIndex(), new String[]{"AUTO", "OPENAI_COMPAT", "ANTHROPIC"});
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

            ImGui.text("System Prompt");
            ImGui.pushItemWidth(wideFieldWidth);
            ImGui.inputTextMultiline("##ai_system_prompt", state.systemPrompt(), wideFieldWidth, 100.0f);
            ImGui.popItemWidth();
            ImGui.treePop();
        }

        ImGui.separator();
        if (ImGui.button("Validate (Local)")) {
            actions.onValidateLocal();
        }
        if (!compactActions) ImGui.sameLine();
        if (ImGui.button("Test Remote API")) {
            actions.onTestRemoteConnection();
        }
        if (!compactActions) ImGui.sameLine();
        if (ImGui.button("Save Settings")) {
            actions.onSaveSettings();
        }
        if (!compactActions) ImGui.sameLine();
        if (ImGui.button("Reload Settings")) {
            actions.onReloadSettings();
        }
        if (!compactActions) ImGui.sameLine();
        if (ImGui.button("Close")) {
            ImGui.closeCurrentPopup();
        }

        if (ImGui.treeNode("Storage")) {
            ImGui.textDisabled("Config file: " + state.settingsPath().toAbsolutePath());
            ImGui.textDisabled("Settings are persisted to disk and loaded on startup.");
            ImGui.treePop();
        }

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
