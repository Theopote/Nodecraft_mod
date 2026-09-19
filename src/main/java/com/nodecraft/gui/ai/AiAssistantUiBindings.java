package com.nodecraft.gui.ai;

import com.nodecraft.gui.components.ai.AiAssistantComponent.AiChatMessage;
import com.nodecraft.gui.components.ai.TopologyPreviewState;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;

/**
 * ImGui widget fields and preview UI state for the AI assistant panel.
 * Bound by renderers; owned/mutated by {@link AiAssistantController}.
 */
public final class AiAssistantUiBindings {

    public final ImString aiPromptInput = new ImString("", 2048);
    public final ImBoolean aiUseSelectionContext = new ImBoolean(true);
    public final ImBoolean aiIncludeGraphContext = new ImBoolean(true);
    public final ImBoolean aiIncludePlayerWorldContext = new ImBoolean(false);
    public final ImBoolean aiIncludeSelectedWorldRegionContext = new ImBoolean(false);
    public List<AiChatMessage> aiChatMessages;
    public final ImString aiApiBaseUrl = new ImString("https://api.openai.com/v1", 512);
    public final ImString aiApiKey = new ImString("", 512);
    public final ImString aiModel = new ImString("gpt-4.1-mini", 128);
    public final ImInt aiProviderStrategyIndex = new ImInt(0);
    public final ImString aiSystemPrompt = new ImString("You are a NodeCraft graph planning assistant.", 2048);
    public final ImInt aiMaxOutputTokens = new ImInt(2048);
    public final ImInt aiRequestTimeoutSeconds = new ImInt(60);
    public final ImInt aiConversationHistoryTurns = new ImInt(6);
    public final ImBoolean aiShowApiKey = new ImBoolean(false);
    public final ImBoolean aiRememberApiKey = new ImBoolean(false);
    public final ImBoolean aiEnableRemotePlanner = new ImBoolean(false);
    public final ImBoolean aiAutoLayoutBeforeApply = new ImBoolean(true);
    public final ImBoolean aiPreviewOnlyMode = new ImBoolean(false);
    public final ImBoolean aiPatchApplyMode = new ImBoolean(true);
    public final ImBoolean aiPatchRemoveScopedConnections = new ImBoolean(false);
    public final ImBoolean aiEnterToSend = new ImBoolean(true);
    public final ImBoolean aiDebugLoggingEnabled = new ImBoolean(false);
    public final ImBoolean aiIncludePromptPreviewInDebug = new ImBoolean(false);
    public String aiPreviewFocusedNodeRef = "";
    public boolean aiPreviewFocusScrollPending = false;
    public final TopologyPreviewState aiTopologyPreviewState = new TopologyPreviewState();

    public static final String[] AI_PROVIDER_STRATEGY_OPTIONS = {
            AiSettingsStore.PROVIDER_AUTO,
            AiSettingsStore.PROVIDER_OPENAI_COMPAT,
            AiSettingsStore.PROVIDER_ANTHROPIC
    };
}
