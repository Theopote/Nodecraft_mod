package com.nodecraft.gui.components.ai;

import com.nodecraft.gui.ai.AiCredentialPolicy;
import com.nodecraft.gui.ai.AiSettingsStore;
import com.nodecraft.gui.layout.ImGuiChildScope;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.nio.file.Path;

final class AiAssistantSettingsPopupRenderer {

    /** Fixed modal size — layout is designed around this canvas. */
    private static final float WINDOW_WIDTH = 540.0f;
    private static final float WINDOW_HEIGHT = 600.0f;

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
            ImBoolean rememberApiKey,
            ImBoolean autoLayoutBeforeApply,
            ImBoolean enterToSend,
            ImBoolean debugLoggingEnabled,
            ImBoolean includePromptPreviewInDebug,
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
        ImGui.setNextWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT, ImGuiCond.Appearing);
        ImGui.setNextWindowSizeConstraints(WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_WIDTH, WINDOW_HEIGHT);

        if (!ImGui.beginPopupModal("AI Settings", ImGuiWindowFlags.NoResize)) {
            return;
        }

        // Footer = separator + one button row; keep it tight to the window bottom padding.
        float footerReserve = ImGui.getFrameHeightWithSpacing()
                + ImGui.getStyle().getItemSpacingY()
                + 1.0f;
        float bodyHeight = Math.max(120.0f, ImGui.getContentRegionAvailY() - footerReserve);

        // AlwaysVerticalScrollbar keeps ContentRegionAvailX stable (no mid-frame reflow clip).
        try (ImGuiChildScope body = new ImGuiChildScope(
                "aiSettingsScrollBody",
                0.0f,
                bodyHeight,
                false,
                ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
            if (body.isOpen()) {
                // Inside the scroll child: avail width already excludes the scrollbar; gutter adds a small gap.
                float fieldWidth = Math.max(160.0f, AiUiHelper.usableContentWidth());
                renderConnectionSection(state, actions, fieldWidth);
                ImGui.spacing();
                renderBehaviorSection(state, fieldWidth);
                ImGui.spacing();
                renderAdvancedSection(state, fieldWidth);
                ImGui.spacing();
                renderStorageSection(state);
            }
        }

        ImGui.separator();
        renderFooterActions(actions);

        ImGui.endPopup();
    }

    private static void renderConnectionSection(State state, Actions actions, float fieldWidth) {
        sectionHeader("Remote Planner");

        ImGui.checkbox("Enable remote planner", state.enableRemotePlanner());

        if (state.providerPresetLabels() != null && state.providerPresetLabels().length > 0) {
            labeledField("Provider preset");
            ImGui.setNextItemWidth(fieldWidth);
            if (ImGui.combo("##ai_provider_preset", state.providerPresetIndex(), state.providerPresetLabels())) {
                int index = clampIndex(state.providerPresetIndex().get(), state.providerPresetLabels().length);
                actions.onProviderPresetSelected(index);
            }
        }

        labeledField("API base URL");
        ImGui.setNextItemWidth(fieldWidth);
        ImGui.inputText("##ai_api_base_url", state.apiBaseUrl());
        AiUiHelper.textWrappedDisabled(AiCredentialPolicy.primaryPathSummary());

        labeledField("API key");
        int keyFlags = state.showApiKey().get() ? ImGuiInputTextFlags.None : ImGuiInputTextFlags.Password;
        ImGui.setNextItemWidth(fieldWidth);
        ImGui.inputText("##ai_api_key", state.apiKey(), keyFlags);
        ImGui.checkbox("Show API key", state.showApiKey());

        AiUiHelper.textWrappedDisabled(
                "Prefer a local proxy with an empty key, or set "
                        + AiSettingsStore.API_KEY_ENV + " / "
                        + AiSettingsStore.OPENAI_API_KEY_ENV + " / "
                        + AiSettingsStore.ANTHROPIC_API_KEY_ENV + ".");

        if (state.rememberApiKey().get() && state.apiKey().get() != null && !state.apiKey().get().isBlank()) {
            AiUiHelper.textWrappedColored(0.95f, 0.72f, 0.22f, 1.0f, AiCredentialPolicy.diskPersistWarning());
        } else if (AiCredentialPolicy.looksLikeLocalProxy(state.apiBaseUrl().get())
                && (state.apiKey().get() == null || state.apiKey().get().isBlank())) {
            AiUiHelper.textWrappedDisabled("Local proxy mode: provider key stays out of this Minecraft process.");
        } else {
            AiUiHelper.textWrappedDisabled("By default, pasted API keys stay in memory for this session only.");
        }

        labeledField("Model");
        ImGui.setNextItemWidth(fieldWidth);
        ImGui.inputText("##ai_model", state.model());

        if (state.detectedProviderLabel() != null && !state.detectedProviderLabel().isBlank()) {
            AiUiHelper.textWrappedDisabled("Detected provider: " + state.detectedProviderLabel());
        }

        if (state.suggestedModels() != null && state.suggestedModels().length > 0) {
            int selectedModelIndex = indexOfModel(state.suggestedModels(), state.model().get());
            ImInt modelIndex = new ImInt(Math.max(0, selectedModelIndex));

            labeledField("Suggested models");
            ImGui.setNextItemWidth(fieldWidth);
            if (ImGui.combo("##ai_suggested_model_combo", modelIndex, state.suggestedModels())) {
                int index = clampIndex(modelIndex.get(), state.suggestedModels().length);
                state.model().set(state.suggestedModels()[index]);
            }
        }
    }

    private static void renderBehaviorSection(State state, float fieldWidth) {
        sectionHeader("Editor Behavior");

        labeledField("Request timeout (seconds)");
        ImGui.setNextItemWidth(Math.min(160.0f, fieldWidth));
        if (ImGui.inputInt("##ai_timeout_seconds", state.requestTimeoutSeconds())) {
            state.requestTimeoutSeconds().set(Math.max(5, Math.min(600, state.requestTimeoutSeconds().get())));
        }

        ImGui.checkbox("Auto layout before apply", state.autoLayoutBeforeApply());
        ImGui.checkbox("Enter sends message", state.enterToSend());
    }

    private static void renderAdvancedSection(State state, float fieldWidth) {
        if (!ImGui.collapsingHeader("Advanced")) {
            return;
        }

        labeledField("Provider strategy");
        ImGui.setNextItemWidth(fieldWidth);
        ImGui.combo("##ai_provider_strategy", state.providerStrategyIndex(),
                new String[]{"AUTO", "OPENAI_COMPAT", "ANTHROPIC"});

        labeledField("Max output tokens");
        ImGui.setNextItemWidth(Math.min(160.0f, fieldWidth));
        if (ImGui.inputInt("##ai_max_output_tokens", state.maxOutputTokens())) {
            state.maxOutputTokens().set(Math.max(512, Math.min(4096, state.maxOutputTokens().get())));
        }

        labeledField("Conversation history turns");
        ImGui.setNextItemWidth(Math.min(160.0f, fieldWidth));
        if (ImGui.inputInt("##ai_conversation_history_turns", state.conversationHistoryTurns())) {
            state.conversationHistoryTurns().set(Math.max(1, Math.min(20, state.conversationHistoryTurns().get())));
        }

        labeledField("System prompt");
        ImGui.setNextItemWidth(fieldWidth);
        ImGui.inputTextMultiline("##ai_system_prompt", state.systemPrompt(), fieldWidth, 110.0f);

        ImGui.checkbox("Remember API key on disk (dev only)", state.rememberApiKey());
        ImGui.checkbox("Enable AI debug logging", state.debugLoggingEnabled());
        if (!state.debugLoggingEnabled().get()) {
            ImGui.beginDisabled();
        }
        ImGui.checkbox("Include prompt preview in debug data", state.includePromptPreviewInDebug());
        if (!state.debugLoggingEnabled().get()) {
            ImGui.endDisabled();
        }
    }

    private static void renderStorageSection(State state) {
        if (!ImGui.collapsingHeader("Storage")) {
            return;
        }
        AiUiHelper.textWrappedDisabled("Config file: " + state.settingsPath().toAbsolutePath());
        AiUiHelper.textWrappedDisabled("Settings are persisted to disk and loaded on startup.");
        AiUiHelper.textWrappedDisabled("API keys are excluded unless Remember API key on disk is enabled.");
        AiUiHelper.textWrappedDisabled(AiCredentialPolicy.primaryPathSummary());
    }

    private static void renderFooterActions(Actions actions) {
        String[] labels = {
                "Check Settings",
                "Test Remote API",
                "Save Settings",
                "Reload Settings",
                "Close"
        };
        float rowRight = AiUiHelper.contentRowRightEdge();
        for (int i = 0; i < labels.length; i++) {
            float width = ImGui.calcTextSize(labels[i]).x + ImGui.getStyle().getFramePaddingX() * 2.0f;
            if (i > 0) {
                AiUiHelper.sameLineIfFitsInRow(rowRight, width);
            }
            boolean clicked = ImGui.button(labels[i]);
            if (!clicked) {
                continue;
            }
            switch (i) {
                case 0 -> actions.onValidateLocal();
                case 1 -> actions.onTestRemoteConnection();
                case 2 -> actions.onSaveSettings();
                case 3 -> actions.onReloadSettings();
                case 4 -> ImGui.closeCurrentPopup();
                default -> {
                }
            }
        }
    }

    private static void sectionHeader(String title) {
        ImGui.spacing();
        ImGui.text(title);
        ImGui.separator();
    }

    private static void labeledField(String label) {
        ImGui.spacing();
        ImGui.text(label);
    }

    private static int clampIndex(int index, int length) {
        if (length <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(length - 1, index));
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
