package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.INode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight per-node timing collector for graph execution runs.
 */
public final class ExecutionProfiler {

    public record NodeTiming(UUID nodeId, String typeId, String displayName, long durationNanos) {}

    public record Profile(long totalNanos, int executedNodeCount, List<NodeTiming> nodeTimings) {
        public String formatSummary(int topN) {
            StringBuilder builder = new StringBuilder();
            builder.append("executed=").append(executedNodeCount)
                .append(", totalMs=").append(String.format("%.3f", totalNanos / 1_000_000.0d));

            List<NodeTiming> slowest = nodeTimings.stream()
                .sorted(Comparator.comparingLong(NodeTiming::durationNanos).reversed())
                .limit(Math.max(0, topN))
                .toList();
            if (!slowest.isEmpty()) {
                builder.append(", slowest=[");
                for (int i = 0; i < slowest.size(); i++) {
                    NodeTiming timing = slowest.get(i);
                    if (i > 0) {
                        builder.append(", ");
                    }
                    builder.append(timing.displayName())
                        .append("(")
                        .append(String.format("%.3fms", timing.durationNanos() / 1_000_000.0d))
                        .append(")");
                }
                builder.append(']');
            }
            return builder.toString();
        }
    }

    private long startedAtNanos;
    private final List<NodeTiming> nodeTimings = new ArrayList<>();

    public void beginRun() {
        startedAtNanos = System.nanoTime();
        nodeTimings.clear();
    }

    public void recordNode(INode node, long durationNanos) {
        if (node == null || durationNanos < 0L) {
            return;
        }
        nodeTimings.add(new NodeTiming(
            node.getId(),
            node.getTypeId(),
            node.getDisplayName(),
            durationNanos
        ));
    }

    public Profile finish() {
        long totalNanos = Math.max(0L, System.nanoTime() - startedAtNanos);
        return new Profile(totalNanos, nodeTimings.size(), List.copyOf(nodeTimings));
    }
}
