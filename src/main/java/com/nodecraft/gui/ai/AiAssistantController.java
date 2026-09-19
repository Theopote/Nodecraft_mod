package com.nodecraft.gui.ai;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.gui.ai.AiIntentAnalysisService.UserIntent;
import com.nodecraft.gui.components.ai.AiAssistantComponent;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiChatMessage;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiGraphPlan;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiPlanConnection;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiPlanNode;
import com.nodecraft.gui.editor.GraphApplyTargetResolver;
import com.nodecraft.gui.editor.base.GraphApplyHistoryView;
import com.nodecraft.gui.editor.base.GraphApplyTarget;
import com.nodecraft.gui.editor.base.GraphNodeAnchor;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.api.NodeDataType;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.registry.NodeRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AiAssistantController {

    private static final long AI_SESSION_SAVE_DEBOUNCE_MS = 800L;
    private static final int AI_HISTORY_MAX_CHARS_PER_MESSAGE = 1800;
    private static final int AI_HISTORY_MAX_TOTAL_CHARS = 9000;
    private static final int AI_LATEST_USER_MESSAGE_MAX_CHARS = 7000;

    private final AiAssistantComponent aiAssistantComponent;
    private final AiPlannerService plannerService = new AiPlannerService();
    private final AiPlanValidator planValidator = new AiPlanValidator();
    private final Supplier<NodeGraph> nodeGraphSupplier;
    private final Consumer<String> clipboardCopier;
    private final AiAssistantUiBindings ui;
    private final AiPlanningSession session;
    private final Path aiSettingsPath;

    private String aiLastDetectedProviderLabel = "";
    private CompletableFuture<AiRemotePlannerService.RemotePlanResult> aiConnectionTestFuture = null;
    private String aiSettingsStatusMessage = "";

    public AiAssistantController(
            AiAssistantComponent aiAssistantComponent,
            Supplier<NodeGraph> nodeGraphSupplier,
            Consumer<String> clipboardCopier,
            AiAssistantUiBindings ui
    ) {
        this(aiAssistantComponent, nodeGraphSupplier, clipboardCopier, ui, null);
    }

    public AiAssistantController(
            AiAssistantComponent aiAssistantComponent,
            Supplier<NodeGraph> nodeGraphSupplier,
            Consumer<String> clipboardCopier,
            AiAssistantUiBindings ui,
            Path settingsPathOverride
    ) {
        this.aiAssistantComponent = aiAssistantComponent;
        this.nodeGraphSupplier = nodeGraphSupplier;
        this.clipboardCopier = clipboardCopier;
        this.ui = ui;
        this.session = new AiPlanningSession(aiAssistantComponent);
        this.ui.aiChatMessages = aiAssistantComponent.getChatMessages();
        this.aiSettingsPath = settingsPathOverride != null ? settingsPathOverride : AiSettingsStore.resolveSettingsPath();
        this.aiAssistantComponent.initializeSessionStore(aiSettingsPath);
        reloadSettings();
        loadAiSessionStateFromDisk();
    }

    public void onFrameTick() {
        pollRemotePlannerResultIfReady();
        pollConnectionTestResultIfReady();
    }

    public String planStatusMessage() {
        return session.planStatusMessage();
    }

    public void setPlanStatusMessage(String message) {
        session.setPlanStatusMessage(message);
    }

    public AiPlanningSession planningSession() {
        return session;
    }

    public String settingsStatusMessage() {
        return aiSettingsStatusMessage;
    }

    public void setSettingsStatusMessage(String message) {
        aiSettingsStatusMessage = message == null ? "" : message;
    }

    public Path settingsPath() {
        return aiSettingsPath;
    }

    public String lastSubmittedPrompt() {
        return session.lastSubmittedPrompt();
    }

    public AiAssistantUiBindings ui() {
        return ui;
    }

    public AiAssistantComponent component() {
        return aiAssistantComponent;
    }

    public void toggleRemotePlannerEnabled() {
        ui.aiEnableRemotePlanner.set(!ui.aiEnableRemotePlanner.get());
        saveSettings();
        aiSettingsStatusMessage = ui.aiEnableRemotePlanner.get()
                ? "Remote planner re-enabled."
                : "Switched to local planner for the next request.";
    }

    public void resaveSettings() {
        saveSettings();
        aiSettingsStatusMessage = "AI settings saved.";
    }

    public void focusPreviewNode(String nodeRef) {
        if (nodeRef == null || nodeRef.isBlank()) {
            return;
        }
        ui.aiPreviewFocusedNodeRef = nodeRef;
        ui.aiPreviewFocusScrollPending = true;
        session.setPlanStatusMessage("Preview focus: node " + nodeRef);
    }

    public void consumePreviewFocusScroll() {
        ui.aiPreviewFocusScrollPending = false;
    }

    public String previewFocusedNodeRef() {
        return ui.aiPreviewFocusedNodeRef;
    }

    public boolean previewFocusScrollPending() {
        return ui.aiPreviewFocusScrollPending;
    }


    public void onSelectedNodeChanged(INode node) {
        aiAssistantComponent.handleEvent("nodeSelected", node == null ? null : node.getId());
    }

    public void flushSessionStateIfDue() {
        aiAssistantComponent.flushSessionStateIfDue(AI_SESSION_SAVE_DEBOUNCE_MS, AiSessionPlanCodecService::serializePendingPlanToDsl);
    }

    public void cleanup() {
        saveSettings();
        saveAiSessionStateToDiskNow();
        aiAssistantComponent.cleanup();
        ui.aiChatMessages.clear();
        ui.aiPromptInput.clear();
        ui.aiApiKey.clear();
        ui.aiTopologyPreviewState.reset();
        session.resetForCleanup();
        aiSettingsStatusMessage = "";
    }




    public void copyToClipboard(String text) {
        clipboardCopier.accept(text);
    }

    public boolean hasAiDebugData() {
        return AiDiagnosticsService.hasAiDebugData(aiAssistantComponent);
    }

    public void increaseTimeoutSeconds(int deltaSeconds) {
        int current = ui.aiRequestTimeoutSeconds.get();
        int updated = Math.max(5, Math.min(600, current + deltaSeconds));
        ui.aiRequestTimeoutSeconds.set(updated);
        saveSettings();
        aiSettingsStatusMessage = "AI timeout increased to " + updated + " seconds.";
    }

    public String validateSettings() {
        return AiSettingsStore.validate(collectAiSettingsData());
    }

    public String settingsSummary() {
        return AiSettingsStore.buildSummary(collectAiSettingsData());
    }

    public void reloadSettings() {
        AiSettingsStore.LoadResult result = AiSettingsStore.load(aiSettingsPath);
        applyAiSettingsData(result.data());
        aiSettingsStatusMessage = result.statusMessage();
    }

    public void saveSettings() {
        aiSettingsStatusMessage = AiSettingsStore.save(aiSettingsPath, collectAiSettingsData());
    }

    private void loadAiSessionStateFromDisk() {
        String status = aiAssistantComponent.loadSessionState(AiSessionPlanCodecService::deserializePendingPlanFromDsl);
        if (status != null && !status.isBlank()) {
            aiSettingsStatusMessage = status;
        }
    }

    private void saveAiSessionStateToDisk() {
        aiAssistantComponent.queueSessionStateSave(AI_SESSION_SAVE_DEBOUNCE_MS);
    }

    private void saveAiSessionStateToDiskNow() {
        aiAssistantComponent.saveSessionStateNow(AiSessionPlanCodecService::serializePendingPlanToDsl);
    }

    private void addAiChatMessage(String role, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        aiAssistantComponent.addChatMessage(role == null ? "assistant" : role, content, System.currentTimeMillis());
        saveAiSessionStateToDisk();
    }

    public void clearConversation() {
        if (isRemotePlannerBusy()) {
            session.setPlanStatusMessage("Cannot clear chat while AI is generating.");
            return;
        }

        aiAssistantComponent.clearConversationState();
        ui.aiPromptInput.clear();
        ui.aiTopologyPreviewState.reset();
        ui.aiPreviewFocusedNodeRef = "";
        ui.aiPreviewFocusScrollPending = false;
        session.clearConversationPlanningState();
        saveAiSessionStateToDiskNow();
    }

    private void setPendingAiPlan(AiGraphPlan plan) {
        session.setPendingPlan(plan);
        saveAiSessionStateToDisk();
    }

    public AiGraphPlan pendingPlan() {
        return session.pendingPlan();
    }

    private AiSettingsStore.AiSettingsData collectAiSettingsData() {
        return new AiSettingsStore.AiSettingsData(
                ui.aiApiBaseUrl.get(),
                ui.aiApiKey.get(),
                ui.aiModel.get(),
                AiProviderModelService.providerStrategyFromIndex(ui.aiProviderStrategyIndex.get(), AiAssistantUiBindings.AI_PROVIDER_STRATEGY_OPTIONS),
                ui.aiSystemPrompt.get(),
                ui.aiMaxOutputTokens.get(),
                ui.aiRequestTimeoutSeconds.get(),
                ui.aiConversationHistoryTurns.get(),
                ui.aiShowApiKey.get(),
                ui.aiRememberApiKey.get(),
                ui.aiEnableRemotePlanner.get(),
                ui.aiAutoLayoutBeforeApply.get(),
                ui.aiIncludeGraphContext.get(),
                ui.aiIncludePlayerWorldContext.get(),
                ui.aiIncludeSelectedWorldRegionContext.get(),
                ui.aiPreviewOnlyMode.get(),
                ui.aiPatchApplyMode.get(),
                ui.aiPatchRemoveScopedConnections.get(),
                ui.aiEnterToSend.get(),
                ui.aiDebugLoggingEnabled.get(),
                ui.aiIncludePromptPreviewInDebug.get()
        );
    }

    private void applyAiSettingsData(AiSettingsStore.AiSettingsData data) {
        if (data == null) {
            return;
        }
        ui.aiApiBaseUrl.set(data.apiBaseUrl());
        ui.aiApiKey.set(data.apiKey());
        ui.aiModel.set(data.model());
        ui.aiProviderStrategyIndex.set(AiProviderModelService.indexFromProviderStrategy(data.providerStrategy(), AiAssistantUiBindings.AI_PROVIDER_STRATEGY_OPTIONS));
        ui.aiSystemPrompt.set(data.systemPrompt());
        ui.aiMaxOutputTokens.set(data.maxOutputTokens());
        ui.aiRequestTimeoutSeconds.set(data.timeoutSeconds());
        ui.aiConversationHistoryTurns.set(data.conversationHistoryTurns());
        ui.aiShowApiKey.set(data.showApiKey());
        ui.aiRememberApiKey.set(data.rememberApiKey());
        ui.aiEnableRemotePlanner.set(data.enableRemotePlanner());
        ui.aiAutoLayoutBeforeApply.set(data.autoLayoutBeforeApply());
        ui.aiIncludeGraphContext.set(data.includeGraphContext());
        ui.aiIncludePlayerWorldContext.set(data.includePlayerWorldContext());
        ui.aiIncludeSelectedWorldRegionContext.set(data.includeSelectedWorldRegionContext());
        ui.aiPreviewOnlyMode.set(data.previewOnlyMode());
        ui.aiPatchApplyMode.set(data.patchApplyMode());
        ui.aiPatchRemoveScopedConnections.set(data.patchRemoveScopedConnections());
        ui.aiEnterToSend.set(data.enterToSend());
        ui.aiDebugLoggingEnabled.set(data.debugLoggingEnabled());
        ui.aiIncludePromptPreviewInDebug.set(data.includePromptPreviewInDebug());
    }


    public String resolveUndoUnavailableReason() {
        if (session.lastUndoStepCount() <= 0) {
            return "No recent AI apply to undo.";
        }

        GraphApplyTarget applyTarget = resolveGraphApplyTarget();
        if (applyTarget == null) {
            return "Editor history is unavailable.";
        }

        GraphApplyHistoryView history = applyTarget.getApplyHistoryView();
        if (session.lastApplyWasPatch()) {
            if (!history.isUndoTopAiPatch()) {
                return "Latest history action is no longer this AI patch apply.";
            }
            return "";
        }

        if (!history.canUndo()) {
            return "Undo stack is empty.";
        }

        return "";
    }

    public String resolveApplyModeHint() {
        if (ui.aiPreviewOnlyMode.get()) {
            return "Apply mode: Preview only (dry-run report, no graph mutation).";
        }

        if (!ui.aiPatchApplyMode.get()) {
            return "Apply mode: Exact replace/apply.";
        }

        UserIntent intent = AiIntentAnalysisService.classifyIntent(session.lastSubmittedPrompt());
        if (intent == UserIntent.MODIFY_PARAM) {
            return "Apply mode: Patch + parameter merge (partial params keep existing fields).";
        }
        return "Apply mode: Patch + replace node state.";
    }

    public String resolveAiRuntimeStageLabel(boolean plannerBusy, String streamingPreview) {
        if (plannerBusy) {
            if (streamingPreview != null && !streamingPreview.isBlank()) {
                return "Streaming";
            }
            return "Preparing";
        }

        String status = session.planStatusMessage() == null ? "" : session.planStatusMessage().trim();
        if (status.isBlank()) {
            return "Idle";
        }

        String normalized = status.toLowerCase(Locale.ROOT);
        if (normalized.contains("failed")
                || normalized.contains("error")
                || normalized.contains("aborted")
                || normalized.contains("unavailable")
                || normalized.contains("cannot")
                || normalized.contains("incomplete")) {
            return "Failed";
        }
        if (normalized.contains("submitted")
                || normalized.contains("processing")
                || normalized.contains("preparing")
                || normalized.contains("thinking")) {
            return "Preparing";
        }
        if (normalized.contains("validated")
                || normalized.contains("applied")
                || normalized.contains("completed")
                || normalized.contains("saved")) {
            return "Parsed";
        }

        return pendingPlan() == null ? "Idle" : "Parsed";
    }

    public List<String> buildPlannedNodePreviewLines(AiGraphPlan plan) {
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

    public List<String> buildPlannedConnectionPreviewLines(AiGraphPlan plan) {
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

    public void dryRunPendingPlan() {
        AiGraphPlan pendingAiPlan = pendingPlan();
        AiPlanValidator.GateResult gate = planValidator.checkBeforeDryRun(pendingAiPlan);
        if (!gate.allowed()) {
            session.setPlanStatusMessage(gate.rejectionMessage());
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
        session.setPlanStatusMessage(reportText);
        addAiChatMessage("assistant", reportText);
    }

    public void savePendingPlanAsTemplate() {
        AiGraphPlan pendingAiPlan = pendingPlan();
        if (pendingAiPlan == null) {
            session.setPlanStatusMessage("Save template skipped: no pending plan available.");
            return;
        }

        try {
            String dslJson = AiPlanDslWorkflowService.toDslJson(toServiceGraphPlanForHistory(pendingAiPlan));
            String suggestedName = buildTemplateFileStem(pendingAiPlan.summary());
            Path savedPath = AiTemplateLibrary.saveTemplate(suggestedName, dslJson);
            session.setPlanStatusMessage("Template saved: " + savedPath.getFileName());
            addAiChatMessage("assistant", "Template saved to " + toDisplayPath(savedPath));
        } catch (Exception e) {
            session.setPlanStatusMessage("Save template failed: " + e.getMessage());
            NodeCraft.LOGGER.warn("[AI_TEMPLATE] Failed to save template", e);
        }
    }

    private String buildTemplateFileStem(String summary) {
        String base = summary == null ? "" : summary.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isBlank()) {
            return "template";
        }
        return base.length() > 48 ? base.substring(0, 48) : base;
    }

    private String toDisplayPath(Path path) {
        if (path == null) {
            return "(unknown)";
        }
        try {
            Path cwd = Path.of("").toAbsolutePath().normalize();
            Path normalized = path.toAbsolutePath().normalize();
            if (normalized.startsWith(cwd)) {
                return cwd.relativize(normalized).toString().replace('\\', '/');
            }
            return normalized.toString().replace('\\', '/');
        } catch (Exception ignored) {
            return path.toString().replace('\\', '/');
        }
    }

    public AiGraphDiffService.GraphDiffSummary buildGraphDiffSummary(AiGraphPlan plan) {
        List<AiPlanNode> planNodes = safePlanNodes(plan);
        List<AiPlanConnection> planConnections = safePlanConnections(plan);
        return AiGraphDiffAdapterService.buildGraphDiffSummary(
                toDiffPlanNodes(planNodes),
                toDiffPlanConnections(planConnections),
                getNodeGraph()
        );
    }

    public AiGraphDiffService.MappedDiffSummary buildMappedDiffSummary(AiGraphPlan plan) {
        List<AiPlanNode> planNodes = safePlanNodes(plan);
        List<AiPlanConnection> planConnections = safePlanConnections(plan);
        return AiGraphDiffAdapterService.buildMappedDiffSummary(
                toDiffPlanNodes(planNodes),
                toDiffPlanConnections(planConnections),
                getNodeGraph()
        );
    }

    public void setPrompt(String text) {
        if (text == null) {
            ui.aiPromptInput.clear();
            return;
        }
        ui.aiPromptInput.set(text);
    }

    public void submitPrompt() {
        if (isRemotePlannerBusy()) {
            cancelRequest();
            session.setPlanStatusMessage("Previous remote request canceled. Sending the latest prompt...");
            NodeCraft.LOGGER.info("[AI_SEND] Busy request canceled before submitting latest prompt.");
        }

        String prompt = ui.aiPromptInput.get();
        int promptLength = prompt == null ? 0 : prompt.trim().length();
        session.setPlanStatusMessage("Submitting prompt (chars=" + promptLength + ")...");
        NodeCraft.LOGGER.info("[AI_SEND] Submit clicked. promptLength={}, remoteEnabled={}", promptLength, ui.aiEnableRemotePlanner.get());
        if (prompt == null || prompt.isBlank()) {
            session.setPlanStatusMessage("Prompt is empty. Please enter a request.");
            NodeCraft.LOGGER.info("[AI_SEND] Submission ignored because prompt is empty.");
            return;
        }

        submitAiPromptWithText(prompt.trim());
        ui.aiPromptInput.clear();
    }

    private void submitAiPromptWithText(String trimmedPrompt) {
        try {
            session.setLastSubmittedPrompt(trimmedPrompt);
            addAiChatMessage("user", trimmedPrompt);
            session.setPlanStatusMessage("Processing prompt...");
            NodeCraft.LOGGER.info("[AI_SEND] Processing prompt. chars={}, remoteEnabled={}",
                    trimmedPrompt.length(), ui.aiEnableRemotePlanner.get());

            if (ui.aiEnableRemotePlanner.get()) {
                startRemotePlannerRequest(trimmedPrompt);
                return;
            }

            AiPlannerService.LocalPlanPayload localPlan = plannerService.planLocal(trimmedPrompt);
            applyDslResponse(trimmedPrompt, localPlan.dslJson(), localPlan.source());
        } catch (Exception e) {
            String error = "Failed to submit prompt: " + e.getMessage();
            session.setPlanStatusMessage(error);
            addAiChatMessage("assistant", error);
            NodeCraft.LOGGER.error("[AI_SEND] Submit failed.", e);
        }
    }

    public void retryLastRequest() {
        if (session.lastSubmittedPrompt() == null || session.lastSubmittedPrompt().isBlank()) {
            session.setPlanStatusMessage("No previous prompt is available to retry.");
            return;
        }

        if (!ui.aiEnableRemotePlanner.get()) {
            session.setPlanStatusMessage("Retry requires remote planner to be enabled.");
            return;
        }

        if (isRemotePlannerBusy()) {
            session.setPlanStatusMessage("Remote planner is already running.");
            return;
        }

        session.setPlanStatusMessage("Retrying last request...");
        startRemotePlannerRequest(session.lastSubmittedPrompt());
    }

    private void startRemotePlannerRequest(String userPrompt) {
        if (isRemotePlannerBusy()) {
            session.setPlanStatusMessage("Remote planner is already running.");
            return;
        }

        session.beginFreshRemoteRequest();

        INode selectedNode = getSelectedNode();
        NodeGraph graph = getNodeGraph();
        AiWorldContextSnapshot worldContext = null;
        if (ui.aiIncludePlayerWorldContext.get() || ui.aiIncludeSelectedWorldRegionContext.get()) {
            worldContext = AiWorldContextService.capture(
                    graph,
                    selectedNode,
                    ui.aiIncludePlayerWorldContext.get(),
                    ui.aiIncludeSelectedWorldRegionContext.get()
            );
        }
        session.setLastWorldContextSnapshot(worldContext);

        String validation = validateSettings();
        if (validation.startsWith("Validation failed")) {
            session.setPlanStatusMessage(validation);
            addAiChatMessage("assistant", validation);
            return;
        }

        String userPromptPayload = AiPromptBuilder.buildUserPrompt(
                userPrompt,
                AiPromptContextService.buildSelectionContextSummary(
                        ui.aiUseSelectionContext.get(),
                        ui.aiIncludeGraphContext.get(),
                        selectedNode,
                        resolveSelectedNodePosition(),
                        graph
                ),
                worldContext
        );
        List<AiRemotePlannerService.ConversationMessage> conversationHistory =
                buildConversationHistory(userPrompt, userPromptPayload);
        AiRemotePlanningOrchestrator.PreparedRequest preparedRequest = plannerService.prepareInitialRemoteRequest(
                collectRemoteRequestSettings(),
                userPrompt,
                userPromptPayload,
                looksLikeComplexGenerationPrompt(userPrompt)
        );

        NodeCraft.LOGGER.info("[AI_SEND] Intent classified. intent={}, promptChars={}, promptFingerprint={}",
                preparedRequest.userIntent(),
                userPrompt == null ? 0 : userPrompt.length(),
                preparedRequest.promptFingerprint());
        logAiDebug("[AI_SEND] Prompt preview: {}", plannerService.sanitizeUserPromptForSnapshot(userPrompt));
        NodeCraft.LOGGER.info("[AI_SEND] Schema context selected. selectedSchemas={}, totalSchemas={}, limit={}",
                preparedRequest.selectedSchemaCount(),
                preparedRequest.totalSchemaCount(),
                preparedRequest.schemaLimit());

        AiRemotePlannerService.PlannerConfig config = preparedRequest.config();
        boolean submitted = aiAssistantComponent.submitRemotePlannerRequest(
                userPrompt,
                config,
                conversationHistory,
                preparedRequest.requestSnapshot()
        );
        if (submitted) {
            NodeCraft.LOGGER.info(
                    "[AI_SEND] Remote planner submitted. provider={}, baseUrl={}, model={}, strategy={}, timeoutSeconds={}, maxOutputTokens={}, schemas={}, historyMessages={}, promptFingerprint={}",
                    AiProviderModelService.resolveDetectedProviderLabel(config.apiBaseUrl()),
                    config.apiBaseUrl(),
                    config.model(),
                    config.providerStrategy(),
                    config.timeoutSeconds(),
                    config.maxOutputTokens(),
                    preparedRequest.selectedSchemaCount(),
                    conversationHistory.size(),
                    preparedRequest.promptFingerprint()
            );
            session.setPlanStatusMessage("Remote planner request submitted...");
            addAiChatMessage("assistant", "Remote planner request submitted. Streaming output will appear while generating.");
            return;
        }

        NodeCraft.LOGGER.warn("[AI_SEND] Remote planner submit rejected because another request is running.");
        session.setPlanStatusMessage("Remote planner is already running. Please wait for completion or cancel the current request.");
        addAiChatMessage("assistant", session.planStatusMessage());
    }

    public void testRemoteConnection() {
        String validation = validateSettings();
        if (validation.startsWith("Validation failed")) {
            aiSettingsStatusMessage = validation;
            return;
        }

        if (aiConnectionTestFuture != null && !aiConnectionTestFuture.isDone()) {
            aiSettingsStatusMessage = "Connection test is already running...";
            return;
        }

        AiRemotePlannerService.PlannerConfig config = new AiRemotePlannerService.PlannerConfig(
                ui.aiApiBaseUrl.get(),
                resolveEffectiveApiKey(),
                ui.aiModel.get(),
                AiProviderModelService.providerStrategyFromIndex(ui.aiProviderStrategyIndex.get(), AiAssistantUiBindings.AI_PROVIDER_STRATEGY_OPTIONS),
                ui.aiSystemPrompt.get(),
                ui.aiMaxOutputTokens.get(),
                ui.aiRequestTimeoutSeconds.get()
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
        AiGraphPlan pendingAiPlan = pendingPlan();
        if (pendingAiPlan != null) {
            String currentPlanJson = AiPlanDslWorkflowService.toDslJsonCompact(toServiceGraphPlanForHistory(pendingAiPlan));
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
        return Math.max(1, Math.min(20, ui.aiConversationHistoryTurns.get()));
    }

    private boolean looksLikeComplexGenerationPrompt(String prompt) {
        String text = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        return containsAny(text,
                "教堂", "建筑", "城堡", "房子", "结构", "生成", "建造",
                "cathedral", "church", "building", "castle", "house", "structure", "generate", "build",
                "gothic", "哥特");
    }

    private List<AiChatMessage> getRecentPlanningMessages(int limit, String latestUserPrompt) {
        if (ui.aiChatMessages.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<AiConversationHistoryService.ChatLine> allLines = new ArrayList<>(ui.aiChatMessages.size());
        for (AiChatMessage message : ui.aiChatMessages) {
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
            NodeCraft.LOGGER.warn("[AI_SEND] Remote planner completed with exception. messageChars={}",
                    pollResult.exceptionMessage() == null ? 0 : pollResult.exceptionMessage().length());
            logAiDebug("[AI_SEND] Remote planner exception detail: {}", pollResult.exceptionMessage());
            session.setPlanStatusMessage(error);
            addAiChatMessage("assistant", error);
            return;
        }

        String prompt = pollResult.prompt();
        AiRemotePlannerService.RemotePlanResult result = pollResult.result();
        if (result == null) {
            String error = "Remote planner failed: unknown error";
            NodeCraft.LOGGER.warn("[AI_SEND] Remote planner completed with null result.");
            session.setPlanStatusMessage(error);
            addAiChatMessage("assistant", error);
            return;
        }

        if (!result.success()) {
            String error = formatRemoteErrorMessage(result);
            NodeCraft.LOGGER.warn(
                    "[AI_SEND] Remote planner failed. category={}, statusCode={}, attempts={}, detailChars={}",
                    result.errorCategory(),
                    result.statusCode(),
                    result.attempts(),
                    result.errorMessage() == null ? 0 : result.errorMessage().length()
            );
            logAiDebug("[AI_SEND] Remote planner failure detail: {}", result.errorMessage());
            session.setPlanStatusMessage(error);
            addAiChatMessage("assistant", error);
            setPendingAiPlan(null);
            return;
        }

        NodeCraft.LOGGER.info(
                "[AI_SEND] Remote planner succeeded. statusCode={}, attempts={}, structuredPayload={}, modelContentChars={}",
                result.statusCode(),
                result.attempts(),
                result.structuredPayload(),
                result.modelContent() == null ? 0 : result.modelContent().length()
        );
        applyDslResponse(prompt, result.modelContent(), result.structuredPayload() ? "remote-tool" : "remote");
    }

    private void applyDslResponse(String prompt, String dslOrModelResponse, String source) {
        boolean isStructured = "remote-tool".equals(source);
        AiGraphDslSupport.ParseValidationResult parsed =
                planValidator.parseModelResponse(dslOrModelResponse, isStructured);
        NodeCraft.LOGGER.info("[AI_SEND] DSL parse result. source={}, success={}, errors={}, warnings={}",
                source,
                parsed.isSuccess(),
                parsed.errors() == null ? 0 : parsed.errors().size(),
                parsed.warnings() == null ? 0 : parsed.warnings().size());

        if (!parsed.isSuccess() || parsed.graph() == null) {
            String errorMessage = (parsed.errors() != null && !parsed.errors().isEmpty())
                    ? "Plan JSON validation failed: " + String.join("; ", parsed.errors())
                    : "Plan validation failed (no error details available).";
            addAiChatMessage("assistant", errorMessage);
            session.setPlanStatusMessage(errorMessage);
            NodeCraft.LOGGER.warn("[AI_SEND] DSL parse failed. source={}, errors={}", source, parsed.errors());
            if ("remote".equals(source) || "remote-tool".equals(source)) {
                if (tryStartRemoteDslRepair(prompt, dslOrModelResponse, parsed.errors())) {
                    return;
                }
                setPendingAiPlan(null);
                session.setPlanStatusMessage(errorMessage + " No fallback plan was generated; fix the remote output or switch to local mode explicitly.");
                addAiChatMessage("assistant", session.planStatusMessage());
            }
            return;
        }

        session.setDslRepairAttempts(0);

        AiGraphPlanDslAdapterService.GraphPlan enrichedPlan = enrichPlanWithIntentDefaults(
            prompt,
            AiPlanDslWorkflowService.fromDsl(parsed.graph())
        );

        if (shouldRequestConnectedGraphExpansion(prompt, enrichedPlan)
                && tryStartRemoteGraphExpansion(prompt, enrichedPlan, dslOrModelResponse)) {
            return;
        }

        session.setGraphExpansionAttempts(0);
        setPendingAiPlan(fromServiceGraphPlan(enrichedPlan));
        ui.aiPreviewFocusedNodeRef = "";
        ui.aiPreviewFocusScrollPending = false;
        AiGraphPlan pendingAiPlan = pendingPlan();
        String warningSuffix = formatValidationWarningSuffix(parsed.warnings());
        UserIntent intent = AiIntentAnalysisService.classifyIntent(prompt);
        boolean shouldAutoApplyPlacement = intent != UserIntent.MODIFY_PARAM && intent != UserIntent.RESTRUCTURE && intent != UserIntent.EXPLAIN && shouldAutoApplyPlacementPlan(prompt, pendingAiPlan);
        boolean autoAppliedPlacement = false;
        if (shouldAutoApplyPlacement) {
            autoAppliedPlacement = applyPlacementPlan(pendingAiPlan);
        }
        NodeCraft.LOGGER.info("[AI_SEND] Plan parsed. source={}, nodes={}, connections={}, nodeTypes={}, shouldAutoApplyPlacement={}, autoAppliedPlacement={}",
            source,
            pendingAiPlan == null || pendingAiPlan.nodes() == null ? 0 : pendingAiPlan.nodes().size(),
            pendingAiPlan == null || pendingAiPlan.connections() == null ? 0 : pendingAiPlan.connections().size(),
            summarizePlanNodeTypes(pendingAiPlan),
            shouldAutoApplyPlacement,
            autoAppliedPlacement);
        if (pendingAiPlan != null) {
            int nodeCount = pendingAiPlan.nodes() == null ? 0 : pendingAiPlan.nodes().size();
            int connectionCount = pendingAiPlan.connections() == null ? 0 : pendingAiPlan.connections().size();
            addAiChatMessage(
                    "assistant",
                    AiPromptContextService.buildAiPlanReply(
                            prompt,
                            source,
                            ui.aiUseSelectionContext.get(),
                            getSelectedNode(),
                            nodeCount,
                            connectionCount,
                            pendingAiPlan.isValid(),
                            pendingAiPlan.validationErrors()
                    ) + warningSuffix
            );
        }
        if (!shouldAutoApplyPlacement) {
            session.setPlanStatusMessage("Plan JSON validated (" + source + "). Review and click Apply Plan." + warningSuffix);
        } else if (!autoAppliedPlacement) {
            if (!warningSuffix.isBlank()) {
                session.setPlanStatusMessage(session.planStatusMessage() + warningSuffix);
            }
        } else if (!warningSuffix.isBlank()) {
            session.setPlanStatusMessage(session.planStatusMessage() + warningSuffix);
        }
    }

    private boolean tryStartRemoteDslRepair(String originalPrompt, String invalidDslOrModelResponse, List<String> parseErrors) {
        if (session.dslRepairAttempts() >= plannerService.maxDslRepairAttempts()) {
            return false;
        }
        if (!ui.aiEnableRemotePlanner.get()) {
            return false;
        }
        if (isRemotePlannerBusy()) {
            return false;
        }

        String validation = validateSettings();
        if (validation.startsWith("Validation failed")) {
            session.setPlanStatusMessage(validation);
            addAiChatMessage("assistant", validation);
            return false;
        }

        AiRemotePlanningOrchestrator.PreparedRetryRequest preparedRequest =
                plannerService.prepareDslRepairRequest(
                        collectRemoteRequestSettings(),
                        originalPrompt,
                        invalidDslOrModelResponse,
                        parseErrors,
                        session.dslRepairAttempts(),
                        AiPromptBuilder.serializeWorldContext(session.lastWorldContextSnapshot())
                );
        boolean submitted = aiAssistantComponent.submitRemotePlannerRequest(
                originalPrompt,
                preparedRequest.config(),
                preparedRequest.conversation(),
                preparedRequest.requestSnapshot()
        );
        if (!submitted) {
            return false;
        }

        session.setDslRepairAttempts(preparedRequest.nextAttempt());
        session.setPlanStatusMessage("Remote DSL validation failed. Running schema/type repair retry "
            + preparedRequest.nextAttempt() + " / " + preparedRequest.maxAttempts() + "...");
        addAiChatMessage("assistant", session.planStatusMessage());
        NodeCraft.LOGGER.info("[AI_SEND] Started remote DSL repair retry {}/{}. errors={}",
                preparedRequest.nextAttempt(),
                preparedRequest.maxAttempts(),
                preparedRequest.diagnosticText());
        NodeCraft.LOGGER.info("[AI_SEND] Repair schema context selected. selectedSchemas={}, totalSchemas={}, limit={}",
                preparedRequest.selectedSchemaCount(),
                preparedRequest.totalSchemaCount(),
                preparedRequest.schemaLimit());
        return true;
    }

    private boolean shouldRequestConnectedGraphExpansion(
            String prompt,
            AiGraphPlanDslAdapterService.GraphPlan plan
    ) {
        return plannerService.shouldRequestConnectedGraphExpansion(
                prompt,
                plan,
                session.graphExpansionAttempts(),
                looksLikeComplexGenerationPrompt(prompt)
        );
    }

    private boolean isPlacementOnlyCanvasPrompt(String prompt) {
        String text = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        return containsAny(text,
                "画布", "节点放到", "放置节点", "添加节点", "canvas", "place node", "add node")
                && !looksLikeComplexGenerationPrompt(text);
    }

    private boolean tryStartRemoteGraphExpansion(
            String originalPrompt,
            AiGraphPlanDslAdapterService.GraphPlan underspecifiedPlan,
            String originalModelPayload
    ) {
        if (session.graphExpansionAttempts() >= plannerService.maxGraphExpansionAttempts()) {
            return false;
        }
        if (!ui.aiEnableRemotePlanner.get() || isRemotePlannerBusy()) {
            return false;
        }

        String validation = validateSettings();
        if (validation.startsWith("Validation failed")) {
            session.setPlanStatusMessage(validation);
            addAiChatMessage("assistant", validation);
            return false;
        }

        AiRemotePlanningOrchestrator.PreparedRetryRequest preparedRequest =
                plannerService.prepareGraphExpansionRequest(
                        collectRemoteRequestSettings(),
                        originalPrompt,
                        underspecifiedPlan,
                        originalModelPayload,
                        session.graphExpansionAttempts(),
                        AiPromptBuilder.serializeWorldContext(session.lastWorldContextSnapshot())
                );
        boolean submitted = aiAssistantComponent.submitRemotePlannerRequest(
                originalPrompt,
                preparedRequest.config(),
                preparedRequest.conversation(),
                preparedRequest.requestSnapshot()
        );
        if (!submitted) {
            return false;
        }

        session.setGraphExpansionAttempts(preparedRequest.nextAttempt());
        session.setPlanStatusMessage("Remote plan was valid but not connected. Requesting connected graph expansion...");
        addAiChatMessage("assistant", session.planStatusMessage());
        NodeCraft.LOGGER.info(
                "[AI_SEND] Started connected graph expansion retry {}/{}. currentNodes={}, currentConnections={}, selectedSchemas={}, totalSchemas={}",
                preparedRequest.nextAttempt(),
                preparedRequest.maxAttempts(),
                underspecifiedPlan == null || underspecifiedPlan.nodes() == null ? 0 : underspecifiedPlan.nodes().size(),
                underspecifiedPlan == null || underspecifiedPlan.connections() == null ? 0 : underspecifiedPlan.connections().size(),
                preparedRequest.selectedSchemaCount(),
                preparedRequest.totalSchemaCount()
        );
        return true;
    }

    private boolean shouldAutoApplyPlacementPlan(String prompt, AiGraphPlan plan) {
        if (ui.aiPreviewOnlyMode.get() || plan == null || !plan.isValid()) {
            return false;
        }
        if (plan.nodes() == null || plan.connections() == null) {
            return false;
        }
        if (plan.nodes().size() != 1 || !plan.connections().isEmpty()) {
            return false;
        }

        AiPlanNode node = plan.nodes().getFirst();
        if (node == null || node.typeId() == null || node.typeId().isBlank()) {
            return false;
        }

        if (AiIntentAnalysisService.classifyIntent(prompt) != UserIntent.GENERATE_NEW) {
            return false;
        }

        String nodeType = node.typeId().toLowerCase(Locale.ROOT);
        if (nodeType.startsWith("world.selection.")) {
            return true;
        }
        if (nodeType.startsWith("input.type_selectors.")) {
            return true;
        }

        boolean placementLikeNodeType = nodeType.startsWith("world.")
                || nodeType.contains("selection")
                || nodeType.contains("selector")
                || nodeType.contains("placement")
                || nodeType.contains("region");
        if (!placementLikeNodeType) {
            return false;
        }

        String normalizedPrompt = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        boolean hasPlacementAction = containsAny(normalizedPrompt,
                "place", "add", "insert", "spawn", "set",
                "放置", "添加", "插入", "生成", "摆放", "设置");
        boolean hasPlacementTarget = containsAny(normalizedPrompt,
                "block", "blocks", "selection", "selector", "region",
                "方块", "选区", "选择器", "区域", "地形");
        return hasPlacementAction && hasPlacementTarget;
    }

    private boolean containsAny(String text, String... keywords) {
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

    private boolean applyPlacementPlan(AiGraphPlan pendingAiPlan) {
        if (pendingAiPlan == null || !pendingAiPlan.isValid() || pendingAiPlan.nodes() == null || pendingAiPlan.nodes().isEmpty()) {
            NodeCraft.LOGGER.info("[AI_SEND] Placement auto-apply skipped: invalid or empty pending plan.");
            return false;
        }

        GraphApplyTarget applyTarget = resolveGraphApplyTarget();
        if (applyTarget == null) {
            session.setPlanStatusMessage("Placement apply failed: editor is unavailable.");
            NodeCraft.LOGGER.warn("[AI_SEND] Placement auto-apply failed: editor unavailable.");
            return false;
        }

        float[] anchor = resolveAiPlanAnchorPosition(applyTarget);
        NodeCraft.LOGGER.info("[AI_SEND] Placement auto-apply start. nodes={}, anchor=({}, {})",
                pendingAiPlan.nodes().size(), anchor[0], anchor[1]);
        List<AiPlanApplyCoordinatorService.PlanNode> applyNodes = new ArrayList<>(pendingAiPlan.nodes().size());
        for (AiPlanNode node : pendingAiPlan.nodes()) {
            applyNodes.add(new AiPlanApplyCoordinatorService.PlanNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    node.nodeState()
            ));
        }

        AiPlanApplyCoordinatorService.ApplyResult result = AiPlanApplyCoordinatorService.applyExact(
                applyTarget,
                applyNodes,
                List.of(),
                anchor
        );

        if (result.success()) {
            session.recordApplyResult(result.undoSteps(), false);
            session.setPlanStatusMessage(result.statusMessage() + " (placement auto-applied)");
            NodeCraft.LOGGER.info("[AI_SEND] Placement auto-apply success. createdNodes={}, connectedEdges={}, undoSteps={}, status={}",
                    result.createdNodes(), result.connectedEdges(), result.undoSteps(), result.statusMessage());
            return true;
        }

        session.clearApplyBookkeeping();
        session.setPlanStatusMessage("Placement auto-apply failed: " + result.statusMessage());
        NodeCraft.LOGGER.warn("[AI_SEND] Placement auto-apply failed. status={}", result.statusMessage());
        return false;
    }

    private String formatValidationWarningSuffix(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return "";
        }
        return " Warning: " + String.join("; ", warnings);
    }

    private String summarizePlanNodeTypes(AiGraphPlan plan) {
        if (plan == null || plan.nodes() == null || plan.nodes().isEmpty()) {
            return "[]";
        }
        List<String> types = new ArrayList<>();
        for (AiPlanNode node : plan.nodes()) {
            if (node == null || node.typeId() == null || node.typeId().isBlank()) {
                continue;
            }
            types.add(node.ref() + ":" + node.typeId());
            if (types.size() >= 12) {
                types.add("...");
                break;
            }
        }
        return types.toString();
    }

    private record PortMeta(String id, NodeDataType dataType, boolean required) {
    }

    private record NodeMeta(
            String ref,
            String typeId,
            String category,
            float x,
            List<PortMeta> inputs,
            List<PortMeta> outputs
    ) {
    }

    private AiGraphPlanDslAdapterService.GraphPlan enrichPlanWithIntentDefaults(
            String prompt,
            AiGraphPlanDslAdapterService.GraphPlan plan
    ) {
        if (plan == null || plan.nodes() == null || plan.nodes().isEmpty()) {
            return plan;
        }

        List<AiGraphPlanDslAdapterService.PlanNode> normalizedNodes = applyDefaultNodeParams(plan.nodes());
        List<AiGraphPlanDslAdapterService.PlanConnection> normalizedConnections =
                plan.connections() == null ? new ArrayList<>() : new ArrayList<>(plan.connections());

        int beforeConnectionCount = normalizedConnections.size();
        UserIntent intent = AiIntentAnalysisService.classifyIntent(prompt);
        if (intent == UserIntent.GENERATE_NEW && normalizedNodes.size() > 1 && normalizedConnections.isEmpty()) {
            List<AiGraphPlanDslAdapterService.PlanConnection> inferredConnections = buildAutoConnections(normalizedNodes);
            normalizedConnections.addAll(filterValidInferredConnections(
                    plan.summary(),
                    normalizedNodes,
                    normalizedConnections,
                    inferredConnections
            ));
        }

        int addedConnections = normalizedConnections.size() - beforeConnectionCount;
        if (addedConnections > 0) {
            NodeCraft.LOGGER.info(
                    "[AI_SEND] Plan enrichment added {} inferred connections for intent={}.",
                    addedConnections,
                    intent
            );
        }

        return new AiGraphPlanDslAdapterService.GraphPlan(
                plan.summary(),
                normalizedNodes,
                normalizedConnections,
                plan.validationErrors() == null ? List.of() : plan.validationErrors()
        );
    }

    private List<AiGraphPlanDslAdapterService.PlanConnection> filterValidInferredConnections(
            String summary,
            List<AiGraphPlanDslAdapterService.PlanNode> nodes,
            List<AiGraphPlanDslAdapterService.PlanConnection> existingConnections,
            List<AiGraphPlanDslAdapterService.PlanConnection> inferredConnections
    ) {
        if (inferredConnections == null || inferredConnections.isEmpty()) {
            return List.of();
        }

        List<AiGraphPlanDslAdapterService.PlanConnection> accepted = new ArrayList<>();
        List<AiGraphPlanDslAdapterService.PlanConnection> working = new ArrayList<>(
                existingConnections == null ? List.of() : existingConnections
        );

        for (AiGraphPlanDslAdapterService.PlanConnection candidate : inferredConnections) {
            working.add(candidate);
            AiGraphPlanDslAdapterService.GraphPlan trial = new AiGraphPlanDslAdapterService.GraphPlan(
                    summary,
                    nodes,
                    working,
                    List.of()
            );
            AiGraphDslSupport.ParseValidationResult parsed =
                    planValidator.parseAndValidateJson(AiPlanDslWorkflowService.toDslJsonCompact(trial));
            if (parsed.isSuccess()) {
                accepted.add(candidate);
            } else {
                working.removeLast();
                NodeCraft.LOGGER.warn(
                        "[AI_SEND] Rejected inferred connection {}.{} -> {}.{} because validation failed: {}",
                        candidate.sourceRef(),
                        candidate.sourcePortId(),
                        candidate.targetRef(),
                        candidate.targetPortId(),
                        parsed.errors()
                );
            }
        }

        return accepted;
    }

    private List<AiGraphPlanDslAdapterService.PlanNode> applyDefaultNodeParams(
            List<AiGraphPlanDslAdapterService.PlanNode> nodes
    ) {
        NodeRegistry registry = NodeRegistry.getInstance();
        Map<String, Map<String, Object>> defaultStateCache = new HashMap<>();
        List<AiGraphPlanDslAdapterService.PlanNode> result = new ArrayList<>(nodes.size());

        for (AiGraphPlanDslAdapterService.PlanNode node : nodes) {
            Map<String, Object> existingState = toStateMap(node.nodeState());
            Map<String, Object> defaultState = defaultStateCache.computeIfAbsent(
                    node.typeId(),
                    typeId -> resolveDefaultNodeState(registry, typeId)
            );

            if (defaultState.isEmpty()) {
                result.add(node);
                continue;
            }

            Map<String, Object> merged = new HashMap<>(defaultState);
            merged.putAll(existingState);

            result.add(new AiGraphPlanDslAdapterService.PlanNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    merged
            ));
        }

        return result;
    }

    private Map<String, Object> resolveDefaultNodeState(NodeRegistry registry, String typeId) {
        if (registry == null || typeId == null || typeId.isBlank()) {
            return Map.of();
        }
        try {
            return registry.getDefaultNodeState(typeId);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> toStateMap(Object state) {
        if (!(state instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key && !key.isBlank()) {
                normalized.put(key, entry.getValue());
            }
        }
        return normalized;
    }

    private List<AiGraphPlanDslAdapterService.PlanConnection> buildAutoConnections(
            List<AiGraphPlanDslAdapterService.PlanNode> nodes
    ) {
        List<AiGraphPlanDslAdapterService.PlanConnection> generated = new ArrayList<>();
        if (nodes == null || nodes.size() < 2) {
            return generated;
        }

        List<NodeMeta> metas = buildNodeMetas(nodes);
        if (metas.size() < 2) {
            return generated;
        }

        Set<String> usedTargetPorts = new HashSet<>();
        List<NodeMeta> targetOrder = new ArrayList<>(metas);
        targetOrder.sort(Comparator
                .comparing((NodeMeta meta) -> !isOutputCategory(meta.category()))
                .thenComparing(NodeMeta::x));

        for (NodeMeta target : targetOrder) {
            if (target.inputs() == null || target.inputs().isEmpty()) {
                continue;
            }
            for (PortMeta input : target.inputs()) {
                if (!input.required()) {
                    continue;
                }
                if (!shouldAutoWireInputPort(input)) {
                    continue;
                }
                String targetKey = target.ref() + "#" + input.id();
                if (usedTargetPorts.contains(targetKey)) {
                    continue;
                }

                NodeMeta source = findBestSourceMeta(metas, target, input);
                if (source == null) {
                    continue;
                }

                PortMeta sourcePort = findBestSourcePort(source, input);
                if (sourcePort == null) {
                    continue;
                }

                generated.add(new AiGraphPlanDslAdapterService.PlanConnection(
                        source.ref(),
                        sourcePort.id(),
                        target.ref(),
                        input.id()
                ));
                usedTargetPorts.add(targetKey);
            }
        }

        return generated;
    }

    private List<NodeMeta> buildNodeMetas(List<AiGraphPlanDslAdapterService.PlanNode> nodes) {
        NodeRegistry registry = NodeRegistry.getInstance();
        List<NodeMeta> metas = new ArrayList<>(nodes.size());
        for (AiGraphPlanDslAdapterService.PlanNode node : nodes) {
            String category = "";
            if (registry != null && node.typeId() != null && !node.typeId().isBlank()) {
                var info = registry.getNodeInfo(node.typeId());
                if (info != null && info.getCategoryId() != null) {
                    category = info.getCategoryId();
                }
            }

            List<PortMeta> inputs = new ArrayList<>();
            List<PortMeta> outputs = new ArrayList<>();
            try {
                INode instance = registry == null ? null : registry.createNodeInstance(node.typeId());
                if (instance != null) {
                    for (IPort input : instance.getInputPorts()) {
                        inputs.add(new PortMeta(input.getId(), input.getDataType(), input.isRequired()));
                    }
                    for (IPort output : instance.getOutputPorts()) {
                        outputs.add(new PortMeta(output.getId(), output.getDataType(), false));
                    }
                }
            } catch (Exception ignored) {
                // Keep partial metadata when node instantiation fails.
            }

            metas.add(new NodeMeta(
                    node.ref(),
                    node.typeId(),
                    category == null ? "" : category,
                    node.offsetX(),
                    inputs,
                    outputs
            ));
        }
        return metas;
    }

    private NodeMeta findBestSourceMeta(List<NodeMeta> metas, NodeMeta target, PortMeta targetInput) {
        NodeMeta best = null;
        int bestScore = Integer.MIN_VALUE;
        for (NodeMeta candidate : metas) {
            if (candidate == null || candidate.ref().equals(target.ref())) {
                continue;
            }
            if (isOutputCategory(candidate.category())) {
                continue;
            }
            PortMeta sourcePort = findBestSourcePort(candidate, targetInput);
            if (sourcePort == null) {
                continue;
            }

            int score = 0;
            if (candidate.x() <= target.x()) {
                score += 8;
            }
            if (isInputCategory(candidate.category())) {
                score += 10;
            }
            if (!isOutputCategory(candidate.category()) && isOutputCategory(target.category())) {
                score += 6;
            }
            if (candidate.typeId() != null && targetInput.id() != null
                    && candidate.typeId().toLowerCase(Locale.ROOT).contains(targetInput.id().replace("input_", "").toLowerCase(Locale.ROOT))) {
                score += 3;
            }

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private PortMeta findBestSourcePort(NodeMeta source, PortMeta targetInput) {
        if (source == null || source.outputs() == null || source.outputs().isEmpty() || targetInput == null) {
            return null;
        }
        PortMeta best = null;
        int bestScore = Integer.MIN_VALUE;
        for (PortMeta output : source.outputs()) {
            if (!shouldAutoWireSourcePort(output)) {
                continue;
            }
            if (!isTypeCompatible(output.dataType(), targetInput.dataType())) {
                continue;
            }
            int score = 0;
            if (output.dataType() == targetInput.dataType()) {
                score += 20;
            }
            if (safeLower(output.id()).contains("output_" + safeLower(targetInput.id()).replace("input_", ""))) {
                score += 6;
            }
            if (score > bestScore) {
                bestScore = score;
                best = output;
            }
        }
        return best;
    }

    private boolean isTypeCompatible(NodeDataType outputType, NodeDataType inputType) {
        if (outputType == null || inputType == null) {
            return false;
        }
        return NodeDataType.isConnectableTo(outputType, inputType);
    }

    private boolean shouldAutoWireInputPort(PortMeta input) {
        if (input == null || !input.required()) {
            return false;
        }
        String inputId = safeLower(input.id());
        String inputType = input.dataType() == null ? "" : safeLower(input.dataType().getId());

        if (containsAny(inputId,
                "color", "block_type", "transparency", "trigger", "notify", "status", "message", "progress")) {
            return false;
        }

        if (containsAny(inputType,
                "geometry", "blocks", "curve", "coordinate", "position", "vector", "integer", "float", "double", "number", "bounding_box")) {
            return true;
        }

        return containsAny(inputId,
                "geometry", "blocks", "curve", "coordinate", "position", "vector", "radius", "height", "width", "depth", "size", "count", "value");
    }

    private boolean shouldAutoWireSourcePort(PortMeta output) {
        if (output == null) {
            return false;
        }
        String outputId = safeLower(output.id());
        return !containsAny(outputId,
                "status", "valid", "notify", "message", "progress", "debug", "log");
    }

    private boolean isInputCategory(String category) {
        return safeLower(category).startsWith("input.") || safeLower(category).startsWith("reference.");
    }

    private boolean isOutputCategory(String category) {
        return safeLower(category).startsWith("output.");
    }

    private String safeLower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    public boolean isRemotePlannerBusy() {
        return session.isRemotePlannerBusy();
    }

    public void cancelRequest() {
        aiAssistantComponent.cancelRemotePlannerRequest();
        NodeCraft.LOGGER.info("[AI_SEND] Remote planner request canceled by user.");
        session.setPlanStatusMessage("Remote planner request canceled.");
        addAiChatMessage("assistant", session.planStatusMessage());
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

    private void logAiDebug(String message, Object... args) {
        if (!ui.aiDebugLoggingEnabled.get()) {
            return;
        }
        NodeCraft.LOGGER.debug(message, args);
    }

    private String resolveEffectiveApiKey() {
        return AiSettingsStore.resolveApiKey(collectAiSettingsData());
    }

    private AiRemotePlanningOrchestrator.RequestSettings collectRemoteRequestSettings() {
        return new AiRemotePlanningOrchestrator.RequestSettings(
                ui.aiApiBaseUrl.get(),
                resolveEffectiveApiKey(),
                ui.aiModel.get(),
                AiProviderModelService.providerStrategyFromIndex(ui.aiProviderStrategyIndex.get(), AiAssistantUiBindings.AI_PROVIDER_STRATEGY_OPTIONS),
                ui.aiSystemPrompt.get(),
                ui.aiMaxOutputTokens.get(),
                ui.aiRequestTimeoutSeconds.get(),
                ui.aiUseSelectionContext.get(),
                ui.aiDebugLoggingEnabled.get(),
                ui.aiIncludePromptPreviewInDebug.get()
        );
    }

    public void maybeAutofillModelByProviderChange(String detectedProviderLabel, String[] suggestedModels) {
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

        String currentModel = ui.aiModel.get();
        boolean shouldAutofill = currentModel == null
                || currentModel.isBlank()
            || !AiProviderModelService.isModelInSuggestions(currentModel, suggestedModels);

        if (shouldAutofill) {
            ui.aiModel.set(suggestedModels[0]);
            aiSettingsStatusMessage = "Provider changed to " + detectedProviderLabel
                    + ", model auto-filled: " + suggestedModels[0];
        }

        aiLastDetectedProviderLabel = detectedProviderLabel;
    }

    public void applyProviderPreset(int index) {
        AiProviderModelService.ProviderPreset[] presets = AiProviderModelService.providerPresets();
        if (presets == null || presets.length == 0) {
            return;
        }

        int safeIndex = Math.max(0, Math.min(presets.length - 1, index));
        AiProviderModelService.ProviderPreset preset = presets[safeIndex];
        if (preset.baseUrl() != null && !preset.baseUrl().isBlank()) {
            ui.aiApiBaseUrl.set(preset.baseUrl());
        }
        ui.aiProviderStrategyIndex.set(AiProviderModelService.indexFromProviderStrategy(
                preset.providerStrategy(),
                AiAssistantUiBindings.AI_PROVIDER_STRATEGY_OPTIONS
        ));

        String[] models = preset.models();
        if (models != null && models.length > 0) {
            String currentModel = ui.aiModel.get();
            if (currentModel == null
                    || currentModel.isBlank()
                    || !AiProviderModelService.isModelInSuggestions(currentModel, models)) {
                ui.aiModel.set(models[0]);
            }
        }

        aiLastDetectedProviderLabel = preset.label();
        aiSettingsStatusMessage = "Provider preset applied: " + preset.label() + ".";
    }

    private AiGraphPlanDslAdapterService.GraphPlan toServiceGraphPlanForHistory(AiGraphPlan plan) {
        if (plan == null) {
            return new AiGraphPlanDslAdapterService.GraphPlan("", List.of(), List.of(), List.of());
        }

        List<AiPlanNode> planNodes = safePlanNodes(plan);
        List<AiPlanConnection> planConnections = safePlanConnections(plan);

        return new AiGraphPlanDslAdapterService.GraphPlan(
                plan.summary(),
                toDslAdapterNodes(planNodes),
                toDslAdapterConnections(planConnections),
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

    public void applyPendingPlan() {
        AiGraphPlan pendingAiPlan = pendingPlan();
        AiPlanValidator.GateResult gate = planValidator.checkBeforeApply(pendingAiPlan);
        if (!gate.allowed()) {
            session.setPlanStatusMessage(gate.rejectionMessage());
            return;
        }

        GraphApplyTarget applyTarget = resolveGraphApplyTarget();
        if (applyTarget == null) {
            session.setPlanStatusMessage("Cannot apply: editor is unavailable.");
            return;
        }

        logAiApplyHistoryContext("before-apply", applyTarget, pendingAiPlan, ui.aiPatchApplyMode.get());

        float[] anchor = resolveAiPlanAnchorPosition(applyTarget);
        List<AiPlanNode> nodesToApply = ui.aiAutoLayoutBeforeApply.get()
                ? buildAutoLayoutNodes(pendingAiPlan)
            : safePlanNodes(pendingAiPlan);
        if (nodesToApply == null) {
            nodesToApply = List.of();
        }
        List<AiPlanConnection> connectionsToApply = safePlanConnections(pendingAiPlan);

        if (ui.aiPatchApplyMode.get()) {
            applyPendingAiPlanPatch(applyTarget, nodesToApply, anchor);
            return;
        }

        List<AiPlanApplyCoordinatorService.PlanNode> applyNodes = toCoordinatorApplyNodes(nodesToApply);
        List<AiPlanApplyCoordinatorService.PlanConnection> applyConnections = toCoordinatorApplyConnections(connectionsToApply);

        AiPlanApplyCoordinatorService.ApplyResult result = AiPlanApplyCoordinatorService.applyExact(
                applyTarget,
                applyNodes,
                applyConnections,
                anchor
        );

        if (result.success()) {
            session.recordApplyResult(result.undoSteps(), false);
        } else {
            session.clearApplyBookkeeping();
        }
        logAiApplyHistoryContext("after-apply-exact", applyTarget, pendingAiPlan, false);

        session.setPlanStatusMessage(result.statusMessage()
                + (result.success() && ui.aiAutoLayoutBeforeApply.get() ? " (auto layout enabled)" : ""));
    }

    private void applyPendingAiPlanPatch(GraphApplyTarget applyTarget, List<AiPlanNode> nodesToApply, float[] anchor) {
        AiGraphPlan pendingAiPlan = pendingPlan();
        if (pendingAiPlan == null) {
            session.setPlanStatusMessage("Patch apply failed: no pending plan available.");
            return;
        }
        if (applyTarget == null) {
            session.setPlanStatusMessage("Patch apply failed: editor is unavailable.");
            return;
        }
        logAiApplyHistoryContext("before-apply-patch", applyTarget, pendingAiPlan, true);
        NodeGraph graph = getNodeGraph();
        if (graph == null) {
            session.setPlanStatusMessage("Patch apply failed: current graph is unavailable.");
            return;
        }
        if (nodesToApply == null) {
            nodesToApply = List.of();
        }
        List<AiPlanConnection> pendingConnections = safePlanConnections(pendingAiPlan);

        List<AiGraphApplyAdapterService.PlanNode> patchNodes = toPatchApplyNodes(nodesToApply);
        List<AiGraphApplyAdapterService.PlanConnection> patchConnections = toPatchApplyConnections(pendingConnections);

        AiGraphApplyAdapterService.PatchPayload payload =
                AiGraphApplyAdapterService.toPatchPayload(patchNodes, patchConnections);
        boolean mergeExistingNodeState = AiIntentAnalysisService.classifyIntent(session.lastSubmittedPrompt()) == UserIntent.MODIFY_PARAM || AiIntentAnalysisService.classifyIntent(session.lastSubmittedPrompt()) == UserIntent.RESTRUCTURE;

        AiGraphApplyService.ApplyResult result = AiGraphApplyService.applyPatch(
                applyTarget,
                graph,
                payload.nodes(),
                payload.connections(),
                anchor,
                ui.aiPatchRemoveScopedConnections.get(),
                mergeExistingNodeState
        );
        if (result.success()) {
            // Patch apply records one aggregate AI_PATCH action; undo should be one step.
            session.recordApplyResult(1, true);
        } else {
            session.clearApplyBookkeeping();
        }
        logAiApplyHistoryContext("after-apply-patch", applyTarget, pendingAiPlan, true);
        String patchModeDetail = mergeExistingNodeState
                ? " (parameter merge mode)"
                : " (state replace mode)";
        session.setPlanStatusMessage(result.statusMessage()
                + patchModeDetail
                + (result.success() && ui.aiAutoLayoutBeforeApply.get() ? " (auto layout enabled for new nodes)" : ""));
    }

    public void undoLastApply() {
        String undoUnavailableReason = resolveUndoUnavailableReason();
        if (!undoUnavailableReason.isBlank()) {
            session.setPlanStatusMessage("Undo unavailable: " + undoUnavailableReason);
            if (session.lastApplyWasPatch()) {
                session.clearApplyBookkeeping();
            }
            return;
        }

        GraphApplyTarget applyTarget = resolveGraphApplyTarget();
        if (applyTarget == null) {
            session.setPlanStatusMessage("Undo failed: editor is unavailable.");
            session.clearApplyBookkeeping();
            return;
        }

        logAiApplyHistoryContext("before-undo-last-ai-apply", applyTarget, pendingPlan(), session.lastApplyWasPatch());

        int expectedUndoSteps = session.lastUndoStepCount();
        int undone = session.lastApplyWasPatch()
            ? (applyTarget.undo() ? 1 : 0)
            : AiPlanApplyCoordinatorService.undo(applyTarget, expectedUndoSteps);

        if (undone == expectedUndoSteps) {
            session.setPlanStatusMessage("Undo completed: " + undone + " / " + expectedUndoSteps + " steps.");
        } else {
            session.setPlanStatusMessage("Undo incomplete: " + undone + " / " + expectedUndoSteps
                    + " steps. History may have changed since apply.");
        }
        session.clearApplyBookkeeping();
        logAiApplyHistoryContext("after-undo-last-ai-apply", applyTarget, pendingPlan(), false);
    }

    private GraphApplyTarget resolveGraphApplyTarget() {
        return GraphApplyTargetResolver.resolve();
    }

    private void logAiApplyHistoryContext(String phase, GraphApplyTarget applyTarget, AiGraphPlan plan, boolean patchMode) {
        if (applyTarget == null) {
            NodeCraft.LOGGER.info("[AI_APPLY_TRACE] phase={}, editorAvailable=false", phase);
            return;
        }

        GraphApplyHistoryView history = applyTarget.getApplyHistoryView();
        int undoStackSize = history.undoStackSize();
        int redoStackSize = history.redoStackSize();
        Object topActionType = history.undoTopActionType();
        boolean topIsAiPatch = history.isUndoTopAiPatch();

        List<AiPlanNode> nodes = safePlanNodes(plan);
        List<AiPlanConnection> connections = safePlanConnections(plan);

        NodeCraft.LOGGER.info(
                "[AI_APPLY_TRACE] phase={}, patchMode={}, autoLayout={}, removeScoped={}, pendingNodes={}, pendingConnections={}, lastUndoSteps={}, lastWasPatch={}, canUndo={}, canRedo={}, undoStackSize={}, redoStackSize={}, topAction={}, topIsAiPatch={}",
                phase,
                patchMode,
                ui.aiAutoLayoutBeforeApply.get(),
                ui.aiPatchRemoveScopedConnections.get(),
                nodes.size(),
                connections.size(),
                session.lastUndoStepCount(),
                session.lastApplyWasPatch(),
                history.canUndo(),
                history.canRedo(),
                undoStackSize,
                redoStackSize,
                topActionType,
                topIsAiPatch
        );
    }

    private float[] resolveAiPlanAnchorPosition(GraphApplyTarget applyTarget) {
        if (applyTarget == null) {
            return new float[]{0.0f, 0.0f};
        }
        INode selectedNode = getSelectedNode();
        if (selectedNode != null) {
            GraphNodeAnchor selectedPosition = applyTarget.getNodeAnchor(selectedNode.getId());
            if (selectedPosition != null) {
                return new float[]{selectedPosition.x() + 280.0f, selectedPosition.y()};
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

    private List<AiPlanNode> safePlanNodes(AiGraphPlan plan) {
        return plan == null || plan.nodes() == null ? List.of() : plan.nodes();
    }

    private List<AiPlanConnection> safePlanConnections(AiGraphPlan plan) {
        return plan == null || plan.connections() == null ? List.of() : plan.connections();
    }

    private List<AiGraphDiffAdapterService.PlanNode> toDiffPlanNodes(List<AiPlanNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<AiGraphDiffAdapterService.PlanNode> result = new ArrayList<>(nodes.size());
        for (AiPlanNode node : nodes) {
            result.add(new AiGraphDiffAdapterService.PlanNode(node.ref(), node.typeId(), node.nodeState()));
        }
        return result;
    }

    private List<AiGraphDiffAdapterService.PlanConnection> toDiffPlanConnections(List<AiPlanConnection> connections) {
        if (connections == null || connections.isEmpty()) {
            return List.of();
        }
        List<AiGraphDiffAdapterService.PlanConnection> result = new ArrayList<>(connections.size());
        for (AiPlanConnection connection : connections) {
            result.add(new AiGraphDiffAdapterService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }
        return result;
    }

    private List<AiGraphPlanDslAdapterService.PlanNode> toDslAdapterNodes(List<AiPlanNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<AiGraphPlanDslAdapterService.PlanNode> result = new ArrayList<>(nodes.size());
        for (AiPlanNode node : nodes) {
            result.add(new AiGraphPlanDslAdapterService.PlanNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    node.nodeState()
            ));
        }
        return result;
    }

    private List<AiGraphPlanDslAdapterService.PlanConnection> toDslAdapterConnections(List<AiPlanConnection> connections) {
        if (connections == null || connections.isEmpty()) {
            return List.of();
        }
        List<AiGraphPlanDslAdapterService.PlanConnection> result = new ArrayList<>(connections.size());
        for (AiPlanConnection connection : connections) {
            result.add(new AiGraphPlanDslAdapterService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }
        return result;
    }

    private List<AiPlanApplyCoordinatorService.PlanNode> toCoordinatorApplyNodes(List<AiPlanNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<AiPlanApplyCoordinatorService.PlanNode> result = new ArrayList<>(nodes.size());
        for (AiPlanNode node : nodes) {
            result.add(new AiPlanApplyCoordinatorService.PlanNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    node.nodeState()
            ));
        }
        return result;
    }

    private List<AiPlanApplyCoordinatorService.PlanConnection> toCoordinatorApplyConnections(List<AiPlanConnection> connections) {
        if (connections == null || connections.isEmpty()) {
            return List.of();
        }
        List<AiPlanApplyCoordinatorService.PlanConnection> result = new ArrayList<>(connections.size());
        for (AiPlanConnection connection : connections) {
            result.add(new AiPlanApplyCoordinatorService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }
        return result;
    }

    private List<AiGraphApplyAdapterService.PlanNode> toPatchApplyNodes(List<AiPlanNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<AiGraphApplyAdapterService.PlanNode> result = new ArrayList<>(nodes.size());
        for (AiPlanNode node : nodes) {
            result.add(new AiGraphApplyAdapterService.PlanNode(
                    node.ref(),
                    node.typeId(),
                    node.offsetX(),
                    node.offsetY(),
                    node.nodeState()
            ));
        }
        return result;
    }

    private List<AiGraphApplyAdapterService.PlanConnection> toPatchApplyConnections(List<AiPlanConnection> connections) {
        if (connections == null || connections.isEmpty()) {
            return List.of();
        }
        List<AiGraphApplyAdapterService.PlanConnection> result = new ArrayList<>(connections.size());
        for (AiPlanConnection connection : connections) {
            result.add(new AiGraphApplyAdapterService.PlanConnection(
                    connection.sourceRef(),
                    connection.sourcePortId(),
                    connection.targetRef(),
                    connection.targetPortId()
            ));
        }
        return result;
    }

    private NodeGraph getNodeGraph() {
        return nodeGraphSupplier.get();
    }

    private GraphNodeAnchor resolveSelectedNodePosition() {
        INode selectedNode = getSelectedNode();
        if (selectedNode == null) {
            return null;
        }

        GraphApplyTarget applyTarget = resolveGraphApplyTarget();
        if (applyTarget == null) {
            return null;
        }

        return applyTarget.getNodeAnchor(selectedNode.getId());
    }

    public INode selectedNode() {
        return getSelectedNode();
    }

    private INode getSelectedNode() {
        UUID selectedNodeId = aiAssistantComponent.getSelectedNodeId();
        if (selectedNodeId == null) {
            return null;
        }

        NodeGraph graph = getNodeGraph();
        if (graph == null) {
            return null;
        }
        return graph.getNode(selectedNodeId);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
