package com.nodecraft.gui.components.ai;

import com.nodecraft.gui.components.ai.AiAssistantComponent.AiChatMessage;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.List;

final class AiAssistantMainPanelRenderer {

    private AiAssistantMainPanelRenderer() {
    }

    record State(
            String settingsSummary,
            String settingsStatusMessage,
            boolean hasDebugData,
            boolean busy,
            ImBoolean useSelectionContext,
            ImBoolean includeGraphContext,
            ImBoolean previewOnlyMode,
            ImBoolean patchApplyMode,
            ImBoolean patchRemoveScopedConnections,
            ImBoolean enterToSend,
            String inputLanguageDetected,
            String normalizedIntentPreview,
            String selectedNodeDisplayName,
            String selectedNodeTypeId,
            List<AiChatMessage> chatMessages,
            ImString promptInput,
            boolean remotePlannerEnabled,
            int lastRenderedChatCount
    ) {
    }

    interface Actions {
        void openSettingsPopup();

        void openDebugConsolePopup();

        void cancelRequest();

        void onQuickPrompt(String text);

        void renderPlanPreviewSection();

        void onSubmitPrompt();
    }

    static int renderMainPanel(State state, Actions actions) {
        renderHeader(state, actions);
        renderBusyStatus(state, actions);
        renderModeOptions(state);
        renderSelectionContext(state);
        renderQuickPrompts(state, actions);

        actions.renderPlanPreviewSection();

        int chatCount = renderChatHistory(state);
        renderPromptInput(state, actions);
        renderLanguageDiagnostics(state);
        renderModeHint(state);
        return chatCount;
    }

    private static void renderHeader(State state, Actions actions) {
        ImGui.textWrapped("Describe what you want to build (any language), and AI will generate a node graph plan.");
        if (ImGui.smallButton("AI Settings")) {
            actions.openSettingsPopup();
        }
        ImGui.sameLine();
        ImGui.textDisabled(state.settingsSummary());

        if (state.settingsStatusMessage() != null && !state.settingsStatusMessage().isBlank()) {
            renderStatusMessage(state.settingsStatusMessage());
        }

        if (state.hasDebugData() && ImGui.smallButton("Open Debug Console")) {
            actions.openDebugConsolePopup();
        }
    }

    private static void renderBusyStatus(State state, Actions actions) {
        if (!state.busy()) {
            return;
        }
        ImGui.textColored(0.95f, 0.78f, 0.30f, 1.0f, "AI is generating plan...");
        ImGui.sameLine();
        if (ImGui.smallButton("Cancel")) {
            actions.cancelRequest();
        }
    }

