package com.nodecraft.gui.components.ai;

import com.nodecraft.gui.components.ai.AiAssistantComponent.AiChatMessage;
import com.nodecraft.gui.layout.ImGuiChildScope;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.ArrayList;
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
            ImBoolean enterToSend,
            String runtimeStage,
            String runtimeDetail,
            String selectedNodeDisplayName,
            List<AiChatMessage> chatMessages,
            ImString promptInput,
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
        renderStatusLine(state, actions);
        renderContextSelector(state);
        renderQuickPrompts(state, actions);

        actions.renderPlanPreviewSection();

        int chatCount = renderChatHistory(state, actions);
        renderPromptInput(state, actions);
        return chatCount;
    }

    private static void renderHeader(State state, Actions actions) {
        ImGui.textWrapped("Describe the graph you want to create or change.");
        if (ImGui.smallButton("Settings")) {
            actions.openSettingsPopup();
        }
        String summary = state.settingsSummary();
        if (summary != null && !summary.isBlank()) {
            ImGui.sameLine();
            ImGui.textDisabled(summary);
        }

        if (state.settingsStatusMessage() != null && !state.settingsStatusMessage().isBlank()) {
            AiUiHelper.renderStatusMessage(state.settingsStatusMessage());
        }

        if (state.hasDebugData() && ImGui.smallButton("Debug")) {
            actions.openDebugConsolePopup();
        }
    }

    /** Single busy/failed status row — no raw streaming dump on the main panel. */
    private static void renderStatusLine(State state, Actions actions) {
        if (state.busy()) {
            ImGui.textColored(0.95f, 0.78f, 0.30f, 1.0f, "Generating plan…");
            ImGui.sameLine();
            if (ImGui.smallButton("Cancel")) {
                actions.cancelRequest();
            }
            return;
        }

        String stage = state.runtimeStage();
        if (stage == null || !"Failed".equals(stage)) {
            return;
        }
        String detail = state.runtimeDetail();
        String message = (detail != null && !detail.isBlank())
                ? "Failed — " + shortReason(detail)
                : "Failed";
        ImGui.textColored(0.95f, 0.42f, 0.42f, 1.0f, message);
    }

    private static String shortReason(String detail) {
        String trimmed = detail.trim();
        int cut = trimmed.indexOf('\n');
        if (cut > 0) {
            trimmed = trimmed.substring(0, cut).trim();
        }
        if (trimmed.length() > 96) {
            return trimmed.substring(0, 93) + "…";
        }
        return trimmed;
    }

    private static void renderContextSelector(State state) {
        ImGui.separator();
        ImGui.textColored(0.45f, 0.85f, 0.55f, 1.0f, buildContextSummary(state));
        if (ImGui.treeNode("Change##ai_context_change")) {
            ImGui.checkbox("Selected node", state.useSelectionContext());
            ImGui.checkbox("Nearby graph", state.includeGraphContext());
            ImGui.checkbox("World position and view", state.includePlayerWorldContext());
            ImGui.checkbox("Selected world region", state.includeSelectedWorldRegionContext());
            if (state.includePlayerWorldContext().get() || state.includeSelectedWorldRegionContext().get()) {
                ImGui.textDisabled("World context is sent to the configured remote planner.");
            }
            ImGui.treePop();
        }
    }

    private static String buildContextSummary(State state) {
        List<String> parts = new ArrayList<>(4);
        if (state.useSelectionContext().get()) {
            String name = state.selectedNodeDisplayName();
            if (name != null && !name.isBlank()) {
                parts.add(name);
            } else {
                parts.add("no selection");
            }
        }
        if (state.includeGraphContext().get()) {
            parts.add("nearby graph");
        }
        if (state.includePlayerWorldContext().get()) {
            parts.add("world position");
        }
        if (state.includeSelectedWorldRegionContext().get()) {
            parts.add("selected region");
        }
        if (parts.isEmpty()) {
            return "Context: none";
        }
        return "Context: " + String.join(" + ", parts);
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
        if (!compact) {
            ImGui.sameLine();
        }
        if (ImGui.smallButton("Suggest improvements")) {
            actions.onQuickPrompt(
                    "Suggest readability and structure improvements for the selected node graph.");
        }
        if (ImGui.smallButton("Explain selected node")) {
            actions.onQuickPrompt("Explain what the selected node does and how to connect it.");
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

        try (ImGuiChildScope scope = new ImGuiChildScope("aiChatHistory", 0.0f, historyHeight, true, 0)) {
            if (!scope.isOpen()) {
                return updatedCount;
            }
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
        if (ImGui.button("Send##ai_prompt_send", 80.0f, dynamicHeight)
                || (state.enterToSend().get() && submitted)) {
            actions.onSubmitPrompt();
        }

        ImGui.popID();
    }
}
