package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.graph.NodeGraph;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Exec-edge topology extracted from a {@link NodeGraph}.
 *
 * <p>Data edges are ignored here. When {@link #hasExecEdges()} is false, {@link NodeExecutor}
 * falls back to pure dataflow scheduling.</p>
 */
public final class ExecutionFlowGraph {

    private final boolean hasExecEdges;
    private final Set<UUID> entryNodeIds;
    private final Map<UUID, Map<String, Set<UUID>>> execSuccessorsBySourcePort;

    private ExecutionFlowGraph(
            boolean hasExecEdges,
            Set<UUID> entryNodeIds,
            Map<UUID, Map<String, Set<UUID>>> execSuccessorsBySourcePort
    ) {
        this.hasExecEdges = hasExecEdges;
        this.entryNodeIds = Set.copyOf(entryNodeIds);
        this.execSuccessorsBySourcePort = copySuccessorMap(execSuccessorsBySourcePort);
    }

    public static ExecutionFlowGraph analyze(NodeGraph graph) {
        if (graph == null) {
            return new ExecutionFlowGraph(false, Set.of(), Map.of());
        }

        Map<UUID, Map<String, Set<UUID>>> successorsByPort = new HashMap<>();
        Set<UUID> nodesWithIncomingExec = new HashSet<>();
        Set<UUID> execParticipatingNodes = new HashSet<>();
        boolean hasExecEdges = false;

        for (NodeGraph.Connection connection : graph.getConnections()) {
            if (!ExecutionPortKind.isExecConnection(connection)) {
                continue;
            }
            hasExecEdges = true;
            UUID sourceId = connection.sourceNode.getId();
            UUID targetId = connection.targetNode.getId();
            String sourcePortId = connection.sourcePort.getId();
            execParticipatingNodes.add(sourceId);
            execParticipatingNodes.add(targetId);
            successorsByPort
                    .computeIfAbsent(sourceId, ignored -> new HashMap<>())
                    .computeIfAbsent(sourcePortId, ignored -> new LinkedHashSet<>())
                    .add(targetId);
            nodesWithIncomingExec.add(targetId);
        }

        if (!hasExecEdges) {
            return new ExecutionFlowGraph(false, Set.of(), Map.of());
        }

        Set<UUID> entryNodes = new LinkedHashSet<>();
        for (UUID nodeId : execParticipatingNodes) {
            if (!nodesWithIncomingExec.contains(nodeId)) {
                entryNodes.add(nodeId);
            }
        }

        return new ExecutionFlowGraph(true, entryNodes, successorsByPort);
    }

    public boolean hasExecEdges() {
        return hasExecEdges;
    }

    public Set<UUID> entryNodeIds() {
        return entryNodeIds;
    }

    public Set<UUID> execSuccessors(UUID nodeId, String sourcePortId) {
        if (nodeId == null || sourcePortId == null) {
            return Set.of();
        }
        Map<String, Set<UUID>> byPort = execSuccessorsBySourcePort.get(nodeId);
        if (byPort == null) {
            return Set.of();
        }
        return byPort.getOrDefault(sourcePortId, Set.of());
    }

    public Set<UUID> execSuccessors(UUID nodeId) {
        if (nodeId == null) {
            return Set.of();
        }
        Map<String, Set<UUID>> byPort = execSuccessorsBySourcePort.get(nodeId);
        if (byPort == null || byPort.isEmpty()) {
            return Set.of();
        }
        Set<UUID> merged = new LinkedHashSet<>();
        for (Set<UUID> targets : byPort.values()) {
            merged.addAll(targets);
        }
        return Set.copyOf(merged);
    }

    /**
     * Returns exec nodes reachable from entry nodes (including entries).
     */
    public Set<UUID> reachableExecNodeIds() {
        if (!hasExecEdges) {
            return Set.of();
        }

        Set<UUID> reachable = new LinkedHashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>(entryNodeIds);
        while (!queue.isEmpty()) {
            UUID current = queue.removeFirst();
            if (!reachable.add(current)) {
                continue;
            }
            for (UUID next : execSuccessors(current)) {
                if (!reachable.contains(next)) {
                    queue.addLast(next);
                }
            }
        }
        return Set.copyOf(reachable);
    }

    private static Map<UUID, Map<String, Set<UUID>>> copySuccessorMap(Map<UUID, Map<String, Set<UUID>>> source) {
        Map<UUID, Map<String, Set<UUID>>> copy = new HashMap<>();
        for (Map.Entry<UUID, Map<String, Set<UUID>>> entry : source.entrySet()) {
            Map<String, Set<UUID>> portCopy = new HashMap<>();
            for (Map.Entry<String, Set<UUID>> portEntry : entry.getValue().entrySet()) {
                portCopy.put(portEntry.getKey(), Set.copyOf(portEntry.getValue()));
            }
            copy.put(entry.getKey(), Map.copyOf(portCopy));
        }
        return Map.copyOf(copy);
    }
}
