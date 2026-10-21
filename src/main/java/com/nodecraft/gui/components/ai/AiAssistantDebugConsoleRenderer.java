package com.nodecraft.gui.components.ai;

import com.nodecraft.gui.ai.AiGraphDiffService;
import com.nodecraft.gui.layout.ImGuiChildScope;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;

/**
 * Developer diagnostics for the AI remote planner: inspect raw HTTP payloads,
 * extracted model text, request snapshots, graph diffs, and clipboard exports.
 * <p>
 * Opened from the AI Assistant <b>Debug</b> button when debug data is available.
 */
final class AiAssistantDebugConsoleRenderer {

    private static final float WINDOW_WIDTH = 640.0f;
    private static final float WINDOW_HEIGHT = 520.0f;

    private enum ActiveTab {
        RAW,
        MODEL,
        REQUEST,
        EXPORT
    }

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
        ImGui.setNextWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT, ImGuiCond.Appearing);
        ImGui.setNextWindowSizeConstraints(WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_WIDTH, WINDOW_HEIGHT);

        if (!ImGui.beginPopupModal("AI Debug Console", ImGuiWindowFlags.NoResize)) {
            return;
        }

        float footerReserve = ImGui.getFrameHeightWithSpacing()
                + ImGui.getStyle().getItemSpacingY()
                + 1.0f;

        // --- Fixed header (does not scroll away with tabs) ---
        actions.renderFailureSummarySection();
        ImGui.spacing();

        String categoryText = state.errorCategory() == null || state.errorCategory().isBlank()
                ? "none"
                : state.errorCategory();
        AiUiHelper.textWrappedDisabled("Category: " + categoryText + " | Attempts: " + state.attempts());
        renderRequestDiagnostics(state);
        renderDebugDiffDetails(state);
        ImGui.separator();

        // --- Tab strip + content (tabs stay visible; content scrolls) ---
        float tabsAreaHeight = Math.max(140.0f, ImGui.getContentRegionAvailY() - footerReserve);
        ActiveTab activeTab = ActiveTab.RAW;
        try (ImGuiChildScope tabsArea = new ImGuiChildScope(
                "aiDebugTabsArea",
                0.0f,
                tabsAreaHeight,
                false,
                ImGuiWindowFlags.NoScrollbar)) {
            if (tabsArea.isOpen()) {
                ActiveTab selected = renderTabs(state);
                if (selected != null) {
                    activeTab = selected;
                }
            }
        }

        ImGui.separator();
        renderFooter(activeTab, actions);

        ImGui.endPopup();
    }

    private static ActiveTab renderTabs(State state) {
        ActiveTab selected = null;
        if (!ImGui.beginTabBar("aiDebugConsoleTabs")) {
            return null;
        }

        // Leave room for the tab bar itself inside the tabs area.
        float contentHeight = Math.max(100.0f, ImGui.getContentRegionAvailY() - ImGui.getFrameHeightWithSpacing());

        if (ImGui.beginTabItem("Raw Response")) {
            selected = ActiveTab.RAW;
            renderScrollText("aiDebugRawBody", contentHeight, state.rawResponse(), "No raw response available.");
            ImGui.endTabItem();
        }

        if (ImGui.beginTabItem("Model Text")) {
            selected = ActiveTab.MODEL;
            renderScrollText("aiDebugModelTextBody", contentHeight, state.modelText(),
                    "No extracted model text available.");
            ImGui.endTabItem();
        }

        if (ImGui.beginTabItem("Request Snapshot")) {
            selected = ActiveTab.REQUEST;
            AiUiHelper.textWrappedDisabled("API key is masked for safety.");
            float snapshotHeight = Math.max(80.0f, contentHeight - ImGui.getTextLineHeightWithSpacing());
            renderScrollText("aiDebugRequestSnapshotBody", snapshotHeight, state.requestSnapshot(),
                    "No request snapshot available.");
            ImGui.endTabItem();
        }

        if (ImGui.beginTabItem("Export")) {
            selected = ActiveTab.EXPORT;
            try (ImGuiChildScope scope = new ImGuiChildScope(
                    "aiDebugExportBody",
                    0.0f,
                    contentHeight,
                    true,
                    ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
                if (scope.isOpen()) {
                    AiUiHelper.textWrappedDisabled("Preview (compact):");
                    AiUiHelper.textWrapped(state.compactDiagnostics() == null ? "" : state.compactDiagnostics());
                }
            }
            ImGui.endTabItem();
        }

        ImGui.endTabBar();
        return selected;
    }

    private static void renderScrollText(String childId, float height, String text, String emptyHint) {
        try (ImGuiChildScope scope = new ImGuiChildScope(
                childId,
                0.0f,
                height,
                true,
                ImGuiWindowFlags.AlwaysVerticalScrollbar)) {
            if (!scope.isOpen()) {
                return;
            }
            if (text == null || text.isBlank()) {
                AiUiHelper.textWrappedDisabled(emptyHint);
            } else {
                AiUiHelper.textWrapped(text);
            }
        }
    }

    /** Copy action(s) for the active tab + Close, kept on one row when width allows. */
    private static void renderFooter(ActiveTab activeTab, Actions actions) {
        float rowRight = AiUiHelper.contentRowRightEdge();
        boolean first = true;

        switch (activeTab == null ? ActiveTab.RAW : activeTab) {
            case RAW -> first = footerButton(rowRight, first, "Copy Raw Response", actions::copyRawResponse);
            case MODEL -> first = footerButton(rowRight, first, "Copy Model Text", actions::copyModelText);
            case REQUEST -> first = footerButton(rowRight, first, "Copy Request Snapshot", actions::copyRequestSnapshot);
            case EXPORT -> {
                first = footerButton(rowRight, first, "Copy Compact Export", actions::copyCompactExport);
                first = footerButton(rowRight, first, "Copy Full Export", actions::copyFullExport);
            }
        }
        footerButton(rowRight, first, "Close", ImGui::closeCurrentPopup);
    }

    private static boolean footerButton(float rowRight, boolean first, String label, Runnable onClick) {
        float width = ImGui.calcTextSize(label).x + ImGui.getStyle().getFramePaddingX() * 2.0f;
        if (!first) {
            AiUiHelper.sameLineIfFitsInRow(rowRight, width);
        }
        if (ImGui.button(label)) {
            onClick.run();
        }
        return false;
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
        AiUiHelper.textWrappedDisabled("Input language: " + (language == null || language.isBlank() ? "unknown" : language));
        AiUiHelper.textWrappedDisabled("Normalized intent: " + (intent == null || intent.isBlank() ? "general-request" : intent));
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
            AiUiHelper.textWrappedDisabled("Compared by node type+params signature and typed connection signature.");
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
            AiUiHelper.textWrappedDisabled("Greedy matching by type+params, then type fallback. Estimates reusable vs new nodes.");
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
