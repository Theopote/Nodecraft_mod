package com.nodecraft.gui.ai;

import java.util.List;

public final class AiPlanDryRunReportService {

    private AiPlanDryRunReportService() {
    }

    public static String buildDryRunReport(
            int plannedNodeCount,
            int plannedConnectionCount,
            AiGraphDiffService.GraphDiffSummary heuristic,
            AiGraphDiffService.MappedDiffSummary mapped
    ) {
        StringBuilder report = new StringBuilder(512);
        report.append("Dry run only (no graph mutation). ")
                .append("Planned nodes=").append(plannedNodeCount)
                .append(", connections=").append(plannedConnectionCount).append(". ")
                .append("Mapped: reusable=").append(mapped.reusableNodeMatches())
                .append(", new=").append(mapped.newNodesToCreate())
                .append(", paramUpdates=").append(mapped.paramUpdateCandidates())
                .append(", connAdd=").append(mapped.connectionAdditions())
                .append(", connRemoveCandidates=").append(mapped.connectionRemovalCandidates())
                .append(", incomingReplaceCandidates=").append(mapped.incomingReplacementCandidates())
                .append(". Heuristic missing: nodes=").append(heuristic.nodeMissingFromPlan())
                .append(", connections=").append(heuristic.connectionMissingFromPlan()).append(".");

        List<String> incomingSamples = mapped.incomingReplacementSamples();
        if (incomingSamples != null && !incomingSamples.isEmpty()) {
            report.append(" Incoming replacements sample: ")
                    .append(String.join(" | ", incomingSamples.subList(0, Math.min(3, incomingSamples.size()))))
                    .append(".");
        }

        return report.toString();
    }
}
