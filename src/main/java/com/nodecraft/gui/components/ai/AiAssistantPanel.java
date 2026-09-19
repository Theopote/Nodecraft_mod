package com.nodecraft.gui.components.ai;

import com.nodecraft.gui.ai.AiAssistantController;
import com.nodecraft.gui.ai.AiAssistantUiBindings;
import com.nodecraft.gui.ai.AiDiagnosticsService;
import com.nodecraft.gui.ai.AiGraphDiffService;
import com.nodecraft.gui.ai.AiIntentAnalysisService;
import com.nodecraft.gui.ai.AiProviderModelService;
import com.nodecraft.gui.components.ai.AiAssistantComponent.AiGraphPlan;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AiAssistantPanel {

    private final AiAssistantComponent aiAssistantComponent;
    private final AiAssistantUiBindings ui;
    private final AiAssistantController controller;
    private int lastRenderedChatCount = 0;

    public AiAssistantPanel(
            AiAssistantComponent aiAssistantComponent,
            Supplier<NodeGraph> nodeGraphSupplier,
            Consumer<String> clipboardCopier
    ) {
        this.aiAssistantComponent = aiAssistantComponent;
        this.ui = new AiAssistantUiBindings();
        this.controller = new AiAssistantController(
                aiAssistantComponent,
                nodeGraphSupplier,
                clipboardCopier,
                ui
        );
    }

    public void onSelectedNodeChanged(INode node) {
        controller.onSelectedNodeChanged(node);
    }

    public void flushSessionStateIfDue() {
        controller.flushSessionStateIfDue();
    }

    public void cleanup() {
        controller.cleanup();
        lastRenderedChatCount = 0;
    }

    public void render() {
        controller.onFrameTick();

        INode selectedNode = resolveSelectedNodeForRender();
        String selectedNodeDisplayName = selectedNode == null ? "" : selectedNode.getDisplayName();
        String selectedNodeTypeId = selectedNode == null ? "" : selectedNode.getTypeId();
        String inputLanguageDetected = AiIntentAnalysisService.detectInputLanguage(ui.aiPromptInput.get());
        String normalizedIntentPreview = AiIntentAnalysisService.buildNormalizedIntentPreview(ui.aiPromptInput.get());
        boolean plannerBusy = controller.isRemotePlannerBusy();
        String streamingPreview = aiAssistantComponent.getRemoteStreamingBuffer();

        lastRenderedChatCount = AiAssistantMainPanelRenderer.renderMainPanel(
                new AiAssistantMainPanelRenderer.State(
                        controller.settingsSummary(),
                        controller.settingsStatusMessage(),
                        controller.hasAiDebugData(),
                        plannerBusy,
                        ui.aiUseSelectionContext,
                        ui.aiIncludeGraphContext,
                        ui.aiIncludePlayerWorldContext,
                        ui.aiIncludeSelectedWorldRegionContext,
                        ui.aiPreviewOnlyMode,
                        ui.aiPatchApplyMode,
                        ui.aiPatchRemoveScopedConnections,
                        ui.aiEnterToSend,
                        inputLanguageDetected,
                        normalizedIntentPreview,
                        streamingPreview,
                        controller.resolveAiRuntimeStageLabel(plannerBusy, streamingPreview),
                        controller.planStatusMessage(),
                        selectedNodeDisplayName,
                        selectedNodeTypeId,
                        ui.aiChatMessages,
                        ui.aiPromptInput,
                        ui.aiEnableRemotePlanner.get(),
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
                        controller.cancelRequest();
                    }

                    @Override
                    public void onQuickPrompt(String text) {
                        controller.setPrompt(text);
                    }

                    @Override
                    public void renderPlanPreviewSection() {
                        renderAiPlanPreviewSection();
                    }

                    @Override
                    public void onSubmitPrompt() {
                        controller.setPlanStatusMessage("Send clicked. Preparing request...");
                        controller.submitPrompt();
                    }

                    @Override
                    public void clearConversation() {
                        controller.clearConversation();
                        lastRenderedChatCount = 0;
                    }
                }
        );

        renderAiSettingsPopup();
        renderAiDebugConsolePopup();
    }

    private void renderAiSettingsPopup() {
        int providerPresetIndex = AiProviderModelService.resolveProviderPresetIndex(ui.aiApiBaseUrl.get());
        String detectedProviderLabel = AiProviderModelService.resolveDetectedProviderLabel(ui.aiApiBaseUrl.get());
        String[] suggestedModels = AiProviderModelService.resolveSuggestedModels(ui.aiApiBaseUrl.get());
        controller.maybeAutofillModelByProviderChange(detectedProviderLabel, suggestedModels);

        AiAssistantSettingsPopupRenderer.renderSettingsPopup(
                new AiAssistantSettingsPopupRenderer.State(
                        ui.aiEnableRemotePlanner,
                        ui.aiApiBaseUrl,
                        ui.aiApiKey,
                        ui.aiModel,
                        new ImInt(providerPresetIndex),
                        AiProviderModelService.providerPresetLabels(),
                        detectedProviderLabel,
                        suggestedModels,
                        ui.aiProviderStrategyIndex,
                        ui.aiSystemPrompt,
                        ui.aiMaxOutputTokens,
                        ui.aiRequestTimeoutSeconds,
                        ui.aiConversationHistoryTurns,
                        ui.aiShowApiKey,
                        ui.aiRememberApiKey,
                        ui.aiAutoLayoutBeforeApply,
                        ui.aiDebugLoggingEnabled,
                        ui.aiIncludePromptPreviewInDebug,
                        controller.settingsPath()
                ),
                new AiAssistantSettingsPopupRenderer.Actions() {
                    @Override
                    public void onProviderPresetSelected(int index) {
                        controller.applyProviderPreset(index);
                    }

                    @Override
                    public void onValidateLocal() {
                        controller.setSettingsStatusMessage(controller.validateSettings());
                    }

                    @Override
                    public void onTestRemoteConnection() {
                        controller.testRemoteConnection();
                    }

                    @Override
                    public void onSaveSettings() {
                        controller.saveSettings();
                    }

                    @Override
                    public void onReloadSettings() {
                        controller.reloadSettings();
                        controller.setSettingsStatusMessage("AI settings reloaded from disk.");
                    }
                }
        );
    }

    private void renderAiDebugConsolePopup() {
        String compactDiagnostics = AiDiagnosticsService.buildAiDiagnosticsExportText(
                aiAssistantComponent, controller.planStatusMessage(), false);
        String fullDiagnostics = AiDiagnosticsService.buildAiDiagnosticsExportText(
                aiAssistantComponent, controller.planStatusMessage(), true);
        AiAssistantComponent.RemotePlannerSnapshot remoteSnapshot = aiAssistantComponent.getRemotePlannerSnapshot();

        AiAssistantDebugConsoleRenderer.renderDebugConsolePopup(
                new AiAssistantDebugConsoleRenderer.State(
                        remoteSnapshot.errorCategory(),
                        remoteSnapshot.attempts(),
                        remoteSnapshot.rawResponse(),
                        remoteSnapshot.modelText(),
                        remoteSnapshot.requestSnapshot(),
                        compactDiagnostics,
                        fullDiagnostics
                ),
                new AiAssistantDebugConsoleRenderer.Actions() {
                    @Override
                    public void renderFailureSummarySection() {
                        AiAssistantComponent.RemotePlannerSnapshot snapshot = aiAssistantComponent.getRemotePlannerSnapshot();
                        AiAssistantFailurePanelRenderer.renderFailureSummaryCard(
                                new AiAssistantFailurePanelRenderer.State(
                                        snapshot.errorCategory(),
                                        snapshot.statusCode(),
                                        snapshot.attempts(),
                                        snapshot.errorMessage(),
                                        controller.lastSubmittedPrompt() != null && !controller.lastSubmittedPrompt().isBlank(),
                                        controller.isRemotePlannerBusy(),
                                        ui.aiEnableRemotePlanner.get()
                                ),
                                new AiAssistantFailurePanelRenderer.Actions() {
                                    @Override
                                    public void retryLastRequest() {
                                        controller.retryLastRequest();
                                    }

                                    @Override
                                    public void increaseTimeoutSeconds(int deltaSeconds) {
                                        controller.increaseTimeoutSeconds(deltaSeconds);
                                    }

                                    @Override
                                    public void togglePlannerMode() {
                                        controller.toggleRemotePlannerEnabled();
                                    }

                                    @Override
                                    public void openAiSettingsPopup() {
                                        ImGui.openPopup("AI Settings");
                                    }

                                    @Override
                                    public void resaveSettings() {
                                        controller.resaveSettings();
                                    }
                                }
                        );
                    }

                    @Override
                    public void copyRawResponse() {
                        controller.copyToClipboard(aiAssistantComponent.getLastRemoteRawResponse());
                        controller.setPlanStatusMessage("Raw response copied to clipboard.");
                    }

                    @Override
                    public void copyModelText() {
                        controller.copyToClipboard(aiAssistantComponent.getLastRemoteModelText());
                        controller.setPlanStatusMessage("Model text copied to clipboard.");
                    }

                    @Override
                    public void copyRequestSnapshot() {
                        controller.copyToClipboard(aiAssistantComponent.getLastRemoteRequestSnapshot());
                        controller.setPlanStatusMessage("Request snapshot copied to clipboard.");
                    }

                    @Override
                    public void copyCompactExport() {
                        controller.copyToClipboard(compactDiagnostics);
                        controller.setPlanStatusMessage("Compact diagnostics exported to clipboard.");
                    }

                    @Override
                    public void copyFullExport() {
                        controller.copyToClipboard(fullDiagnostics);
                        controller.setPlanStatusMessage("Full diagnostics exported to clipboard.");
                    }
                }
        );
    }

    private void renderAiPlanPreviewSection() {
        AiGraphPlan plan = controller.pendingPlan();
        boolean hasPlan = plan != null;
        boolean plannerBusy = controller.isRemotePlannerBusy();

        AiGraphDiffService.GraphDiffSummary heuristicDiff = hasPlan ? controller.buildGraphDiffSummary(plan) : null;
        AiGraphDiffService.MappedDiffSummary mappedDiff = hasPlan ? controller.buildMappedDiffSummary(plan) : null;
        boolean canApply = hasPlan && plan.isValid() && !plan.nodes().isEmpty() && !plannerBusy;
        String applyModeHint = controller.resolveApplyModeHint();
        String undoUnavailableReason = controller.resolveUndoUnavailableReason();
        if (plannerBusy && undoUnavailableReason.isBlank()) {
            undoUnavailableReason = "AI is generating a plan. Wait for completion before undo.";
        }
        boolean canUndoLastAiApply = undoUnavailableReason.isBlank();

        AiAssistantPlanPreviewRenderer.renderPlanPreviewSection(
                new AiAssistantPlanPreviewRenderer.State(
                        hasPlan,
                        hasPlan ? plan.summary() : "",
                        applyModeHint,
                        controller.previewFocusedNodeRef(),
                        controller.previewFocusScrollPending(),
                        hasPlan ? plan.nodes().size() : 0,
                        hasPlan ? plan.connections().size() : 0,
                        hasPlan ? plan.validationErrors() : List.of(),
                        hasPlan ? controller.buildPlannedNodePreviewLines(plan) : List.of(),
                        hasPlan ? controller.buildPlannedConnectionPreviewLines(plan) : List.of(),
                        hasPlan ? plan.nodes() : List.of(),
                        hasPlan ? plan.connections() : List.of(),
                        ui.aiTopologyPreviewState,
                        heuristicDiff,
                        mappedDiff,
                        canApply,
                        canUndoLastAiApply,
                        undoUnavailableReason,
                        controller.planStatusMessage()
                ),
                new AiAssistantPlanPreviewRenderer.Actions() {
                    @Override
                    public void applyPlan() {
                        if (ui.aiPreviewOnlyMode.get()) {
                            controller.dryRunPendingPlan();
                        } else {
                            controller.applyPendingPlan();
                        }
                    }

                    @Override
                    public void dryRunReport() {
                        controller.dryRunPendingPlan();
                    }

                    @Override
                    public void saveAsTemplate() {
                        controller.savePendingPlanAsTemplate();
                    }

                    @Override
                    public void undoLastApply() {
                        controller.undoLastApply();
                    }

                    @Override
                    public void onTopologyNodeSelected(String nodeRef) {
                        controller.focusPreviewNode(nodeRef);
                    }

                    @Override
                    public void onTopologyFocusScrollConsumed() {
                        controller.consumePreviewFocusScroll();
                    }
                }
        );
    }

    private INode resolveSelectedNodeForRender() {
        return controller.selectedNode();
    }
}
