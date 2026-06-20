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
            ImBoolean includePlayerWorldContext,
            ImBoolean includeSelectedWorldRegionContext,
            ImBoolean previewOnlyMode,
            ImBoolean patchApplyMode,
            ImBoolean patchRemoveScopedConnections,
            ImBoolean enterToSend,
            String inputLanguageDetected,
            String normalizedIntentPreview,
            String streamingPreview,
            String runtimeStage,
            String runtimeDetail,
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

        void clearConversation();
    }

    static int renderMainPanel(State state, Actions actions) {
        renderHeader(state, actions);
        renderRuntimeStatus(state);
        renderBusyStatus(state, actions);
        renderModeOptions(state);
        renderSelectionContext(state);
        renderQuickPrompts(state, actions);

        actions.renderPlanPreviewSection();

        int chatCount = renderChatHistory(state, actions);
        renderPromptInput(state, actions);
        renderLanguageDiagnostics(state);
        renderModeHint(state);
        return chatCount;
    }

    private static void renderHeader(State state, Actions actions) {
        ImGui.textWrapped("Describe the graph you want to create or change.");
        if (ImGui.smallButton("Settings")) {
            actions.openSettingsPopup();
        }
        ImGui.sameLine();
        ImGui.textDisabled(state.settingsSummary());

        if (state.settingsStatusMessage() != null && !state.settingsStatusMessage().isBlank()) {
            AiUiHelper.renderStatusMessage(state.settingsStatusMessage());
        }

        if (state.hasDebugData() && ImGui.smallButton("Debug")) {
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

        String preview = state.streamingPreview();
        if (preview != null && !preview.isBlank()) {
            ImGui.textDisabled("Streaming preview:");
            ImGui.textWrapped(preview);
        }
    }

    private static void renderRuntimeStatus(State state) {
        String stage = state.runtimeStage();
        if (stage == null || stage.isBlank()) {
            return;
        }

        float r = 0.70f;
        float g = 0.70f;
        float b = 0.70f;
        switch (stage) {
            case "Streaming" -> {
                r = 0.50f;
                g = 0.85f;
                b = 0.95f;
            }
            case "Preparing" -> {
                r = 0.95f;
                g = 0.78f;
                b = 0.30f;
            }
            case "Parsed" -> {
                r = 0.45f;
                g = 0.85f;
                b = 0.55f;
            }
            case "Failed" -> {
                r = 0.95f;
                g = 0.42f;
                b = 0.42f;
            }
            default -> {
                // Keep neutral color for Idle/unknown stages.
            }
        }

        ImGui.textColored(r, g, b, 1.0f, "Status: " + stage);
        String detail = state.runtimeDetail();
        if (detail != null && !detail.isBlank()) {
            ImGui.textDisabled(detail);
        }
    }

    private static void renderModeOptions(State state) {
        ImGui.checkbox("Use selected node as context", state.useSelectionContext());
        ImGui.checkbox("Include current graph", state.includeGraphContext());
        ImGui.checkbox("Include player position and view", state.includePlayerWorldContext());
        ImGui.checkbox("Include selected world region", state.includeSelectedWorldRegionContext());
        if (state.includePlayerWorldContext().get() || state.includeSelectedWorldRegionContext().get()) {
            ImGui.textDisabled("Enabled world context is sent to the configured remote planner.");
        }
        ImGui.checkbox("Press Enter to send", state.enterToSend());

        if (!ImGui.treeNode("Advanced apply options")) {
            return;
        }

        ImGui.checkbox("Preview only", state.previewOnlyMode());
        ImGui.checkbox("Reuse matching nodes", state.patchApplyMode());
        if (state.patchApplyMode().get()) {
            ImGui.checkbox("Remove stale scoped connections", state.patchRemoveScopedConnections());
            ImGui.textColored(0.95f, 0.72f, 0.22f, 1.0f,
                    "Some reused-node parameter updates may require manual revert.");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Graph edits are undoable, but direct state updates on reused nodes may not be fully reversible.");
            }
        }
        ImGui.treePop();
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
        ImGui.text("Quick prompts");

        if (state.busy()) {
            ImGui.beginDisabled();
        }

        boolean compact = ImGui.getContentRegionAvailX() < 330.0f;
        if (ImGui.smallButton("Generate from selection")) {
            actions.onQuickPrompt("Generate a node graph based on current selection and keep existing style.");
        }
        if (!compact) ImGui.sameLine();
        if (ImGui.smallButton("Optimize selected graph")) {
            actions.onQuickPrompt("Optimize selected node graph for readability and performance.");
        }
        if (ImGui.smallButton("Explain current node")) {
            actions.onQuickPrompt("Explain what the selected node does and how to connect it.");
        }
        if (!compact) ImGui.sameLine();
        if (ImGui.smallButton("Mobius ring example")) {
            actions.onQuickPrompt("Build a parametrized Mobius ring above selected position with radius/width/thickness controls.");
        }

        if (state.busy()) {
            ImGui.endDisabled();
        }
    }

    private static int renderChatHistory(State state, Actions actions) {
        float inputBlockHeight = ImGui.getFrameHeightWithSpacing() * 3.2f;
        float historyHeight = Math.max(120.0f, ImGui.getContentRegionAvailY() - inputBlockHeight);
        int updatedCount = state.lastRenderedChatCount();

        ImGui.separator();
        ImGui.text("Conversation");
        boolean hasMessages = state.chatMessages() != null && !state.chatMessages().isEmpty();
        if (!state.busy() && hasMessages) {
            ImGui.sameLine();
            if (ImGui.smallButton("Clear Chat")) {
                actions.clearConversation();
                return 0;
            }
        }

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

        float inputWidth = Math.max(120.0f, ImGui.getContentRegionAvailX() - 85.0f);
        ImGui.pushItemWidth(inputWidth);
        int inputFlags = ImGuiInputTextFlags.CtrlEnterForNewLine;
        if (state.enterToSend().get()) {
            inputFlags |= ImGuiInputTextFlags.EnterReturnsTrue;
        }

        boolean submitted = ImGui.inputTextMultiline("##ai_input_multiline", state.promptInput(),
                inputWidth,
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
                ? "Planner: remote"
                : "Planner: local draft");
    }

    private static void renderLanguageDiagnostics(State state) {
        String language = state.inputLanguageDetected();
        String intent = state.normalizedIntentPreview();
        if ((language == null || language.isBlank()) && (intent == null || intent.isBlank())) {
            return;
        }

        if (!ImGui.treeNode("Request diagnostics")) {
            return;
        }
        ImGui.textDisabled("Input language: " + (language == null || language.isBlank() ? "unknown" : language));
        ImGui.textDisabled("Normalized intent: " + (intent == null || intent.isBlank() ? "general-request" : intent));
        ImGui.treePop();
    }
}
