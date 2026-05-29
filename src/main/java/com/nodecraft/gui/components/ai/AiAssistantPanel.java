package com.nodecraft.gui.components.ai;

import com.nodecraft.gui.ai.*;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiChatMessage;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiGraphPlan;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiPlanConnection;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiPlanNode;
import com.nodecraft.gui.editor.impl.ImGuiNodeEditor;
import com.nodecraft.gui.editor.impl.ImGuiNodeHistory;
import com.nodecraft.gui.editor.impl.NodePosition;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AiAssistantPanel {

    private static final long AI_SESSION_SAVE_DEBOUNCE_MS = 800L;
    private static final int AI_HISTORY_MAX_CHARS_PER_MESSAGE = 1800;
    private static final int AI_HISTORY_MAX_TOTAL_CHARS = 9000;
    private static final int AI_LATEST_USER_MESSAGE_MAX_CHARS = 7000;
    private static final String[] AI_PROVIDER_STRATEGY_OPTIONS = {
            AiSettingsStore.PROVIDER_AUTO,
            AiSettingsStore.PROVIDER_OPENAI_COMPAT,
            AiSettingsStore.PROVIDER_ANTHROPIC
    };
    private static final String[] OPENAI_MODELS = {
            "gpt-4.1-mini",
            "gpt-4.1",
            "gpt-4o-mini",
            "gpt-4o"
    };
    private static final String[] ANTHROPIC_MODELS = {
            "claude-3-5-haiku-latest",
            "claude-3-7-sonnet-latest",
            "claude-sonnet-4-0"
    };
    private static final String[] DEEPSEEK_MODELS = {
            "deepseek-chat",
            "deepseek-reasoner"
    };
    private static final String[] QWEN_MODELS = {
            "qwen-max",
            "qwen-plus",
            "qwen-turbo",
            "qwen3-32b"
    };
    private static final String[] GROQ_MODELS = {
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "qwen/qwen3-32b"
    };

    private final AiAssistantComponent aiAssistantComponent;
    private final Supplier<NodeGraph> nodeGraphSupplier;
    private final Consumer<String> clipboardCopier;

    private INode selectedNode;

    private final ImString aiPromptInput = new ImString("", 2048);
    private final ImBoolean aiUseSelectionContext = new ImBoolean(true);
    private final ImBoolean aiIncludeGraphContext = new ImBoolean(true);
    private final List<AiChatMessage> aiChatMessages;
    private final ImString aiApiBaseUrl = new ImString("https://api.openai.com/v1", 512);
    private final ImString aiApiKey = new ImString("", 512);
    private final ImString aiModel = new ImString("gpt-4.1-mini", 128);
    private final ImInt aiProviderStrategyIndex = new ImInt(0);
    private final ImString aiSystemPrompt = new ImString("You are a NodeCraft graph planning assistant.", 2048);
    private final ImInt aiMaxOutputTokens = new ImInt(2048);
    private final ImInt aiRequestTimeoutSeconds = new ImInt(60);
    private final ImInt aiConversationHistoryTurns = new ImInt(6);
    private final ImBoolean aiShowApiKey = new ImBoolean(false);
    private final ImBoolean aiEnableRemotePlanner = new ImBoolean(false);
    private final ImBoolean aiAutoLayoutBeforeApply = new ImBoolean(true);
    private final ImBoolean aiPreviewOnlyMode = new ImBoolean(false);
    private final ImBoolean aiPatchApplyMode = new ImBoolean(true);
    private final ImBoolean aiPatchRemoveScopedConnections = new ImBoolean(false);
    private final ImBoolean aiEnterToSend = new ImBoolean(true);
    private final Path aiSettingsPath;
    private String aiLastSubmittedPrompt = "";
    private String aiLastDetectedProviderLabel = "";
    private CompletableFuture<AiRemotePlannerService.RemotePlanResult> aiConnectionTestFuture = null;
    private int lastRenderedChatCount = 0;
    private int lastAiUndoStepCount = 0;
    private boolean lastAiApplyWasPatch = false;
    private String aiPlanStatusMessage = "";
    private String aiSettingsStatusMessage = "";

    public AiAssistantPanel(
            AiAssistantComponent aiAssistantComponent,
            Supplier<NodeGraph> nodeGraphSupplier,
            Consumer<String> clipboardCopier
    ) {
        this.aiAssistantComponent = aiAssistantComponent;
        this.nodeGraphSupplier = nodeGraphSupplier;
        this.clipboardCopier = clipboardCopier;
        this.aiChatMessages = aiAssistantComponent.getChatMessages();
        this.aiSettingsPath = resolveAiSettingsPath();
        this.aiAssistantComponent.initializeSessionStore(aiSettingsPath);
        loadAiSettingsFromDisk();
        loadAiSessionStateFromDisk();
    }

    public void onSelectedNodeChanged(INode node) {
        this.selectedNode = node;
        aiAssistantComponent.handleEvent("nodeSelected", node == null ? null : node.getId());
    }

    public void flushSessionStateIfDue() {
        aiAssistantComponent.flushSessionStateIfDue(AI_SESSION_SAVE_DEBOUNCE_MS, this::serializePendingPlanToDsl);
    }

    public void cleanup() {
        saveAiSettingsToDisk();
        saveAiSessionStateToDiskNow();
        aiAssistantComponent.cleanup();
        aiChatMessages.clear();
        aiPromptInput.clear();
        aiApiKey.clear();
        lastAiUndoStepCount = 0;
        lastAiApplyWasPatch = false;
        aiPlanStatusMessage = "";
        aiSettingsStatusMessage = "";
        selectedNode = null;
    }

    public void render() {
        pollRemotePlannerResultIfReady();
        pollConnectionTestResultIfReady();

        String selectedNodeDisplayName = selectedNode == null ? "" : selectedNode.getDisplayName();
        String selectedNodeTypeId = selectedNode == null ? "" : selectedNode.getTypeId();
        String inputLanguageDetected = detectInputLanguage(aiPromptInput.get());
        String normalizedIntentPreview = buildNormalizedIntentPreview(aiPromptInput.get());

        lastRenderedChatCount = AiAssistantMainPanelRenderer.renderMainPanel(
                new AiAssistantMainPanelRenderer.State(
                        buildAiSettingsSummary(),
                        aiSettingsStatusMessage,
                        hasAiDebugData(),
                        isRemotePlannerBusy(),
                        aiUseSelectionContext,
                        aiIncludeGraphContext,
                        aiPreviewOnlyMode,
                        aiPatchApplyMode,
                        aiPatchRemoveScopedConnections,
                        aiEnterToSend,
                        inputLanguageDetected,
                        normalizedIntentPreview,
                        selectedNodeDisplayName,
                        selectedNodeTypeId,
                        aiChatMessages,
                        aiPromptInput,
                        aiEnableRemotePlanner.get(),
                        lastRenderedChatCount
                ),
                new AiAssistantMainPanelRenderer.Actions() {
                    @Override
                    public void openSettingsPopup() {
                        ImGui.openPopup("AI Settings");
                    }

                    @Override
                    public void openDebugConsolePopup() {
                        ImGui.openPopup("AI Debug Console");
                    }

                    @Override
                    public void cancelRequest() {
                        cancelRemotePlannerRequest();
                    }

                    @Override
                    public void onQuickPrompt(String text) {
                        setAiPrompt(text);
                    }

                    @Override
                    public void renderPlanPreviewSection() {
                        renderAiPlanPreviewSection();
                    }

                    @Override
                    public void onSubmitPrompt() {
                        submitAiPrompt();
                    }
                }
        );

        renderAiSettingsPopup();
        renderAiDebugConsolePopup();
    }

    private void renderAiSettingsPopup() {
        String detectedProviderLabel = resolveDetectedProviderLabel(aiApiBaseUrl.get());
        String[] suggestedModels = resolveSuggestedModels(aiApiBaseUrl.get());
        maybeAutofillModelByProviderChange(detectedProviderLabel, suggestedModels);

        AiAssistantSettingsPopupRenderer.renderSettingsPopup(
                new AiAssistantSettingsPopupRenderer.State(
                        aiEnableRemotePlanner,
                        aiApiBaseUrl,
                        aiApiKey,
                        aiModel,
                        detectedProviderLabel,
                        suggestedModels,
                        aiProviderStrategyIndex,
                        aiSystemPrompt,
                        aiMaxOutputTokens,
                        aiRequestTimeoutSeconds,
                        aiConversationHistoryTurns,
                        aiShowApiKey,
                        aiAutoLayoutBeforeApply,
                        aiSettingsPath
                ),
                new AiAssistantSettingsPopupRenderer.Actions() {
                    @Override
                    public void onValidateLocal() {
                        aiSettingsStatusMessage = validateAiSettings();
                    }

                    @Override
                    public void onTestRemoteConnection() {
                        testRemoteConnection();
                    }

                    @Override
                    public void onSaveSettings() {
                        saveAiSettingsToDisk();
                    }

                    @Override
                    public void onReloadSettings() {
                        loadAiSettingsFromDisk();
                        aiSettingsStatusMessage = "AI settings reloaded from disk.";
                    }
                }
        );
    }

    private void renderAiDebugConsolePopup() {
        String compactDiagnostics = buildAiDiagnosticsExportText(false);
        String fullDiagnostics = buildAiDiagnosticsExportText(true);

        AiAssistantDebugConsoleRenderer.renderDebugConsolePopup(
                new AiAssistantDebugConsoleRenderer.State(
                        aiAssistantComponent.getLastRemoteErrorCategory(),
                        aiAssistantComponent.getLastRemoteAttempts(),
                        aiAssistantComponent.getLastRemoteRawResponse(),
                        aiAssistantComponent.getLastRemoteModelText(),
                        aiAssistantComponent.getLastRemoteRequestSnapshot(),
                        compactDiagnostics,
                        fullDiagnostics
                ),
                new AiAssistantDebugConsoleRenderer.Actions() {
                    @Override
                    public void renderFailureSummarySection() {
                        AiAssistantFailurePanelRenderer.renderFailureSummaryCard(
                                new AiAssistantFailurePanelRenderer.State(
                                        aiAssistantComponent.getLastRemoteErrorCategory(),
                                        aiAssistantComponent.getLastRemoteStatusCode(),
                                        aiAssistantComponent.getLastRemoteAttempts(),
                                        aiAssistantComponent.getLastRemoteErrorMessage(),
                                        aiLastSubmittedPrompt != null && !aiLastSubmittedPrompt.isBlank(),
                                        isRemotePlannerBusy(),
                                        aiEnableRemotePlanner.get()
                                ),
                                new AiAssistantFailurePanelRenderer.Actions() {
                                    @Override
                                    public void retryLastRequest() {
                                        retryLastAiRequest();
                                    }

                                    @Override
                                    public void increaseTimeoutSeconds(int deltaSeconds) {
                                        increaseAiTimeoutSeconds(deltaSeconds);
                                    }

                                    @Override
                                    public void togglePlannerMode() {
                                        aiEnableRemotePlanner.set(!aiEnableRemotePlanner.get());
                                        saveAiSettingsToDisk();
                                        aiSettingsStatusMessage = aiEnableRemotePlanner.get()
                                                ? "Remote planner re-enabled."
                                                : "Switched to local planner for the next request.";
                                    }

                                    @Override
                                    public void openAiSettingsPopup() {
                                        ImGui.openPopup("AI Settings");
                                    }

                                    @Override
                                    public void resaveSettings() {
                                        saveAiSettingsToDisk();
                                        aiSettingsStatusMessage = "AI settings saved.";
                                    }
                                }
                        );
                    }

                    @Override
                    public void copyRawResponse() {
                        copyToClipboard(aiAssistantComponent.getLastRemoteRawResponse());
                        aiPlanStatusMessage = "Raw response copied to clipboard.";
                    }

                    @Override
                    public void copyModelText() {
                        copyToClipboard(aiAssistantComponent.getLastRemoteModelText());
                        aiPlanStatusMessage = "Model text copied to clipboard.";
                    }

                    @Override
                    public void copyRequestSnapshot() {
                        copyToClipboard(aiAssistantComponent.getLastRemoteRequestSnapshot());
                        aiPlanStatusMessage = "Request snapshot copied to clipboard.";
                    }

                    @Override
                    public void copyCompactExport() {
                        copyToClipboard(compactDiagnostics);
                        aiPlanStatusMessage = "Compact diagnostics exported to clipboard.";
                    }

                    @Override
                    public void copyFullExport() {
                        copyToClipboard(fullDiagnostics);
                        aiPlanStatusMessage = "Full diagnostics exported to clipboard.";
                    }
                }
        );
    }

    private void copyToClipboard(String text) {
        clipboardCopier.accept(text);
    }

    private boolean hasAiDebugData() {
        return (aiAssistantComponent.getLastRemoteRawResponse() != null && !aiAssistantComponent.getLastRemoteRawResponse().isBlank())
                || (aiAssistantComponent.getLastRemoteModelText() != null && !aiAssistantComponent.getLastRemoteModelText().isBlank())
                || (aiAssistantComponent.getLastRemoteRequestSnapshot() != null && !aiAssistantComponent.getLastRemoteRequestSnapshot().isBlank())
                || (aiAssistantComponent.getLastRemoteErrorMessage() != null && !aiAssistantComponent.getLastRemoteErrorMessage().isBlank());
    }

    private void increaseAiTimeoutSeconds(int deltaSeconds) {
        int current = aiRequestTimeoutSeconds.get();
        int updated = Math.max(5, Math.min(600, current + deltaSeconds));
        aiRequestTimeoutSeconds.set(updated);
        saveAiSettingsToDisk();
        aiSettingsStatusMessage = "AI timeout increased to " + updated + " seconds.";
    }

    private String buildAiDiagnosticsExportText(boolean includeFullPayloads) {
        return "[AI Debug Diagnostics]\n" +
                "category: " + nullToEmpty(aiAssistantComponent.getLastRemoteErrorCategory()) + "\n" +
                "statusCode: " + aiAssistantComponent.getLastRemoteStatusCode() + "\n" +
                "attempts: " + aiAssistantComponent.getLastRemoteAttempts() + "\n" +
                "errorMessage: " + nullToEmpty(aiAssistantComponent.getLastRemoteErrorMessage()) + "\n" +
                "statusMessage: " + nullToEmpty(aiPlanStatusMessage) + "\n\n" +
                "[Request Snapshot]\n" +
                formatDiagnosticsSection(aiAssistantComponent.getLastRemoteRequestSnapshot(), includeFullPayloads) + "\n" +
                "[Model Text]\n" +
                formatDiagnosticsSection(aiAssistantComponent.getLastRemoteModelText(), includeFullPayloads) + "\n" +
                "[Raw Response]\n" +
                formatDiagnosticsSection(aiAssistantComponent.getLastRemoteRawResponse(), includeFullPayloads) + "\n";
    }

    private String formatDiagnosticsSection(String value, boolean includeFullPayloads) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        if (includeFullPayloads) {
            return value;
        }
        return truncateForDiagnostics(value, 900);
    }

    private String truncateForDiagnostics(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n...[truncated, total chars=" + value.length() + "]";
    }

    private String validateAiSettings() {
        return AiSettingsStore.validate(collectAiSettingsData());
    }

    private String buildAiSettingsSummary() {
        return AiSettingsStore.buildSummary(collectAiSettingsData());
    }

    private Path resolveAiSettingsPath() {
        return AiSettingsStore.resolveSettingsPath();
    }

    private void loadAiSettingsFromDisk() {
        AiSettingsStore.LoadResult result = AiSettingsStore.load(aiSettingsPath);
        applyAiSettingsData(result.data());
        aiSettingsStatusMessage = result.statusMessage();
    }

    private void saveAiSettingsToDisk() {
        aiSettingsStatusMessage = AiSettingsStore.save(aiSettingsPath, collectAiSettingsData());
    }

    private void loadAiSessionStateFromDisk() {
        String status = aiAssistantComponent.loadSessionState(this::deserializePendingPlanFromDsl);
        if (status != null && !status.isBlank()) {
            aiSettingsStatusMessage = status;
        }
    }

    private void saveAiSessionStateToDisk() {
        aiAssistantComponent.queueSessionStateSave(AI_SESSION_SAVE_DEBOUNCE_MS);
    }

    private void saveAiSessionStateToDiskNow() {
        aiAssistantComponent.saveSessionStateNow(this::serializePendingPlanToDsl);
    }

    private String serializePendingPlanToDsl(AiGraphPlan plan) {
        if (plan == null) {
            return "";
        }
        return AiPlanDslWorkflowService.toDslJson(toServiceGraphPlanForHistory(plan));
    }

    private AiGraphPlan deserializePendingPlanFromDsl(String pendingPlanDslJson) {
        AiGraphDslSupport.ParseValidationResult parsed =
                AiGraphDslSupport.parseAndValidate(pendingPlanDslJson, NodeRegistry.getInstance());
        if (!parsed.isSuccess() || parsed.graph() == null) {
            aiPlanStatusMessage = "Stored pending plan skipped due to validation failure.";
            return null;
        }
        return fromServiceGraphPlan(AiPlanDslWorkflowService.fromDsl(parsed.graph()));
    }

    private void addAiChatMessage(String role, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        aiAssistantComponent.addChatMessage(role == null ? "assistant" : role, content, System.currentTimeMillis());
        saveAiSessionStateToDisk();
    }

    private void setPendingAiPlan(AiGraphPlan plan) {
        aiAssistantComponent.setPendingPlan(plan);
        saveAiSessionStateToDisk();
    }

    private AiGraphPlan getPendingAiPlan() {
        return aiAssistantComponent.getPendingPlan();
    }

    private AiSettingsStore.AiSettingsData collectAiSettingsData() {
        return new AiSettingsStore.AiSettingsData(
                aiApiBaseUrl.get(),
                aiApiKey.get(),
                aiModel.get(),
                providerStrategyFromIndex(aiProviderStrategyIndex.get()),
                aiSystemPrompt.get(),
                aiMaxOutputTokens.get(),
                aiRequestTimeoutSeconds.get(),
                aiConversationHistoryTurns.get(),
                aiShowApiKey.get(),
                aiEnableRemotePlanner.get(),
                aiAutoLayoutBeforeApply.get(),
                aiIncludeGraphContext.get(),
                aiPreviewOnlyMode.get(),
                aiPatchApplyMode.get(),
                aiPatchRemoveScopedConnections.get(),
                aiEnterToSend.get()
        );
    }

    private void applyAiSettingsData(AiSettingsStore.AiSettingsData data) {
        if (data == null) {
            return;
        }
        aiApiBaseUrl.set(data.apiBaseUrl());
        aiApiKey.set(data.apiKey());
        aiModel.set(data.model());
        aiProviderStrategyIndex.set(indexFromProviderStrategy(data.providerStrategy()));
        aiSystemPrompt.set(data.systemPrompt());
        aiMaxOutputTokens.set(data.maxOutputTokens());
        aiRequestTimeoutSeconds.set(data.timeoutSeconds());
        aiConversationHistoryTurns.set(data.conversationHistoryTurns());
        aiShowApiKey.set(data.showApiKey());
        aiEnableRemotePlanner.set(data.enableRemotePlanner());
        aiAutoLayoutBeforeApply.set(data.autoLayoutBeforeApply());
        aiIncludeGraphContext.set(data.includeGraphContext());
        aiPreviewOnlyMode.set(data.previewOnlyMode());
        aiPatchApplyMode.set(data.patchApplyMode());
        aiPatchRemoveScopedConnections.set(data.patchRemoveScopedConnections());
        aiEnterToSend.set(data.enterToSend());
    }

    private void renderAiPlanPreviewSection() {
        AiGraphPlan plan = getPendingAiPlan();
        boolean hasPlan = plan != null;
        boolean plannerBusy = isRemotePlannerBusy();

        AiGraphDiffService.GraphDiffSummary heuristicDiff = hasPlan ? buildGraphDiffSummary(plan) : null;
        AiGraphDiffService.MappedDiffSummary mappedDiff = hasPlan ? buildMappedDiffSummary(plan) : null;
        boolean canApply = hasPlan && plan.isValid() && !plan.nodes().isEmpty() && !plannerBusy;
        String undoUnavailableReason = resolveUndoUnavailableReason();
        if (plannerBusy && undoUnavailableReason.isBlank()) {
            undoUnavailableReason = "AI is generating a plan. Wait for completion before undo.";
        }
        boolean canUndoLastAiApply = undoUnavailableReason.isBlank();

        AiAssistantPlanPreviewRenderer.renderPlanPreviewSection(
                new AiAssistantPlanPreviewRenderer.State(
                        hasPlan,
                        hasPlan ? plan.summary() : "",
                        hasPlan ? plan.nodes().size() : 0,
                        hasPlan ? plan.connections().size() : 0,
                        hasPlan ? plan.validationErrors() : List.of(),
                        hasPlan ? buildPlannedNodePreviewLines(plan) : List.of(),
                        hasPlan ? buildPlannedConnectionPreviewLines(plan) : List.of(),
                        heuristicDiff,
                        mappedDiff,
                        canApply,
                        canUndoLastAiApply,
                        undoUnavailableReason,
                        aiPlanStatusMessage
                ),
                new AiAssistantPlanPreviewRenderer.Actions() {
                    @Override
                    public void applyPlan() {
                        if (aiPreviewOnlyMode.get()) {
                            runDryRunForPendingPlan();
                        } else {
                            applyPendingAiPlan();
                        }
                    }

                    @Override
                    public void dryRunReport() {
                        runDryRunForPendingPlan();
                    }

                    @Override
                    public void undoLastApply() {
                        undoLastAiApply();
                    }
                }
        );
    }

    private String resolveUndoUnavailableReason() {
        if (lastAiUndoStepCount <= 0) {
            return "No recent AI apply to undo.";
        }

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        if (editor == null || editor.getHistory() == null) {
            return "Editor history is unavailable.";
        }

        if (lastAiApplyWasPatch) {
            if (!editor.getHistory().isUndoTopActionType(ImGuiNodeHistory.ActionType.AI_PATCH)) {
                return "Latest history action is no longer this AI patch apply.";
            }
            return "";
        }

        if (!editor.getHistory().canUndo()) {
            return "Undo stack is empty.";
        }

        return "";
    }

    private List<String> buildPlannedNodePreviewLines(AiGraphPlan plan) {
        if (plan == null || plan.nodes().isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>(plan.nodes().size());
        for (AiPlanNode node : plan.nodes()) {
            lines.add(node.ref() + " -> " + node.typeId()
                    + "  (" + String.format(Locale.ROOT, "%.0f", node.offsetX())
                    + ", " + String.format(Locale.ROOT, "%.0f", node.offsetY()) + ")");
        }
        return lines;
    }

    private List<String> buildPlannedConnectionPreviewLines(AiGraphPlan plan) {
        if (plan == null || plan.connections().isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>(plan.connections().size());
        for (AiPlanConnection connection : plan.connections()) {
            lines.add(connection.sourceRef() + "." + connection.sourcePortId()
                    + " -> " + connection.targetRef() + "." + connection.targetPortId());
        }
        return lines;
    }

    private void runDryRunForPendingPlan() {
        AiGraphPlan pendingAiPlan = getPendingAiPlan();
        if (pendingAiPlan == null) {
            aiPlanStatusMessage = "Dry run aborted: no plan available.";
            return;
        }
        if (!pendingAiPlan.isValid()) {
            aiPlanStatusMessage = "Dry run aborted: plan has validation errors.";
            return;
        }

        AiGraphDiffService.GraphDiffSummary heuristic = buildGraphDiffSummary(pendingAiPlan);
        AiGraphDiffService.MappedDiffSummary mapped = buildMappedDiffSummary(pendingAiPlan);

        String reportText = AiPlanDryRunReportService.buildDryRunReport(
                pendingAiPlan.nodes().size(),
                pendingAiPlan.connections().size(),
                heuristic,
                mapped
        );
        aiPlanStatusMessage = reportText;
        addAiChatMessage("assistant", reportText);
    }

    private AiGraphDiffService.GraphDiffSummary buildGraphDiffSummary(AiGraphPlan plan) {
        if (plan == null) {
            return AiGraphDiffAdapterService.buildGraphDiffSummary(List.of(), List.of(), getNodeGraph());
        }

        List<AiGraphDiffAdapterService.PlanNode> nodes = new ArrayList<>(plan.nodes().size());
        for (AiPlanNode node : plan.nodes()) {
            nodes.add(new AiGraphDiffAdapterService.PlanNode(node.ref(), node.typeId(), node.nodeState()));
        }

        List<AiGraphDiffAdapterService.PlanConnection> connections = new ArrayList<>(plan.connections().size());
        for (AiPlanConnection connection : plan.connections()) {
            connections.add(new AiGraphDiffAdapterService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        return AiGraphDiffAdapterService.buildGraphDiffSummary(nodes, connections, getNodeGraph());
    }

    private AiGraphDiffService.MappedDiffSummary buildMappedDiffSummary(AiGraphPlan plan) {
        if (plan == null) {
            return AiGraphDiffAdapterService.buildMappedDiffSummary(List.of(), List.of(), getNodeGraph());
        }

        List<AiGraphDiffAdapterService.PlanNode> nodes = new ArrayList<>(plan.nodes().size());
        for (AiPlanNode node : plan.nodes()) {
            nodes.add(new AiGraphDiffAdapterService.PlanNode(node.ref(), node.typeId(), node.nodeState()));
        }

        List<AiGraphDiffAdapterService.PlanConnection> connections = new ArrayList<>(plan.connections().size());
        for (AiPlanConnection connection : plan.connections()) {
            connections.add(new AiGraphDiffAdapterService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        return AiGraphDiffAdapterService.buildMappedDiffSummary(nodes, connections, getNodeGraph());
    }

    private void setAiPrompt(String text) {
        if (text == null) {
            aiPromptInput.clear();
            return;
        }
        aiPromptInput.set(text);
    }

    private void submitAiPrompt() {
        String prompt = aiPromptInput.get();
        if (prompt == null || prompt.isBlank()) {
            return;
        }

        submitAiPromptWithText(prompt.trim());
        aiPromptInput.clear();
    }

    private void submitAiPromptWithText(String trimmedPrompt) {
        aiLastSubmittedPrompt = trimmedPrompt;
        addAiChatMessage("user", trimmedPrompt);

        if (aiEnableRemotePlanner.get()) {
            startRemotePlannerRequest(trimmedPrompt);
            return;
        }

        String dslJson = AiPlanDslWorkflowService.toDslJson(
                AiPlanDslWorkflowService.buildMockGraphPlan(trimmedPrompt)
        );
        applyDslResponse(trimmedPrompt, dslJson, "local-template");
    }

    private void retryLastAiRequest() {
        if (aiLastSubmittedPrompt == null || aiLastSubmittedPrompt.isBlank()) {
            aiPlanStatusMessage = "No previous prompt is available to retry.";
            return;
        }

        if (isRemotePlannerBusy()) {
            aiPlanStatusMessage = "Remote planner is already running.";
            return;
        }

        submitAiPromptWithText(aiLastSubmittedPrompt);
        aiPlanStatusMessage = "Retrying last request...";
    }

    private void startRemotePlannerRequest(String userPrompt) {
        if (isRemotePlannerBusy()) {
            aiPlanStatusMessage = "Remote planner is already running.";
            return;
        }

        String validation = validateAiSettings();
        if (validation.startsWith("Validation failed")) {
            aiPlanStatusMessage = validation;
            addAiChatMessage("assistant", validation);
            return;
        }

        NodeRegistry registry = NodeRegistry.getInstance();
        List<AiNodeSchemaCatalog.NodeSchema> allSchemas = AiNodeSchemaCatalog.collectAll(registry);
        List<AiNodeSchemaCatalog.NodeSchema> relevantSchemas = AiNodeSchemaCatalog.selectRelevant(allSchemas, userPrompt, 40);

        String systemPrompt = aiSystemPrompt.get();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = AiPromptBuilder.buildSystemPrompt(relevantSchemas);
        } else {
            systemPrompt = systemPrompt + "\n\n" + AiPromptBuilder.buildSystemPrompt(relevantSchemas);
        }

        String userPromptPayload = AiPromptBuilder.buildUserPrompt(
                userPrompt,
                AiPromptContextService.buildSelectionContextSummary(
                        aiUseSelectionContext.get(),
                        aiIncludeGraphContext.get(),
                        selectedNode,
                        getNodeGraph()
                )
        );
        List<AiRemotePlannerService.ConversationMessage> conversationHistory =
                buildConversationHistory(userPrompt, userPromptPayload);
        AiRemotePlannerService.PlannerConfig config = new AiRemotePlannerService.PlannerConfig(
                aiApiBaseUrl.get(),
                aiApiKey.get(),
                aiModel.get(),
                providerStrategyFromIndex(aiProviderStrategyIndex.get()),
                systemPrompt,
                aiMaxOutputTokens.get(),
                aiRequestTimeoutSeconds.get()
        );

        String requestSnapshot = buildRemoteRequestSnapshot(config, userPrompt, userPromptPayload, relevantSchemas.size());
        boolean submitted = aiAssistantComponent.submitRemotePlannerRequest(userPrompt, config, conversationHistory, requestSnapshot);
        aiPlanStatusMessage = submitted
            ? "Remote planner request submitted..."
            : "Remote planner is already running. Please wait for completion or cancel the current request.";
    }

    private void testRemoteConnection() {
        String validation = validateAiSettings();
        if (validation.startsWith("Validation failed")) {
            aiSettingsStatusMessage = validation;
            return;
        }

        if (aiConnectionTestFuture != null && !aiConnectionTestFuture.isDone()) {
            aiSettingsStatusMessage = "Connection test is already running...";
            return;
        }

        AiRemotePlannerService.PlannerConfig config = new AiRemotePlannerService.PlannerConfig(
                aiApiBaseUrl.get(),
                aiApiKey.get(),
                aiModel.get(),
                providerStrategyFromIndex(aiProviderStrategyIndex.get()),
                aiSystemPrompt.get(),
                aiMaxOutputTokens.get(),
                aiRequestTimeoutSeconds.get()
        );
        aiSettingsStatusMessage = "Testing remote API connection...";
        aiConnectionTestFuture = aiAssistantComponent.testRemoteConnectionAsync(config);
    }

    private void pollConnectionTestResultIfReady() {
        if (aiConnectionTestFuture == null || !aiConnectionTestFuture.isDone()) {
            return;
        }

        try {
            AiRemotePlannerService.RemotePlanResult result = aiConnectionTestFuture.join();
            if (result.success()) {
                aiSettingsStatusMessage = "Remote API connection successful (HTTP " + result.statusCode() + ").";
            } else {
                aiSettingsStatusMessage = "Remote API connection failed: " + formatRemoteErrorMessage(result);
            }
        } catch (Exception e) {
            aiSettingsStatusMessage = "Remote API connection failed: " + e.getMessage();
        } finally {
            aiConnectionTestFuture = null;
        }
    }

    private List<AiRemotePlannerService.ConversationMessage> buildConversationHistory(
            String newUserPrompt,
            String userPromptPayload
    ) {
        List<AiChatMessage> recent = getRecentPlanningMessages(resolveConversationHistoryLimit(), newUserPrompt);
        List<AiConversationHistoryService.ChatLine> historyLines = new ArrayList<>(recent.size());
        for (AiChatMessage message : recent) {
            historyLines.add(new AiConversationHistoryService.ChatLine(
                    message.role(),
                    message.content(),
                    message.timestampMs()
            ));
        }

        List<AiRemotePlannerService.ConversationMessage> history = AiConversationHistoryService.toConversationMessages(
                historyLines,
                AI_HISTORY_MAX_CHARS_PER_MESSAGE,
                AI_HISTORY_MAX_TOTAL_CHARS
        );

        String latestUserMessage = userPromptPayload;
        AiGraphPlan pendingAiPlan = getPendingAiPlan();
        if (pendingAiPlan != null) {
            String currentPlanJson = AiPlanDslWorkflowService.toDslJson(toServiceGraphPlanForHistory(pendingAiPlan));
            latestUserMessage = "Current plan in effect:\n```json\n"
                    + currentPlanJson
                    + "\n```\n\n"
                    + "User follow-up:\n"
                    + userPromptPayload;
        }

        latestUserMessage = AiConversationHistoryService.compactMessage(
                latestUserMessage,
                AI_LATEST_USER_MESSAGE_MAX_CHARS
        );

        history.add(new AiRemotePlannerService.ConversationMessage("user", latestUserMessage));
        return history;
    }

    private int resolveConversationHistoryLimit() {
        return Math.max(1, Math.min(20, aiConversationHistoryTurns.get()));
    }

    private List<AiChatMessage> getRecentPlanningMessages(int limit, String latestUserPrompt) {
        if (aiChatMessages.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<AiConversationHistoryService.ChatLine> allLines = new ArrayList<>(aiChatMessages.size());
        for (AiChatMessage message : aiChatMessages) {
            allLines.add(new AiConversationHistoryService.ChatLine(
                    message.role(),
                    message.content(),
                    message.timestampMs()
            ));
        }

        List<AiConversationHistoryService.ChatLine> selected = AiConversationHistoryService.selectRecentPlanningMessages(
                allLines,
                latestUserPrompt,
                limit
        );

        List<AiChatMessage> recent = new ArrayList<>(selected.size());
        for (AiConversationHistoryService.ChatLine line : selected) {
            recent.add(new AiChatMessage(line.role(), line.content(), line.timestampMs()));
        }
        return recent;
    }

    private void pollRemotePlannerResultIfReady() {
        AiAssistantComponent.RemotePollResult pollResult = aiAssistantComponent.pollRemotePlannerResultIfReady();
        if (pollResult == null) {
            return;
        }

        if (pollResult.hasException()) {
            String error = "Remote planner failed: " + pollResult.exceptionMessage();
            aiPlanStatusMessage = error;
            addAiChatMessage("assistant", error);
            return;
        }

        String prompt = pollResult.prompt();
        AiRemotePlannerService.RemotePlanResult result = pollResult.result();
        if (result == null) {
            String error = "Remote planner failed: unknown error";
            aiPlanStatusMessage = error;
            addAiChatMessage("assistant", error);
            return;
        }

        if (!result.success()) {
            String error = formatRemoteErrorMessage(result);
            aiPlanStatusMessage = error;
            addAiChatMessage("assistant", error);
            fallbackToLocalPlan(prompt, "remote request failed");
            return;
        }

        applyDslResponse(prompt, result.modelContent(), result.structuredPayload() ? "remote-tool" : "remote");
    }

    private void applyDslResponse(String prompt, String dslOrModelResponse, String source) {
        boolean isStructured = "remote-tool".equals(source);
        AiGraphDslSupport.ParseValidationResult parsed = isStructured
                ? AiGraphDslSupport.parseStructured(dslOrModelResponse, NodeRegistry.getInstance())
                : AiGraphDslSupport.parseAndValidate(dslOrModelResponse, NodeRegistry.getInstance());

        if (!parsed.isSuccess() || parsed.graph() == null) {
            setPendingAiPlan(null);
            String errorMessage = "Plan JSON validation failed: " + String.join("; ", parsed.errors());
            addAiChatMessage("assistant", errorMessage);
            aiPlanStatusMessage = errorMessage;
            if ("remote".equals(source)) {
                fallbackToLocalPlan(prompt, "remote JSON invalid");
            }
            return;
        }

        setPendingAiPlan(fromServiceGraphPlan(AiPlanDslWorkflowService.fromDsl(parsed.graph())));
        AiGraphPlan pendingAiPlan = getPendingAiPlan();
        String warningSuffix = formatValidationWarningSuffix(parsed.warnings());
        addAiChatMessage(
                "assistant",
                AiPromptContextService.buildAiPlanReply(
                        prompt,
                        source,
                        aiUseSelectionContext.get(),
                        selectedNode,
                        pendingAiPlan.nodes().size(),
                        pendingAiPlan.connections().size(),
                        pendingAiPlan.isValid(),
                        pendingAiPlan.validationErrors()
                ) + warningSuffix
        );
        aiPlanStatusMessage = "Plan JSON validated (" + source + "). Review and click Apply Plan." + warningSuffix;
    }

    private void fallbackToLocalPlan(String prompt, String reason) {
        String localDslJson = AiPlanDslWorkflowService.toDslJson(
                AiPlanDslWorkflowService.buildMockGraphPlan(prompt)
        );
        AiGraphDslSupport.ParseValidationResult localParsed =
                AiGraphDslSupport.parseAndValidate(localDslJson, NodeRegistry.getInstance());

        if (!localParsed.isSuccess() || localParsed.graph() == null) {
            aiPlanStatusMessage = "Local fallback also failed: " + String.join("; ", localParsed.errors());
            addAiChatMessage("assistant", aiPlanStatusMessage);
            return;
        }

        setPendingAiPlan(fromServiceGraphPlan(AiPlanDslWorkflowService.fromDsl(localParsed.graph())));
        String warningSuffix = formatValidationWarningSuffix(localParsed.warnings());
        aiPlanStatusMessage = "Remote planner fallback applied (" + reason + "). Review and click Apply Plan." + warningSuffix;
        addAiChatMessage("assistant", aiPlanStatusMessage);
    }

    private String formatValidationWarningSuffix(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return "";
        }
        return " Warning: " + String.join("; ", warnings);
    }

    private boolean isRemotePlannerBusy() {
        return aiAssistantComponent.isRemotePlannerBusy();
    }

    private void cancelRemotePlannerRequest() {
        aiAssistantComponent.cancelRemotePlannerRequest();
        aiPlanStatusMessage = "Remote planner request canceled.";
        addAiChatMessage("assistant", aiPlanStatusMessage);
    }

    private String formatRemoteErrorMessage(AiRemotePlannerService.RemotePlanResult result) {
        String category = result.errorCategory();
        String headline = switch (category) {
            case "auth" -> "Remote planner auth failed. Please check API key and permissions.";
            case "rate-limit" -> "Remote planner rate-limited. Please retry shortly or reduce request frequency.";
            case "timeout" -> "Remote planner timed out. Increase timeout or retry.";
            case "network" -> "Remote planner network error. Check connectivity and endpoint.";
            case "server" -> "Remote planner service error. Server returned 5xx.";
            case "request" -> "Remote planner rejected the request. Check model/base URL/payload.";
            case "response-format" -> "Remote planner returned an unexpected response format.";
            case "canceled" -> "Remote planner request canceled.";
            default -> "Remote planner failed.";
        };

        String detail = result.errorMessage() == null ? "" : result.errorMessage();
        String attemptInfo = result.attempts() > 1 ? " (retried " + result.attempts() + " times)" : "";
        return headline + attemptInfo + (detail.isBlank() ? "" : " Detail: " + detail);
    }

    private String buildRemoteRequestSnapshot(
            AiRemotePlannerService.PlannerConfig config,
            String userPrompt,
            String userPromptPayload,
            int schemaCount
    ) {
        String detectedLanguage = detectInputLanguage(userPrompt);
        String normalizedIntentPreview = buildNormalizedIntentPreview(userPrompt);
        String promptPreview = sanitizeUserPromptForSnapshot(userPrompt);
        String promptFingerprint = computePromptFingerprint(userPrompt);

        return "baseUrl: " + nullToEmpty(config.apiBaseUrl()) + "\n" +
                "apiKeyMasked: " + maskSecret(config.apiKey()) + "\n" +
                "model: " + nullToEmpty(config.model()) + "\n" +
                "providerStrategy: " + nullToEmpty(config.providerStrategy()) + "\n" +
                "maxOutputTokens: " + config.maxOutputTokens() + "\n" +
                "timeoutSeconds: " + config.timeoutSeconds() + "\n" +
                "selectionContextEnabled: " + aiUseSelectionContext.get() + "\n" +
                "inputLanguageDetected: " + detectedLanguage + "\n" +
                "normalizedIntentPreview: " + normalizedIntentPreview + "\n" +
                "userPromptPreview: " + promptPreview + "\n" +
                "userPromptFingerprint: " + promptFingerprint + "\n" +
                "schemaCountInjected: " + schemaCount + "\n" +
                "systemPromptLength: " + (config.systemPrompt() == null ? 0 : config.systemPrompt().length()) + "\n" +
                "userPromptLength: " + (userPrompt == null ? 0 : userPrompt.length()) + "\n" +
                "payloadLength: " + (userPromptPayload == null ? 0 : userPromptPayload.length()) + "\n";
    }

    private String sanitizeUserPromptForSnapshot(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "(empty)";
        }

        String sanitized = prompt;
        sanitized = sanitized.replaceAll("(?i)(api[_-]?key\\s*[:=]\\s*)([^\\s,;]+)", "$1***");
        sanitized = sanitized.replaceAll("(?i)(password\\s*[:=]\\s*)([^\\s,;]+)", "$1***");
        sanitized = sanitized.replaceAll("(?i)(token\\s*[:=]\\s*)([^\\s,;]+)", "$1***");
        sanitized = sanitized.replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)([^\\s,;]+)", "$1***");
        sanitized = sanitized.replaceAll("(?i)\\bsk-[a-z0-9]{16,}\\b", "***");
        sanitized = sanitized.replaceAll("(?i)\\b[a-z0-9_\\-]{32,}\\b", "***");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();

        if (sanitized.length() <= 280) {
            return sanitized;
        }
        return sanitized.substring(0, 280) + "...[truncated]";
    }

    private String computePromptFingerprint(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "none";
        }
        return Integer.toHexString(prompt.hashCode());
    }

    private String detectInputLanguage(String text) {
        if (text == null || text.isBlank()) {
            return "unknown";
        }

        int cjk = 0;
        int cyrillic = 0;
        int hangul = 0;
        int kana = 0;
        int latin = 0;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
            if (block == null) {
                continue;
            }
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                cjk++;
            } else if (block == Character.UnicodeBlock.CYRILLIC
                    || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY
                    || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_A
                    || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_B) {
                cyrillic++;
            } else if (block == Character.UnicodeBlock.HANGUL_SYLLABLES
                    || block == Character.UnicodeBlock.HANGUL_JAMO
                    || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) {
                hangul++;
            } else if (block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA
                    || block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS) {
                kana++;
            } else if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.LATIN) {
                latin++;
            }
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (cjk > 0 && kana == 0 && hangul == 0) return "zh";
        if (kana > 0) return "ja";
        if (hangul > 0) return "ko";
        if (cyrillic > 0) return "ru";

        if (latin > 0) {
            if (containsAnyLower(lower, " el ", " la ", " los ", " las ", " generar", "jugador", "entidad")) return "es";
            if (containsAnyLower(lower, " não ", "ção", " jogador", " gerar", " entidade")) return "pt";
            if (containsAnyLower(lower, " le ", " la ", " les ", " génér", " joueur", " entité")) return "fr";
            if (containsAnyLower(lower, " der ", " die ", " das ", " und ", " spieler", " entität")) return "de";
            return "en-or-latin";
        }

        return "unknown";
    }

    private String buildNormalizedIntentPreview(String text) {
        if (text == null || text.isBlank()) {
            return "empty";
        }

        String lower = text.toLowerCase(Locale.ROOT);
        List<String> tags = new ArrayList<>();

        if (containsAnyLower(lower,
                "生成", "放置", "输出", "烘焙", "spawn", "produce", "output", "create", "bake",
                "создать", "сгенер", "вывод",
                "crear", "generar", "salida",
                "criar", "gerar", "saida",
                "créer", "générer", "sortie",
                "erstellen", "generieren", "ausgabe",
                "出力", "作成",
                "생성", "출력")) {
            tags.add("generate/output");
        }

        if (containsAnyLower(lower,
                "几何", "模型", "球", "圆球", "sphere", "mesh", "geometry", "shape",
                "геометр", "сфера", "форма",
                "geometr", "esfera", "forma",
                "géométr", "sphère", "forme",
                "kugel", "sphäre", "form",
                "幾何", "球体", "形状",
                "기하", "구체", "형상")) {
            tags.add("geometry");
        }

        if (containsAnyLower(lower,
                "位置", "坐标", "头上", "头顶", "上方", "position", "offset", "above", "overhead",
                "позици", "координ", "смещ", "над",
                "posición", "coordenad", "desplaz", "encima",
                "posição", "coordenad", "desloc", "acima",
                "coordonnée", "décalage", "au-dessus",
                "koordinate", "versatz", "oben",
                "座標", "上方",
                "위치", "좌표", "오프셋", "위")) {
            tags.add("spatial/position");
        }

        if (containsAnyLower(lower,
                "玩家", "实体", "生物", "player", "entity", "mob", "living",
                "игрок", "сущност", "моб",
                "jugador", "entidad", "criatura",
                "jogador", "entidade", "criatura",
                "joueur", "entité", "créature",
                "spieler", "entität", "kreatur",
                "プレイヤー", "エンティティ", "モブ",
                "플레이어", "엔티티", "몹")) {
            tags.add("player/entity");
        }

        if (containsAnyLower(lower,
                "颜色", "彩色", "红", "绿", "蓝", "color", "red", "green", "blue", "rgb", "hsv",
                "цвет", "красный", "зеленый", "синий",
                "rojo", "verde", "azul",
                "vermelho", "verde", "azul",
                "couleur", "rouge", "vert", "bleu",
                "farbe", "rot", "grün", "blau",
                "色", "赤", "緑", "青",
                "색", "빨강", "초록", "파랑")) {
            tags.add("color");
        }

        if (containsAnyLower(lower,
                "计算", "数学", "加", "减", "乘", "除", "比较", "math", "add", "sub", "mul", "div", "logic", "compare",
                "матем", "логик", "слож", "вычит", "умнож", "делен", "сравн",
                "matem", "lógica", "sumar", "restar", "multiplicar", "dividir", "comparar",
                "logica", "somar", "subtrair",
                "logique", "addition", "soustraction", "multiplication", "division", "comparer",
                "logik", "addieren", "subtrahieren", "multiplizieren", "dividieren", "vergleichen",
                "数学", "加算", "減算", "乗算", "除算", "比較",
                "수학", "논리", "더하기", "빼기", "곱하기", "나누기", "비교")) {
            tags.add("math/logic");
        }

        if (tags.isEmpty()) {
            return "general-request";
        }
        return String.join(", ", tags);
    }

    private boolean containsAnyLower(String lowerText, String... keywords) {
        if (lowerText == null || lowerText.isBlank() || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && lowerText.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "(empty)";
        }
        int len = secret.length();
        if (len <= 6) {
            return "***";
        }
        String prefix = secret.substring(0, 4);
        String suffix = secret.substring(Math.max(0, len - 2));
        return prefix + "***" + suffix;
    }

    private String providerStrategyFromIndex(int index) {
        int safeIndex = Math.max(0, Math.min(AI_PROVIDER_STRATEGY_OPTIONS.length - 1, index));
        return AI_PROVIDER_STRATEGY_OPTIONS[safeIndex];
    }

    private int indexFromProviderStrategy(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return 0;
        }
        for (int i = 0; i < AI_PROVIDER_STRATEGY_OPTIONS.length; i++) {
            if (AI_PROVIDER_STRATEGY_OPTIONS[i].equalsIgnoreCase(strategy)) {
                return i;
            }
        }
        return 0;
    }

    private String resolveDetectedProviderLabel(String baseUrl) {
        String normalized = normalizeProviderInput(baseUrl);
        if (normalized.isBlank()) {
            return "Unknown";
        }

        if (normalized.contains("deepseek")) {
            return "DeepSeek";
        }
        if (normalized.contains("dashscope") || normalized.contains("aliyuncs") || normalized.contains("qwen")) {
            return "Qwen (DashScope)";
        }
        if (normalized.contains("anthropic")) {
            return "Anthropic";
        }
        if (normalized.contains("groq")) {
            return "Groq";
        }
        return "OpenAI-Compatible";
    }

    private String[] resolveSuggestedModels(String baseUrl) {
        String normalized = normalizeProviderInput(baseUrl);
        if (normalized.isBlank()) {
            return OPENAI_MODELS;
        }

        if (normalized.contains("deepseek")) {
            return DEEPSEEK_MODELS;
        }
        if (normalized.contains("dashscope") || normalized.contains("aliyuncs") || normalized.contains("qwen")) {
            return QWEN_MODELS;
        }
        if (normalized.contains("anthropic")) {
            return ANTHROPIC_MODELS;
        }
        if (normalized.contains("groq")) {
            return GROQ_MODELS;
        }
        return OPENAI_MODELS;
    }

    private String normalizeProviderInput(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.trim().toLowerCase(Locale.ROOT);
    }

    private void maybeAutofillModelByProviderChange(String detectedProviderLabel, String[] suggestedModels) {
        if (detectedProviderLabel == null) {
            detectedProviderLabel = "";
        }
        if (suggestedModels == null || suggestedModels.length == 0) {
            aiLastDetectedProviderLabel = detectedProviderLabel;
            return;
        }

        if (detectedProviderLabel.equals(aiLastDetectedProviderLabel)) {
            return;
        }

        String currentModel = aiModel.get();
        boolean shouldAutofill = currentModel == null
                || currentModel.isBlank()
                || !isModelInSuggestions(currentModel, suggestedModels);

        if (shouldAutofill) {
            aiModel.set(suggestedModels[0]);
            aiSettingsStatusMessage = "Provider changed to " + detectedProviderLabel
                    + ", model auto-filled: " + suggestedModels[0];
        }

        aiLastDetectedProviderLabel = detectedProviderLabel;
    }

    private boolean isModelInSuggestions(String model, String[] suggestedModels) {
        if (model == null || suggestedModels == null) {
            return false;
        }
        for (String suggestion : suggestedModels) {
            if (suggestion != null && suggestion.equalsIgnoreCase(model)) {
                return true;
            }
        }
        return false;
    }

    private AiGraphPlanDslAdapterService.GraphPlan toServiceGraphPlanForHistory(AiGraphPlan plan) {
        if (plan == null) {
            return new AiGraphPlanDslAdapterService.GraphPlan("", List.of(), List.of(), List.of());
        }

        List<AiGraphPlanDslAdapterService.PlanNode> nodes = new ArrayList<>(plan.nodes().size());
        for (AiPlanNode node : plan.nodes()) {
            nodes.add(new AiGraphPlanDslAdapterService.PlanNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    node.nodeState()
            ));
        }

        List<AiGraphPlanDslAdapterService.PlanConnection> connections = new ArrayList<>(plan.connections().size());
        for (AiPlanConnection connection : plan.connections()) {
            connections.add(new AiGraphPlanDslAdapterService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        return new AiGraphPlanDslAdapterService.GraphPlan(
                plan.summary(),
                nodes,
                connections,
                plan.validationErrors() == null ? List.of() : plan.validationErrors()
        );
    }

    private AiGraphPlan fromServiceGraphPlan(AiGraphPlanDslAdapterService.GraphPlan plan) {
        List<AiPlanNode> nodes = new ArrayList<>();
        for (AiGraphPlanDslAdapterService.PlanNode node : plan.nodes()) {
            nodes.add(new AiPlanNode(node.ref(), node.typeId(), node.offsetX(), node.offsetY(), node.nodeState()));
        }

        List<AiPlanConnection> connections = new ArrayList<>();
        for (AiGraphPlanDslAdapterService.PlanConnection connection : plan.connections()) {
            connections.add(new AiPlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        List<String> errors = plan.validationErrors() == null ? List.of() : plan.validationErrors();
        return new AiGraphPlan(plan.summary(), nodes, connections, errors);
    }

    private void applyPendingAiPlan() {
        AiGraphPlan pendingAiPlan = getPendingAiPlan();
        if (pendingAiPlan == null) {
            aiPlanStatusMessage = "No plan available.";
            return;
        }
        if (!pendingAiPlan.isValid()) {
            aiPlanStatusMessage = "Cannot apply: plan has validation errors.";
            return;
        }

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();
        float[] anchor = resolveAiPlanAnchorPosition(editor);
        List<AiPlanNode> nodesToApply = aiAutoLayoutBeforeApply.get()
                ? buildAutoLayoutNodes(pendingAiPlan)
                : pendingAiPlan.nodes();

        if (aiPatchApplyMode.get()) {
            applyPendingAiPlanPatch(editor, nodesToApply, anchor);
            return;
        }

        List<AiPlanApplyCoordinatorService.PlanNode> applyNodes = new ArrayList<>(nodesToApply.size());
        for (AiPlanNode node : nodesToApply) {
            applyNodes.add(new AiPlanApplyCoordinatorService.PlanNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    node.nodeState()
            ));
        }

        List<AiPlanApplyCoordinatorService.PlanConnection> applyConnections = new ArrayList<>(pendingAiPlan.connections().size());
        for (AiPlanConnection connection : pendingAiPlan.connections()) {
            applyConnections.add(new AiPlanApplyCoordinatorService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        AiPlanApplyCoordinatorService.ApplyResult result = AiPlanApplyCoordinatorService.applyExact(
                editor,
                applyNodes,
                applyConnections,
                anchor
        );

        if (result.success()) {
            lastAiUndoStepCount = result.undoSteps();
            lastAiApplyWasPatch = false;
        } else {
            lastAiUndoStepCount = 0;
            lastAiApplyWasPatch = false;
        }

        aiPlanStatusMessage = result.statusMessage()
                + (result.success() && aiAutoLayoutBeforeApply.get() ? " (auto layout enabled)" : "");
    }

    private void applyPendingAiPlanPatch(ImGuiNodeEditor editor, List<AiPlanNode> nodesToApply, float[] anchor) {
        AiGraphPlan pendingAiPlan = getPendingAiPlan();
        NodeGraph graph = getNodeGraph();
        if (graph == null) {
            aiPlanStatusMessage = "Patch apply failed: current graph is unavailable.";
            return;
        }

        List<AiGraphApplyAdapterService.PlanNode> patchNodes = new ArrayList<>(nodesToApply.size());
        for (AiPlanNode node : nodesToApply) {
            patchNodes.add(new AiGraphApplyAdapterService.PlanNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    node.nodeState()
            ));
        }

        List<AiGraphApplyAdapterService.PlanConnection> patchConnections = new ArrayList<>(pendingAiPlan.connections().size());
        for (AiPlanConnection connection : pendingAiPlan.connections()) {
            patchConnections.add(new AiGraphApplyAdapterService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }

        AiGraphApplyAdapterService.PatchPayload payload =
                AiGraphApplyAdapterService.toPatchPayload(patchNodes, patchConnections);

        AiGraphApplyService.ApplyResult result = AiGraphApplyService.applyPatch(
                editor,
                graph,
                payload.nodes(),
                payload.connections(),
                anchor,
                aiPatchRemoveScopedConnections.get()
        );
        if (result.success()) {
            // Patch apply records one aggregate AI_PATCH action; undo should be one step.
            lastAiUndoStepCount = 1;
            lastAiApplyWasPatch = true;
        } else {
            lastAiUndoStepCount = 0;
            lastAiApplyWasPatch = false;
        }
        aiPlanStatusMessage = result.statusMessage()
                + (result.success() && aiAutoLayoutBeforeApply.get() ? " (auto layout enabled for new nodes)" : "");
    }

    private void undoLastAiApply() {
        String undoUnavailableReason = resolveUndoUnavailableReason();
        if (!undoUnavailableReason.isBlank()) {
            aiPlanStatusMessage = "Undo unavailable: " + undoUnavailableReason;
            if (lastAiApplyWasPatch) {
                lastAiUndoStepCount = 0;
                lastAiApplyWasPatch = false;
            }
            return;
        }

        ImGuiNodeEditor editor = ImGuiNodeEditor.getInstance();

        int expectedUndoSteps = lastAiUndoStepCount;
        int undone = lastAiApplyWasPatch
            ? (editor.undo() ? 1 : 0)
            : AiPlanApplyCoordinatorService.undo(editor, expectedUndoSteps);

        aiPlanStatusMessage = "Undo completed: " + undone + " / " + expectedUndoSteps + " steps.";
        lastAiUndoStepCount = 0;
        lastAiApplyWasPatch = false;
    }

    private float[] resolveAiPlanAnchorPosition(ImGuiNodeEditor editor) {
        if (selectedNode != null) {
            NodePosition selectedPosition = editor.getNodePosition(selectedNode.getId());
            if (selectedPosition != null) {
                return new float[]{selectedPosition.x + 280.0f, selectedPosition.y};
            }
        }
        return new float[]{0.0f, 0.0f};
    }

    private List<AiPlanNode> buildAutoLayoutNodes(AiGraphPlan plan) {
        if (plan == null || plan.nodes().isEmpty()) {
            return List.of();
        }

        List<AiPlanAutoLayoutService.PlanNode> nodes = new ArrayList<>(plan.nodes().size());
        for (AiPlanNode node : plan.nodes()) {
            nodes.add(new AiPlanAutoLayoutService.PlanNode(node.ref(), node.typeId(), node.nodeState()));
        }

        List<AiPlanAutoLayoutService.PlanConnection> connections = new ArrayList<>(plan.connections().size());
        for (AiPlanConnection connection : plan.connections()) {
            connections.add(new AiPlanAutoLayoutService.PlanConnection(
                    connection.sourceRef(),
                    connection.targetRef()
            ));
        }

        List<AiPlanAutoLayoutService.ArrangedNode> arranged = AiPlanAutoLayoutService.autoLayout(nodes, connections);
        List<AiPlanNode> result = new ArrayList<>(arranged.size());
        for (AiPlanAutoLayoutService.ArrangedNode node : arranged) {
            result.add(new AiPlanNode(node.ref(), node.typeId(), node.offsetX(), node.offsetY(), node.nodeState()));
        }
        return result;
    }

    private NodeGraph getNodeGraph() {
        return nodeGraphSupplier.get();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
