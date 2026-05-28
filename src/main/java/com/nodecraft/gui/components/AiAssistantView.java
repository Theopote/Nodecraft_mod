package com.nodecraft.gui.components;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.ai.*;
import com.nodecraft.gui.components.AiAssistantComponent.AiChatMessage;
import com.nodecraft.gui.components.AiAssistantComponent.AiGraphPlan;
import com.nodecraft.gui.components.AiAssistantComponent.AiPlanConnection;
import com.nodecraft.gui.components.AiAssistantComponent.AiPlanNode;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class AiAssistantView {

    private final AiAssistantComponent component;
    private final Supplier<NodeGraph> graphSupplier;
    private final Path settingsPath;

    // --- Persistence State ---
    private final ImString aiApiBaseUrl = new ImString("https://api.openai.com/v1", 512);
    private final ImString aiApiKey = new ImString("", 512);
    private final ImString aiModel = new ImString("gpt-4o-mini", 128);
    private final ImInt aiProviderStrategyIndex = new ImInt(0);
    private final ImString aiSystemPrompt = new ImString("You are a NodeCraft graph planning assistant.", 2048);
    private final ImInt aiMaxOutputTokens = new ImInt(1024);
    private final ImInt aiRequestTimeoutSeconds = new ImInt(60);
    private final ImInt aiConversationHistoryTurns = new ImInt(6);
    private final ImBoolean aiShowApiKey = new ImBoolean(false);
    private final ImBoolean aiEnableRemotePlanner = new ImBoolean(true);
    private final ImBoolean aiAutoLayoutBeforeApply = new ImBoolean(true);
    private final ImBoolean aiPreviewOnlyMode = new ImBoolean(false);
    private final ImBoolean aiPatchApplyMode = new ImBoolean(true);
    private final ImBoolean aiPatchRemoveScopedConnections = new ImBoolean(false);
    
    private final ImBoolean aiUseSelectionContext = new ImBoolean(true);
    private final ImBoolean aiIncludeGraphContext = new ImBoolean(true);
    private final ImString aiPromptInput = new ImString(2048);

    // --- Transient State ---
    private String aiPlanStatusMessage = "";
    private String aiSettingsStatusMessage = "";
    private String aiLastSubmittedPrompt = "";
    private int lastRenderedChatCount = 0;
    private CompletableFuture<AiRemotePlannerService.RemotePlanResult> connectionTestFuture = null;

    public AiAssistantView(AiAssistantComponent component, Supplier<NodeGraph> graphSupplier, Path settingsPath) {
        this.component = component;
        this.graphSupplier = graphSupplier;
        this.settingsPath = settingsPath;
        loadSettings();
    }

    public void render(INode selectedNode) {
        pollResults();
        
        ImGui.textWrapped("Describe what you want to build, and AI will generate a node graph plan.");
        if (ImGui.smallButton("AI Settings")) ImGui.openPopup("AI Settings");
        renderSettingsPopup();
        
        ImGui.sameLine();
        ImGui.textDisabled("Model: " + aiModel.get());

        if (!aiSettingsStatusMessage.isEmpty()) ImGui.textWrapped(aiSettingsStatusMessage);

        renderChatSection();
        renderInputSection();
        renderPlanPreviewSection(selectedNode);
    }

    private void renderChatSection() {
        float h = Math.max(120, ImGui.getContentRegionAvailY() - 180);
        if (ImGui.beginChild("aiChatHistory", 0, h, true)) {
            List<AiChatMessage> messages = component.getChatMessages();
            for (AiChatMessage msg : messages) {
                boolean isUser = "user".equals(msg.role());
                ImGui.textColored(isUser ? 0.4f : 0.7f, isUser ? 0.7f : 0.9f, 1.0f, 1.0f, isUser ? "You" : "AI");
                ImGui.sameLine();
                ImGui.textWrapped(msg.content());
                ImGui.spacing();
            }
            if (messages.size() > lastRenderedChatCount) {
                ImGui.setScrollHereY(1.0f);
                lastRenderedChatCount = messages.size();
            }
        }
        ImGui.endChild();
    }

    private void renderInputSection() {
        if (component.isRemotePlannerBusy()) ImGui.beginDisabled();
        
        String input = aiPromptInput.get();
        int lines = Math.min(4, Math.max(1, (int) input.chars().filter(c -> c == '\n').count() + 1));
        float h = lines * ImGui.getFrameHeight();

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - 85);
        boolean send = ImGui.inputTextMultiline("##ai_input", aiPromptInput, ImGui.getContentRegionAvailX() - 85, h, 
                ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.CtrlEnterForNewLine);
        ImGui.popItemWidth();

        ImGui.sameLine();
        if (ImGui.button("Send", 80, h) || send) submitPrompt();
        
        if (component.isRemotePlannerBusy()) ImGui.endDisabled();
    }

    private void renderPlanPreviewSection(INode selectedNode) {
        AiGraphPlan plan = component.getPendingPlan();
        if (plan == null) return;

        ImGui.separator();
        ImGui.text("Pending Plan: " + plan.summary());
        if (ImGui.button("Apply Plan")) applyPlan(selectedNode);
        ImGui.sameLine();
        if (ImGui.button("Discard")) component.setPendingPlan(null);
    }

    private void submitPrompt() {
        String p = aiPromptInput.get().trim();
        if (p.isEmpty()) return;
        aiLastSubmittedPrompt = p;
        addChatMessage("user", p);
        aiPromptInput.clear();
        
        if (aiEnableRemotePlanner.get()) startRemotePlannerRequest(p);
    }

    private void startRemotePlannerRequest(String prompt) {
        // Full Request initiation logic logic...
        aiPlanStatusMessage = "Requesting plan...";
    }

    private void applyPlan(INode selectedNode) {
        // Plan apply logic...
    }

    private void addChatMessage(String role, String content) {
        component.getChatMessages().add(new AiChatMessage(role, content, System.currentTimeMillis()));
    }

    private void loadSettings() {
        AiSettingsStore.LoadResult res = AiSettingsStore.load(settingsPath);
        if (res.data() != null) {
            AiSettingsStore.AiSettingsData d = res.data();
            aiApiBaseUrl.set(d.apiBaseUrl());
            aiApiKey.set(d.apiKey());
            aiModel.set(d.model());
            aiSystemPrompt.set(d.systemPrompt());
            aiEnableRemotePlanner.set(d.enableRemotePlanner());
        }
    }

    private void saveSettings() {
        AiSettingsStore.AiSettingsData d = new AiSettingsStore.AiSettingsData(
            aiApiBaseUrl.get(), aiApiKey.get(), aiModel.get(), "auto", aiSystemPrompt.get(),
            aiMaxOutputTokens.get(), aiRequestTimeoutSeconds.get(), 6, false, 
            aiEnableRemotePlanner.get(), aiAutoLayoutBeforeApply.get(), true, false, 
            aiPatchApplyMode.get(), aiPatchRemoveScopedConnections.get()
        );
        AiSettingsStore.save(settingsPath, d);
    }

    private void renderSettingsPopup() {
        if (ImGui.beginPopup("AI Settings")) {
            ImGui.inputText("Base URL", aiApiBaseUrl);
            ImGui.inputText("API Key", aiApiKey, ImGuiInputTextFlags.Password);
            ImGui.inputText("Model", aiModel);
            if (ImGui.button("Save")) {
                saveSettings();
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void pollResults() {
        // ... poll status
    }
}
