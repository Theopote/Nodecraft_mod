package com.nodecraft.gui.components.ai;

import com.nodecraft.gui.ai.AiGraphDiffService;
import com.nodecraft.gui.layout.ImGuiChildScope;
import imgui.ImGui;

import java.util.List;

final class AiAssistantDebugConsoleRenderer {

    private AiAssistantDebugConsoleRenderer() {
    }

    record State(
            String errorCategory,
            int attempts,
            String rawResponse,
            String modelText,
            String requestSnapshot,
            String compactDiagnostics,
            String fullDiagnostics,
            String inputLanguageDetected,
            String normalizedIntentPreview,
            AiGraphDiffService.GraphDiffSummary heuristicDiff,
            AiGraphDiffService.MappedDiffSummary mappedDiff
    ) {
    }

    interface Actions {
        void renderFailureSummarySection();

        void copyRawResponse();

        void copyModelText();

        void copyRequestSnapshot();

        void copyCompactExport();

        void copyFullExport();
    }

    static void renderDebugConsolePopup(State state, Actions actions) {
        int flags = 0;
        if (!ImGui.beginPopupModal("AI Debug Console", flags)) {
            return;
        }

        actions.renderFailureSummarySection();
        ImGui.spacing();

        String categoryText = state.errorCategory() == null || state.errorCategory().isBlank()
                ? "none"
                : state.errorCategory();
        ImGui.textDisabled("Category: " + categoryText + " | Attempts: " + state.attempts());
        ImGui.separator();

        renderRequestDiagnostics(state);
        renderDebugDiffDetails(state);

        if (ImGui.beginTabBar("aiDebugConsoleTabs")) {
            if (ImGui.beginTabItem("Raw Response")) {
                try (ImGuiChildScope scope = new ImGuiChildScope("aiDebugRawBody", 0.0f, 280.0f, true, 0)) {
                    if (scope.isOpen()) {
                        if (state.rawResponse() == null || state.rawResponse().isBlank()) {
                            ImGui.textDisabled("No raw response available.");
                        } else {
                            ImGui.textWrapped(state.rawResponse());
                        }
                    }
                }
                if (ImGui.button("Copy Raw Response")) {
                    actions.copyRawResponse();
                }
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Model Text")) {
                try (ImGuiChildScope scope = new ImGuiChildScope("aiDebugModelTextBody", 0.0f, 280.0f, true, 0)) {
                    if (scope.isOpen()) {
                        if (state.modelText() == null || state.modelText().isBlank()) {
                            ImGui.textDisabled("No extracted model text available.");
                        } else {
                            ImGui.textWrapped(state.modelText());
                        }
                    }
                }
                if (ImGui.button("Copy Model Text")) {
                    actions.copyModelText();
                }
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Request Snapshot")) {
                ImGui.textDisabled("API key is masked for safety.");
                try (ImGuiChildScope scope = new ImGuiChildScope("aiDebugRequestSnapshotBody", 0.0f, 260.0f, true, 0)) {
                    if (scope.isOpen()) {
                        if (state.requestSnapshot() == null || state.requestSnapshot().isBlank()) {
                            ImGui.textDisabled("No request snapshot available.");
                        } else {
                            ImGui.textWrapped(state.requestSnapshot());
                        }
                    }
                }
                if (ImGui.button("Copy Request Snapshot")) {
                    actions.copyRequestSnapshot();
                }
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Export")) {
                try (ImGuiChildScope scope = new ImGuiChildScope("aiDebugExportBody", 0.0f, 260.0f, true, 0)) {
                    if (scope.isOpen()) {
                        ImGui.textDisabled("Preview (compact):");
                        ImGui.textWrapped(state.compactDiagnostics() == null ? "" : state.compactDiagnostics());
                    }
                }
                if (ImGui.button("Copy Compact Export")) {
                    actions.copyCompactExport();
                }
                ImGui.sameLine();
                if (ImGui.button("Copy Full Export")) {
                    actions.copyFullExport();
                }
                ImGui.endTabItem();
            }

            ImGui.endTabBar();
        }

        if (ImGui.button("Close")) {
            ImGui.closeCurrentPopup();
        }

        ImGui.endPopup();
    }

    private static void renderRequestDiagnostics(State state) {
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

    private static void renderDebugDiffDetails(State state) {
        AiGraphDiffService.GraphDiffSummary diff = state.heuristicDiff();
        AiGraphDiffService.MappedDiffSummary mapped = state.mappedDiff();
        if (diff == null && mapped == null) {
            return;
        }
        if (!ImGui.treeNode("Debug diff details")) {
            return;
        }

        if (diff != null && ImGui.treeNode("Heuristic diff")) {
            ImGui.textDisabled("Compared by node type+params signature and typed connection signature.");
            ImGui.text("Potential additions: nodes=" + diff.nodeAdditions() + ", connections=" + diff.connectionAdditions());
            ImGui.text("Potential missing from plan: nodes=" + diff.nodeMissingFromPlan()
                    + ", connections=" + diff.connectionMissingFromPlan());

            renderDiffSamples("Node additions", diff.nodeAdditionSamples());
            renderDiffSamples("Node missing from plan", diff.nodeMissingSamples());
            renderDiffSamples("Connection additions", diff.connectionAdditionSamples());
            renderDiffSamples("Connection missing from plan", diff.connectionMissingSamples());
            ImGui.treePop();
        }

        if (mapped != null && ImGui.treeNode("Mapped diff")) {
            ImGui.textDisabled("Greedy matching by type+params, then type fallback. Estimates reusable vs new nodes.");
            ImGui.text("Reusable matches=" + mapped.reusableNodeMatches()
                    + ", new nodes=" + mapped.newNodesToCreate());
            ImGui.text("Unchanged reused=" + mapped.unchangedReusableNodes()
                    + ", param updates=" + mapped.paramUpdateCandidates());
            ImGui.text("Connection additions=" + mapped.connectionAdditions()
                    + ", connection removal candidates=" + mapped.connectionRemovalCandidates()
                    + ", incoming replacements=" + mapped.incomingReplacementCandidates());

            renderDiffSamples("Node reuse matches", mapped.nodeReuseSamples());
            renderDiffSamples("Node creation candidates", mapped.nodeCreationSamples());
            renderDiffSamples("Param update candidates", mapped.paramUpdateSamples());
            renderDiffSamples("Connection additions", mapped.connectionAdditionSamples());
            renderDiffSamples("Connection removal candidates", mapped.connectionRemovalSamples());
            renderDiffSamples("Incoming replacement candidates", mapped.incomingReplacementSamples());
            ImGui.treePop();
        }

        ImGui.treePop();
    }

    private static void renderDiffSamples(String title, List<String> samples) {
        if (!ImGui.treeNode(title)) {
            return;
        }
        if (samples == null || samples.isEmpty()) {
            ImGui.textDisabled("None");
        } else {
            for (String sample : samples) {
                ImGui.bulletText(sample);
            }
        }
        ImGui.treePop();
    }
}
