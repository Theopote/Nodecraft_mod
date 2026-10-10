package com.nodecraft.gui.ai.compose;

import com.nodecraft.nodesystem.semantic.NodeCapability;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Minimal UCS search state for Composer v1.
 */
public final class AiComposeSearchState implements Comparable<AiComposeSearchState> {

    private final LinkedHashSet<String> typePath;
    private final String frontierTypeId;
    private final String frontierPortKey;
    private final Set<NodeCapability> covered;
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
            double cost,
            List<Step> steps
    ) {
        this.typePath = typePath == null ? new LinkedHashSet<>() : new LinkedHashSet<>(typePath);
        this.frontierTypeId = frontierTypeId;
        this.frontierPortKey = frontierPortKey;
        this.covered = covered == null ? Set.of() : Set.copyOf(covered);
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

    public double cost() {
        return cost;
    }

    public List<Step> steps() {
        return steps;
    }

    public AiComposeSearchState extend(Step step, Set<NodeCapability> nextCovered, double addedCost) {
        LinkedHashSet<String> nextPath = new LinkedHashSet<>(typePath);
        nextPath.add(step.toTypeId());
        List<Step> nextSteps = new ArrayList<>(steps);
        nextSteps.add(step);
        return new AiComposeSearchState(
                nextPath,
                step.toTypeId(),
                step.toPortId(),
                nextCovered,
                cost + addedCost,
                nextSteps
        );
    }

    @Override
    public int compareTo(AiComposeSearchState other) {
        int c = Double.compare(this.cost, other.cost);
        if (c != 0) {
            return c;
        }
        return String.valueOf(this.frontierTypeId).compareToIgnoreCase(String.valueOf(other.frontierTypeId));
    }
}