    private static void renderModeOptions(State state) {
        ImGui.checkbox("Use current selection as context", state.useSelectionContext());
        ImGui.checkbox("Include current canvas graph summary", state.includeGraphContext());
        ImGui.checkbox("Preview-only mode (do not mutate graph)", state.previewOnlyMode());
        ImGui.checkbox("Patch apply mode (reuse matching nodes)", state.patchApplyMode());
        ImGui.checkbox("Press Enter to send", state.enterToSend());
        if (!state.patchApplyMode().get()) {
            return;
        }

        ImGui.checkbox("Patch remove scoped stale connections", state.patchRemoveScopedConnections());
        ImGui.textColored(0.95f, 0.72f, 0.22f, 1.0f,
                "Warning: reused-node parameter updates may not be undoable.");
        ImGui.sameLine();
        ImGui.textDisabled("(?)");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Patch mode can update state on matched existing nodes directly.\n"
                    + "Graph edits are undoable, but some parameter/state updates may require manual revert.");
        }
    }

    private static void renderSelectionContext(State state) {
        if (!state.useSelectionContext().get()) {
            return;
        }

        ImGui.separator();
        if (state.selectedNodeDisplayName() != null && !state.selectedNodeDisplayName().isBlank()) {
            ImGui.textColored(0.45f, 0.85f, 0.55f, 1.0f,
                    "Context: Selected node = " + state.selectedNodeDisplayName());
            ImGui.textDisabled("Type ID: " + state.selectedNodeTypeId());
            return;
        }
        ImGui.textDisabled("Context: No node selected");
    }

    private static void renderQuickPrompts(State state, Actions actions) {
        ImGui.separator();
        ImGui.text("Quick prompts:");

        if (state.busy()) {
            ImGui.beginDisabled();
        }

        if (ImGui.smallButton("Generate from selection")) {
            actions.onQuickPrompt("Generate a node graph based on current selection and keep existing style.");
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Optimize selected graph")) {
            actions.onQuickPrompt("Optimize selected node graph for readability and performance.");
        }
        if (ImGui.smallButton("Explain current node")) {
            actions.onQuickPrompt("Explain what the selected node does and how to connect it.");
        }
        ImGui.sameLine();
        if (ImGui.smallButton("Mobius ring example")) {
            actions.onQuickPrompt("Build a parametrized Mobius ring above selected position with radius/width/thickness controls.");
        }

        if (state.busy()) {
            ImGui.endDisabled();
        }
    }

    private static int renderChatHistory(State state) {
        float inputBlockHeight = ImGui.getFrameHeightWithSpacing() * 3.2f;
        float historyHeight = Math.max(120.0f, ImGui.getContentRegionAvailY() - inputBlockHeight);
        int updatedCount = state.lastRenderedChatCount();

        if (ImGui.beginChild("aiChatHistory", 0.0f, historyHeight, true)) {
            if (state.chatMessages() == null || state.chatMessages().isEmpty()) {
                ImGui.textDisabled("No messages yet.");
                ImGui.textDisabled("Tip: Ask AI to create or modify a node graph.");
            } else {
                for (AiChatMessage message : state.chatMessages()) {
                    boolean isUser = "user".equals(message.role());
                    ImGui.textColored(
                            isUser ? 0.45f : 0.65f,
                            isUser ? 0.75f : 0.85f,
                            isUser ? 1.0f : 0.55f,
                            1.0f,
                            isUser ? "You" : "AI");
                    ImGui.sameLine();
                    ImGui.textWrapped(message.content());
                    ImGui.spacing();
                }
                if (state.chatMessages().size() > updatedCount) {
                    ImGui.setScrollHereY(1.0f);
                    updatedCount = state.chatMessages().size();
                }
            }
        }
        ImGui.endChild();
        return updatedCount;
    }

    private static void renderPromptInput(State state, Actions actions) {
        boolean busy = state.busy();

        ImGui.pushID("ai_prompt_input_zone");

        if (busy) {
            ImGui.beginDisabled();
        }

        String rawInput = state.promptInput().get();
        long newlines = (rawInput == null) ? 0 : rawInput.chars().filter(c -> c == '\n').count();
        int activeLineCount = Math.min(4, (int) newlines + 1);
        float lineH = ImGui.getFrameHeight();
        float dynamicHeight = activeLineCount * lineH;

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - 85.0f);
        int inputFlags = ImGuiInputTextFlags.CtrlEnterForNewLine;
        if (state.enterToSend().get()) {
            inputFlags |= ImGuiInputTextFlags.EnterReturnsTrue;
        }

        boolean submitted = ImGui.inputTextMultiline("##ai_input_multiline", state.promptInput(),
                ImGui.getContentRegionAvailX() - 85.0f,
                dynamicHeight,
            inputFlags);
        ImGui.popItemWidth();

        if (busy) {
            ImGui.endDisabled();
        }

        ImGui.sameLine();
        if (ImGui.button("Send##ai_prompt_send", 80.0f, dynamicHeight) || (state.enterToSend().get() && submitted)) {
            actions.onSubmitPrompt();
        }

        ImGui.popID();
    }

    private static void renderModeHint(State state) {
        ImGui.textDisabled(state.remotePlannerEnabled()
                ? "Current mode: remote planner + DSL validation + apply/undo"
                : "Current mode: local mock planner + DSL validation + apply/undo");
    }

    private static void renderStatusMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        StatusTone tone = resolveStatusTone(message);
        switch (tone) {
            case ERROR -> ImGui.textColored(0.96f, 0.35f, 0.35f, 1.0f, "[Error] " + message);
            case WARN -> ImGui.textColored(0.95f, 0.74f, 0.30f, 1.0f, "[Warn] " + message);
            case SUCCESS -> ImGui.textColored(0.45f, 0.82f, 0.54f, 1.0f, "[OK] " + message);
            default -> ImGui.textWrapped(message);
        }
    }

    private static StatusTone resolveStatusTone(String message) {
        String lower = message.toLowerCase();
        if (containsAny(lower, "failed", "error", "invalid", "exception", "aborted")) {
            return StatusTone.ERROR;
        }
        if (containsAny(lower, "warn", "retry", "canceled", "unavailable", "busy")) {
            return StatusTone.WARN;
        }
        if (containsAny(lower, "saved", "loaded", "validated", "completed", "successful", "submitted")) {
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

    private enum StatusTone {
        INFO,
        SUCCESS,
        WARN,
        ERROR
    }

    private static void renderLanguageDiagnostics(State state) {
        String language = state.inputLanguageDetected();
        String intent = state.normalizedIntentPreview();
        if ((language == null || language.isBlank()) && (intent == null || intent.isBlank())) {
            return;
        }

        ImGui.textDisabled("Input language: " + (language == null || language.isBlank() ? "unknown" : language));
        ImGui.textDisabled("Normalized intent: " + (intent == null || intent.isBlank() ? "general-request" : intent));
    }
}
