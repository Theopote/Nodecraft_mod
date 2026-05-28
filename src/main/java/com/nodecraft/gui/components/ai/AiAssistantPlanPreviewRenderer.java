package com.nodecraft.gui.components.ai;

import com.nodecraft.gui.ai.AiGraphDiffService;
import imgui.ImGui;
import java.util.List;

final class AiAssistantPlanPreviewRenderer {

    private AiAssistantPlanPreviewRenderer() {
    }

    record State(
            boolean hasPlan,
            String summary,
            int nodeCount,
            int connectionCount,
            List<String> validationErrors,
            List<String> plannedNodeLines,
            List<String> plannedConnectionLines,
            AiGraphDiffService.GraphDiffSummary heuristicDiff,
            AiGraphDiffService.MappedDiffSummary mappedDiff,
            boolean canApply,
            boolean canUndo,
            String statusMessage
    ) {
    }

    interface Actions {
        void applyPlan();

        void dryRunReport();

        void undoLastApply();
    }

    static void renderPlanPreviewSection(State state, Actions actions) {
        ImGui.separator();
        ImGui.text("Plan Preview");

        if (!state.hasPlan()) {
            ImGui.textDisabled("No plan yet. Send a prompt to generate a plan.");
            return;
        }

        ImGui.textWrapped(state.summary() == null ? "" : state.summary());
        ImGui.text("Nodes: " + state.nodeCount() + "  Connections: " + state.connectionCount());

        if (state.validationErrors() != null && !state.validationErrors().isEmpty()) {
            ImGui.textColored(1.0f, 0.45f, 0.35f, 1.0f, "Validation errors:");
            for (String error : state.validationErrors()) {
                ImGui.bulletText(error);
            }
        }

        if (ImGui.treeNode("Planned Nodes")) {
            if (state.plannedNodeLines() == null || state.plannedNodeLines().isEmpty()) {
                ImGui.textDisabled("None");
            } else {
                for (String line : state.plannedNodeLines()) {
                    ImGui.bulletText(line);
                }
            }
            ImGui.treePop();
        }

        if (ImGui.treeNode("Planned Connections")) {
            if (state.plannedConnectionLines() == null || state.plannedConnectionLines().isEmpty()) {
                ImGui.textDisabled("None");
            } else {
                for (String line : state.plannedConnectionLines()) {
                    ImGui.bulletText(line);
                }
            }
            ImGui.treePop();
        }

        AiGraphDiffService.GraphDiffSummary diff = state.heuristicDiff();
        if (diff != null && ImGui.treeNode("Graph Diff (Heuristic)")) {
            ImGui.textDisabled("Compared by node type+params signature and typed connection signature.");
            ImGui.text("Potential additions: nodes=" + diff.nodeAdditions() + ", connections=" + diff.connectionAdditions());
            ImGui.text("Potential missing from plan: nodes=" + diff.nodeMissingFromPlan() + ", connections=" + diff.connectionMissingFromPlan());

            renderDiffSamples("Node additions", diff.nodeAdditionSamples());
            renderDiffSamples("Node missing from plan", diff.nodeMissingSamples());
            renderDiffSamples("Connection additions", diff.connectionAdditionSamples());
            renderDiffSamples("Connection missing from plan", diff.connectionMissingSamples());
            ImGui.treePop();
        }

        AiGraphDiffService.MappedDiffSummary mapped = state.mappedDiff();
        if (mapped != null && ImGui.treeNode("Mapped Diff (Preview)")) {
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

        if (!state.canApply()) ImGui.beginDisabled();
        if (ImGui.button("Apply Plan")) {
            actions.applyPlan();
        }
        if (!state.canApply()) ImGui.endDisabled();

        ImGui.sameLine();
        if (!state.canApply()) ImGui.beginDisabled();
        if (ImGui.button("Dry Run Report")) {
            actions.dryRunReport();
        }
        if (!state.canApply()) ImGui.endDisabled();

        ImGui.sameLine();
        if (!state.canUndo()) ImGui.beginDisabled();
        if (ImGui.button("Undo Last AI Apply")) {
            actions.undoLastApply();
        }
        if (!state.canUndo()) ImGui.endDisabled();

        if (state.statusMessage() != null && !state.statusMessage().isBlank()) {
            ImGui.textWrapped(state.statusMessage());
        }
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
