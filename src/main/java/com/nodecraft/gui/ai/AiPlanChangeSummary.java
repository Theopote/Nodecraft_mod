package com.nodecraft.gui.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * User-facing change summary derived from mapped (preferred) or heuristic graph diffs.
 * Avoids claiming node deletion; canvas-only nodes are labeled as not in this plan.
 */
public final class AiPlanChangeSummary {

    private static final int MAX_SAMPLES_PER_GROUP = 6;

    private AiPlanChangeSummary() {
    }

    public record View(String headline, List<String> detailLines) {
    }

    public static View build(
            AiGraphDiffService.MappedDiffSummary mapped,
            AiGraphDiffService.GraphDiffSummary heuristic
    ) {
        if (mapped != null) {
            return fromMapped(mapped, heuristic);
        }
        if (heuristic != null) {
            return fromHeuristic(heuristic);
        }
        return null;
    }

    static View fromMapped(
            AiGraphDiffService.MappedDiffSummary mapped,
            AiGraphDiffService.GraphDiffSummary heuristic
    ) {
        int connectionChurn = mapped.connectionAdditions()
                + mapped.connectionRemovalCandidates()
                + mapped.incomingReplacementCandidates();
        String headline = "+ " + mapped.newNodesToCreate() + " nodes"
                + " · ~ " + mapped.paramUpdateCandidates() + " updated"
                + " · ↔ " + connectionChurn + " connections"
                + " · keep: " + mapped.unchangedReusableNodes();

        List<String> details = new ArrayList<>();
        appendSamples(details, "New nodes", mapped.nodeCreationSamples());
        appendSamples(details, "Updated", mapped.paramUpdateSamples());
        appendSamples(details, "Kept / reused", mapped.nodeReuseSamples());
        appendSamples(details, "Connection additions", mapped.connectionAdditionSamples());
        appendSamples(details, "Connection removals", mapped.connectionRemovalSamples());
        appendSamples(details, "Incoming replacements", mapped.incomingReplacementSamples());
        appendNotInPlanDetails(details, heuristic);
        return new View(headline, List.copyOf(details));
    }

    static View fromHeuristic(AiGraphDiffService.GraphDiffSummary heuristic) {
        int connectionChurn = heuristic.connectionAdditions() + heuristic.connectionMissingFromPlan();
        String headline = "+ " + heuristic.nodeAdditions() + " nodes"
                + " · ~ 0 updated"
                + " · ↔ " + connectionChurn + " connections"
                + " · keep: 0";

        List<String> details = new ArrayList<>();
        appendSamples(details, "New nodes", heuristic.nodeAdditionSamples());
        appendSamples(details, "Connection additions", heuristic.connectionAdditionSamples());
        appendNotInPlanDetails(details, heuristic);
        return new View(headline, List.copyOf(details));
    }

    private static void appendNotInPlanDetails(
            List<String> details,
            AiGraphDiffService.GraphDiffSummary heuristic
    ) {
        if (heuristic == null || heuristic.nodeMissingFromPlan() <= 0) {
            return;
        }
        details.add(heuristic.nodeMissingFromPlan()
                + " node(s) not in this plan (still on canvas)");
        List<String> samples = heuristic.nodeMissingSamples();
        if (samples == null || samples.isEmpty()) {
            return;
        }
        int limit = Math.min(MAX_SAMPLES_PER_GROUP, samples.size());
        for (int i = 0; i < limit; i++) {
            String sample = samples.get(i);
            if (sample != null && !sample.isBlank()) {
                details.add("not in this plan (still on canvas): " + sample);
            }
        }
    }

    private static void appendSamples(List<String> details, String label, List<String> samples) {
        if (samples == null || samples.isEmpty()) {
            return;
        }
        int limit = Math.min(MAX_SAMPLES_PER_GROUP, samples.size());
        for (int i = 0; i < limit; i++) {
            String sample = samples.get(i);
            if (sample != null && !sample.isBlank()) {
                details.add(label + ": " + sample);
            }
        }
    }
}
