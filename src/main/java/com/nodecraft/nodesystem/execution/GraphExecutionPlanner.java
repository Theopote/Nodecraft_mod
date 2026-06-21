package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a static execution plan for a DAG dataflow graph.
 *
 * <p>Topological order is the serial schedule used by {@link NodeExecutor}.
 * {@linkplain ExecutionPlan#levels() Levels} group nodes whose port dependencies are
 * satisfied at the same depth; nodes within a level could run in parallel once world-thread,
 * context, and node mutability constraints are handled.</p>
 */
public final class GraphExecutionPlanner {

    private GraphExecutionPlanner() {
    }

    public record ExecutionPlan(
        List<INode> topologicalOrder,
        List<List<INode>> levels,
        boolean hasCycle
    ) {
        public static ExecutionPlan cycleFailure() {
            return new ExecutionPlan(List.of(), List.of(), true);
        }

        public int maxParallelWidth() {
            return levels.stream().mapToInt(List::size).max().orElse(0);
        }
    }

    public static ExecutionPlan plan(NodeGraph graph) {
        if (graph == null) {
            return ExecutionPlan.cycleFailure();
        }

        List<INode> topologicalOrder = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        Set<UUID> temporaryMarked = new HashSet<>();

        for (INode node : graph.getNodes()) {
            if (!visited.contains(node.getId()) && !visit(node, graph, visited, temporaryMarked, topologicalOrder)) {
                return ExecutionPlan.cycleFailure();
            }
        }

        return new ExecutionPlan(topologicalOrder, buildLevels(graph, topologicalOrder), false);
    }

    private static List<List<INode>> buildLevels(NodeGraph graph, List<INode> topologicalOrder) {
        Map<UUID, Integer> levelById = new HashMap<>();
        for (INode node : topologicalOrder) {
            int level = 0;
            for (NodeGraph.Connection connection : graph.getConnections()) {
                if (!connection.targetNode.getId().equals(node.getId())) {
                    continue;
                }
                Integer predecessorLevel = levelById.get(connection.sourceNode.getId());
                if (predecessorLevel != null) {
                    level = Math.max(level, predecessorLevel + 1);
                }
            }
            levelById.put(node.getId(), level);
        }

        Map<Integer, List<INode>> grouped = new LinkedHashMap<>();
        for (INode node : topologicalOrder) {
            int level = levelById.getOrDefault(node.getId(), 0);
            grouped.computeIfAbsent(level, ignored -> new ArrayList<>()).add(node);
        }
        return List.copyOf(grouped.values());
    }

    private static boolean visit(
        INode node,
        NodeGraph graph,
        Set<UUID> visited,
        Set<UUID> temporaryMarked,
        List<INode> result
    ) {
        if (temporaryMarked.contains(node.getId())) {
            return false;
        }

        if (!visited.contains(node.getId())) {
            temporaryMarked.add(node.getId());
            for (NodeGraph.Connection connection : graph.getConnections()) {
                if (connection.targetNode.getId().equals(node.getId())
                        && !visit(connection.sourceNode, graph, visited, temporaryMarked, result)) {
                    return false;
                }
            }
            temporaryMarked.remove(node.getId());
            visited.add(node.getId());
            result.add(node);
        }

        return true;
    }
}
