package com.nodecraft.gui.ai.compose;

import com.nodecraft.nodesystem.semantic.NodeCapability;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Minimal UCS search state for Composer v1.
 * Tracks covered capabilities and open required structural inputs — not a full CSP.
 */
public final class AiComposeSearchState implements Comparable<AiComposeSearchState> {

    private final LinkedHashSet<String> typePath;
    private final String frontierTypeId;
    private final String frontierPortKey;
    private final Set<NodeCapability> covered;
    private final Set<String> openRequiredInputs;
    private final double cost;
    private final List<Step> steps;

    public record Step(
            String fromTypeId,
            String fromPortId,
            String toTypeId,
            String toPortId,
            String reason,
            boolean converter
    ) {
    }

    public AiComposeSearchState(
            LinkedHashSet<String> typePath,
            String frontierTypeId,
            String frontierPortKey,
            Set<NodeCapability> covered,
            Set<String> openRequiredInputs,
            double cost,
            List<Step> steps
    ) {
        this.typePath = typePath == null ? new LinkedHashSet<>() : new LinkedHashSet<>(typePath);
        this.frontierTypeId = frontierTypeId;
        this.frontierPortKey = frontierPortKey;
        this.covered = covered == null ? Set.of() : Set.copyOf(covered);
        this.openRequiredInputs = openRequiredInputs == null ? Set.of() : Set.copyOf(openRequiredInputs);
        this.cost = cost;
        this.steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public LinkedHashSet<String> typePath() {
        return new LinkedHashSet<>(typePath);
    }

    public String frontierTypeId() {
        return frontierTypeId;
    }

    public String frontierPortKey() {
        return frontierPortKey;
    }

    public Set<NodeCapability> covered() {
        return covered;
    }

    public Set<String> openRequiredInputs() {
        return openRequiredInputs;
    }

    public double cost() {
        return cost;
    }

    public List<Step> steps() {
        return steps;
    }

    public AiComposeSearchState withFrontier(String typeId, String portKey, Set<NodeCapability> nextCovered,
                                             Set<String> openInputs, double nextCost, List<Step> nextSteps) {
        LinkedHashSet<String> nextPath = new LinkedHashSet<>(typePath);
        nextPath.add(typeId);
        return new AiComposeSearchState(nextPath, typeId, portKey, nextCovered, openInputs, nextCost, nextSteps);
    }

    @Override
    public int compareTo(AiComposeSearchState other) {
        int c = Double.compare(this.cost, other.cost);
        if (c != 0) {
            return c;
        }
        int open = Integer.compare(this.openRequiredInputs.size(), other.openRequiredInputs.size());
        if (open != 0) {
            return open;
        }
        return String.valueOf(this.frontierTypeId).compareToIgnoreCase(String.valueOf(other.frontierTypeId));
    }
}
