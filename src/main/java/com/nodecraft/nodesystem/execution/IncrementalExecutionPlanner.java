package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.graph.NodeGraph;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Computes incremental re-execution scopes from graph topology and node dirty events.
 */
public final class IncrementalExecutionPlanner {

    private IncrementalExecutionPlanner() {
    }

    /**
     * Returns the changed node plus every downstream dependent that must recompute.
     *
     * <p>Includes data downstream and, when the graph has exec wires, exec downstream closure
     * so partial preview runs cover exec side-effect chains.</p>
     */
    public static Set<UUID> resolveInvalidationScope(NodeGraph graph, UUID changedNodeId) {
        if (graph == null || changedNodeId == null) {
            return Set.of();
        }
        return Set.copyOf(graph.getDirtyImpactNodeIds(changedNodeId));
    }

    /**
     * Merges invalidation scopes for multiple dirty nodes.
     */
    public static Set<UUID> resolveInvalidationScope(NodeGraph graph, Collection<UUID> changedNodeIds) {
        if (graph == null || changedNodeIds == null || changedNodeIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> merged = new LinkedHashSet<>();
        for (UUID changedNodeId : changedNodeIds) {
            if (changedNodeId != null) {
                merged.addAll(graph.getDirtyImpactNodeIds(changedNodeId));
            }
        }
        return Set.copyOf(merged);
    }
}
