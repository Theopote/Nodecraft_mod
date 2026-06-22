package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.ExecLoopNode;
import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.TrackedPreviewPlacementService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes a node graph using either pure dataflow scheduling or exec-edge scheduling.
 *
 * <p>When the graph has no {@link NodeDataType#EXEC} connections, nodes run once in dataflow
 * topological order (legacy behaviour). When exec edges exist, only the exec frontier runs
 * nodes; data inputs are pulled lazily from upstream data ports.</p>
 *
 * <p>Flow-control nodes with exec ports ({@code flow.control.branch}, {@code flow.control.sequence},
 * {@code flow.control.do_once}) route execution via {@link ExecRoutingNode}.</p>
 */
public class NodeExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeExecutor.class);

    private final NodeGraph graph;
    private final ExecutionContext context;
    private final Set<UUID> executionScopeNodeIds;
    private final IncrementalExecutionOptions incrementalOptions;
    private final ExecutionRunLimits runLimits;
    private final Map<UUID, NodeState> nodeStates = new HashMap<>();
    private final AtomicBoolean isExecuting = new AtomicBoolean(false);
    private volatile CompletableFuture<Boolean> executionFuture;
    private final ExecutionProfiler profiler = new ExecutionProfiler();
    private volatile ExecutionProfiler.Profile lastExecutionProfile = new ExecutionProfiler.Profile(0L, 0, List.of());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(new NodeExecutorThreadFactory());

    private enum NodeState {
        NOT_VISITED,
        VISITING,
        VISITED,
        ERROR
    }

    public NodeExecutor(NodeGraph graph, ExecutionContext context) {
        this(graph, context, null);
    }

    public NodeExecutor(NodeGraph graph) {
        this(graph, null, null);
    }

    public NodeExecutor(NodeGraph graph, ExecutionContext context, Set<UUID> executionScopeNodeIds) {
        this(graph, context, executionScopeNodeIds, IncrementalExecutionOptions.defaults());
    }

    public NodeExecutor(
            NodeGraph graph,
            ExecutionContext context,
            Set<UUID> executionScopeNodeIds,
            IncrementalExecutionOptions incrementalOptions
    ) {
        this(graph, context, executionScopeNodeIds, incrementalOptions, ExecutionRunLimits.defaults());
    }

    public NodeExecutor(
            NodeGraph graph,
            ExecutionContext context,
            Set<UUID> executionScopeNodeIds,
            IncrementalExecutionOptions incrementalOptions,
            ExecutionRunLimits runLimits
    ) {
        this.graph = graph;
        this.context = context;
        this.executionScopeNodeIds = executionScopeNodeIds == null ? null : new HashSet<>(executionScopeNodeIds);
        this.incrementalOptions = incrementalOptions == null ? IncrementalExecutionOptions.defaults() : incrementalOptions;
        this.runLimits = runLimits == null ? ExecutionRunLimits.defaults() : runLimits;
    }

    public ExecutionProfiler.Profile getLastExecutionProfile() {
        return lastExecutionProfile;
    }

    public CompletableFuture<Boolean> executeAsync() {
        if (!isExecuting.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(false);
        }

        executionFuture = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                executionFuture.complete(executeGraph());
            } catch (Exception e) {
                LOGGER.error("Node graph execution failed.", e);
                executionFuture.completeExceptionally(e);
            } finally {
                isExecuting.set(false);
                executorService.shutdown();
            }
        }, executorService);
        return executionFuture;
    }

    public boolean executeSync() {
        if (!isExecuting.compareAndSet(false, true)) {
            return false;
        }

        try {
            return executeGraph();
        } catch (Exception e) {
            LOGGER.error("Node graph execution failed.", e);
            return false;
        } finally {
            isExecuting.set(false);
            executorService.shutdown();
        }
    }

    public boolean executeSync(long timeoutMs) {
        CompletableFuture<Boolean> future = executeAsync();
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.error("Node graph execution timed out or failed.", e);
            future.cancel(true);
            return false;
        }
    }

    public void stop() {
        if (isExecuting.get() && executionFuture != null && !executionFuture.isDone()) {
            executionFuture.cancel(true);
            isExecuting.set(false);
            executorService.shutdownNow();
            clearGraphPreviews();
        }
    }

    public boolean isExecuting() {
        return isExecuting.get();
    }

    private boolean executeGraph() {
        nodeStates.clear();
        for (INode node : graph.getNodes()) {
            nodeStates.put(node.getId(), NodeState.NOT_VISITED);
        }

        GraphExecutionPlanner.ExecutionPlan plan = GraphExecutionPlanner.plan(graph);
        if (plan.hasCycle()) {
            LOGGER.error("Node graph contains a data dependency cycle and cannot be executed.");
            clearGraphPreviews();
            lastExecutionProfile = new ExecutionProfiler.Profile(0L, 0, List.of());
            return false;
        }

        ExecutionFlowGraph flowGraph = ExecutionFlowGraph.analyze(graph);
        if (flowGraph.hasExecEdges()) {
            return executeExecFlowGraph(flowGraph);
        }
        return executeDataflowGraph(plan);
    }

    private boolean executeDataflowGraph(GraphExecutionPlanner.ExecutionPlan plan) {
        List<INode> sortedNodes = plan.topologicalOrder();
        boolean partialExecution = executionScopeNodeIds != null && !executionScopeNodeIds.isEmpty();
        ExecutionRunGuard guard = new ExecutionRunGuard(runLimits);
        profiler.beginRun();
        LOGGER.debug(
                "Starting {} dataflow graph execution. nodes={}, levels={}, maxParallelWidth={}, scopeSize={}",
                partialExecution ? "partial" : "full",
                sortedNodes.size(),
                plan.levels().size(),
                plan.maxParallelWidth(),
                partialExecution ? executionScopeNodeIds.size() : 0
        );

        int executedCount = 0;
        Set<UUID> recomputedThisRun = new HashSet<>();
        NodeExecutionCache executionCache = graph.getExecutionCache();
        for (INode node : sortedNodes) {
            if (Thread.currentThread().isInterrupted()) {
                lastExecutionProfile = profiler.finish();
                clearGraphPreviews();
                return false;
            }
            if (!shouldExecuteNode(node, recomputedThisRun, executionCache)) {
                nodeStates.put(node.getId(), NodeState.VISITED);
                continue;
            }
            if (computeNode(node, recomputedThisRun, executionCache, guard)) {
                executedCount++;
            } else {
                return false;
            }
        }

        lastExecutionProfile = profiler.finish();
        LOGGER.debug(
                "Dataflow graph execution finished. executed={}/{}, mode={}, profile={}",
                executedCount,
                sortedNodes.size(),
                partialExecution ? "partial" : "full",
                lastExecutionProfile.formatSummary(5)
        );
        return true;
    }

    private boolean executeExecFlowGraph(ExecutionFlowGraph flowGraph) {
        if (flowGraph.entryNodeIds().isEmpty()) {
            LOGGER.error("Exec graph has edges but no entry nodes (every exec port has an incoming exec wire).");
            lastExecutionProfile = new ExecutionProfiler.Profile(0L, 0, List.of());
            return false;
        }

        boolean partialExecution = executionScopeNodeIds != null && !executionScopeNodeIds.isEmpty();
        ExecutionRunGuard guard = new ExecutionRunGuard(runLimits);
        profiler.beginRun();
        LOGGER.debug(
                "Starting exec-flow graph execution. entries={}, reachable={}, scopeSize={}",
                flowGraph.entryNodeIds().size(),
                flowGraph.reachableExecNodeIds().size(),
                partialExecution ? executionScopeNodeIds.size() : 0
        );

        int[] executedCount = {0};
        Set<UUID> recomputedThisRun = new HashSet<>();
        NodeExecutionCache executionCache = graph.getExecutionCache();
        ArrayDeque<UUID> frontier = new ArrayDeque<>(flowGraph.entryNodeIds());

        if (!drainExecFrontier(frontier, flowGraph, guard, recomputedThisRun, executionCache, () -> executedCount[0]++)) {
            lastExecutionProfile = profiler.finish();
            clearGraphPreviews();
            return false;
        }

        lastExecutionProfile = profiler.finish();
        LOGGER.debug(
                "Exec-flow graph execution finished. executed={}, profile={}",
                executedCount[0],
                lastExecutionProfile.formatSummary(5)
        );
        return true;
    }

    private boolean drainExecFrontier(
            ArrayDeque<UUID> frontier,
            ExecutionFlowGraph flowGraph,
            ExecutionRunGuard guard,
            Set<UUID> recomputedThisRun,
            NodeExecutionCache executionCache,
            Runnable onNodeExecuted
    ) {
        while (!frontier.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }

            UUID nodeId = frontier.removeFirst();
            if (!processExecNode(nodeId, flowGraph, guard, recomputedThisRun, executionCache, frontier, onNodeExecuted)) {
                return false;
            }
        }
        return true;
    }

    private boolean processExecNode(
            UUID nodeId,
            ExecutionFlowGraph flowGraph,
            ExecutionRunGuard guard,
            Set<UUID> recomputedThisRun,
            NodeExecutionCache executionCache,
            ArrayDeque<UUID> frontier,
            Runnable onNodeExecuted
    ) {
        INode node = graph.getNode(nodeId);
        if (node == null) {
            return true;
        }

        Set<UUID> visiting = new HashSet<>();
        ensureDataUpstreamForInputs(node, recomputedThisRun, executionCache, guard, visiting);

        if (!shouldExecuteNode(node, recomputedThisRun, executionCache)) {
            nodeStates.put(node.getId(), NodeState.VISITED);
        } else if (computeNode(node, recomputedThisRun, executionCache, guard)) {
            onNodeExecuted.run();
        } else {
            return false;
        }

        if (node instanceof ExecLoopNode loopNode) {
            return drainExecLoop(loopNode, nodeId, flowGraph, guard, recomputedThisRun, executionCache, onNodeExecuted);
        }

        if (ExecRouting.drainExecPortsSequentially(node)) {
            for (String portId : ExecRouting.orderedActiveExecOutputPortIds(node)) {
                ArrayDeque<UUID> stepFrontier = new ArrayDeque<>();
                for (UUID nextId : flowGraph.execSuccessors(nodeId, portId)) {
                    stepFrontier.addLast(nextId);
                }
                if (!drainExecFrontier(stepFrontier, flowGraph, guard, recomputedThisRun, executionCache, onNodeExecuted)) {
                    return false;
                }
            }
        } else {
            for (String portId : ExecRouting.orderedActiveExecOutputPortIds(node)) {
                for (UUID nextId : flowGraph.execSuccessors(nodeId, portId)) {
                    frontier.addLast(nextId);
                }
            }
        }
        return true;
    }

    private boolean drainExecLoop(
            ExecLoopNode loopNode,
            UUID nodeId,
            ExecutionFlowGraph flowGraph,
            ExecutionRunGuard guard,
            Set<UUID> recomputedThisRun,
            NodeExecutionCache executionCache,
            Runnable onNodeExecuted
    ) {
        String bodyPortId = loopNode.execBodyPortId();
        String completePortId = loopNode.execCompletePortId();
        Set<UUID> bodyEntryIds = new LinkedHashSet<>(flowGraph.execSuccessors(nodeId, bodyPortId));

        int iterationCount = loopNode.execLoopIterationCount();
        for (int iteration = 0; iteration < iterationCount; iteration++) {
            loopNode.prepareExecLoopIteration(iteration);
            resetExecSubtreeVisited(bodyEntryIds, flowGraph);

            ArrayDeque<UUID> bodyFrontier = new ArrayDeque<>(bodyEntryIds);
            if (!drainExecFrontier(bodyFrontier, flowGraph, guard, recomputedThisRun, executionCache, onNodeExecuted)) {
                return false;
            }
        }

        if (completePortId == null) {
            return true;
        }

        ArrayDeque<UUID> completeFrontier = new ArrayDeque<>(flowGraph.execSuccessors(nodeId, completePortId));
        return drainExecFrontier(completeFrontier, flowGraph, guard, recomputedThisRun, executionCache, onNodeExecuted);
    }

    private void resetExecSubtreeVisited(Set<UUID> entryNodeIds, ExecutionFlowGraph flowGraph) {
        if (entryNodeIds == null || entryNodeIds.isEmpty()) {
            return;
        }

        ArrayDeque<UUID> queue = new ArrayDeque<>(entryNodeIds);
        Set<UUID> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            UUID current = queue.removeFirst();
            if (!seen.add(current)) {
                continue;
            }
            nodeStates.put(current, NodeState.NOT_VISITED);
            for (UUID nextId : flowGraph.execSuccessors(current)) {
                if (!seen.contains(nextId)) {
                    queue.addLast(nextId);
                }
            }
        }
    }

    private void ensureDataUpstreamForInputs(
            INode node,
            Set<UUID> recomputedThisRun,
            NodeExecutionCache executionCache,
            ExecutionRunGuard guard,
            Set<UUID> visiting
    ) {
        for (NodeGraph.Connection connection : graph.getConnections()) {
            if (!ExecutionPortKind.isDataConnection(connection)) {
                continue;
            }
            if (!connection.targetNode.getId().equals(node.getId())) {
                continue;
            }

            INode upstream = connection.sourceNode;
            UUID upstreamId = upstream.getId();
            if (nodeStates.get(upstreamId) == NodeState.VISITED) {
                continue;
            }
            if (!visiting.add(upstreamId)) {
                continue;
            }

            ensureDataUpstreamForInputs(upstream, recomputedThisRun, executionCache, guard, visiting);

            if (!shouldExecuteNode(upstream, recomputedThisRun, executionCache)) {
                nodeStates.put(upstreamId, NodeState.VISITED);
            } else {
                computeNode(upstream, recomputedThisRun, executionCache, guard);
            }
            visiting.remove(upstreamId);
        }
    }

    private boolean computeNode(
            INode node,
            Set<UUID> recomputedThisRun,
            NodeExecutionCache executionCache,
            ExecutionRunGuard guard
    ) {
        Map<String, Object> inputs = collectNodeInputs(node);
        long startedAt = System.nanoTime();
        try {
            if (node instanceof BaseNode baseNode && context != null) {
                if (requiresWorldThread(node)) {
                    context.callOnWorldThread(() -> {
                        baseNode.compute(inputs, context);
                        return null;
                    });
                } else {
                    baseNode.compute(inputs, context);
                }
            } else {
                node.compute(inputs);
            }
            executionCache.record(node);
            recomputedThisRun.add(node.getId());
            profiler.recordNode(node, System.nanoTime() - startedAt);
            guard.recordStep();
            nodeStates.put(node.getId(), NodeState.VISITED);
            return true;
        } catch (Exception e) {
            LOGGER.error("Node {} failed during execution: {}", node.getDisplayName(), e.getMessage(), e);
            nodeStates.put(node.getId(), NodeState.ERROR);
            lastExecutionProfile = profiler.finish();
            clearGraphPreviews();
            return false;
        }
    }

    private void clearGraphPreviews() {
        if (graph == null) {
            return;
        }
        for (INode node : graph.getNodes()) {
            String ownerNodeId = node.getId().toString();
            PreviewManager.hideNodePreviews(ownerNodeId);
            TrackedPreviewPlacementService.getInstance().clearTrackedPreviewAcrossWorlds(ownerNodeId);
        }
    }

    private boolean shouldExecuteNode(INode node, Set<UUID> recomputedThisRun, NodeExecutionCache executionCache) {
        if (executionScopeNodeIds == null || executionScopeNodeIds.isEmpty()) {
            return true;
        }
        if (!executionScopeNodeIds.contains(node.getId())) {
            return false;
        }
        if (node instanceof BaseNode baseNode && baseNode.isDirty()) {
            return true;
        }
        if (incrementalOptions.skipCachedNodesInPartialScope()
                && executionCache.hasValidCachedOutput(node)
                && !requiresRecomputeDueToScopedUpstream(node, recomputedThisRun)) {
            return false;
        }
        return true;
    }

    private boolean requiresRecomputeDueToScopedUpstream(INode node, Set<UUID> recomputedThisRun) {
        for (NodeGraph.Connection connection : graph.getConnections()) {
            if (!ExecutionPortKind.isDataConnection(connection)) {
                continue;
            }
            if (!connection.targetNode.getId().equals(node.getId())) {
                continue;
            }
            UUID sourceId = connection.sourceNode.getId();
            if (!executionScopeNodeIds.contains(sourceId)) {
                continue;
            }
            if (recomputedThisRun.contains(sourceId)) {
                return true;
            }
            NodeState upstreamState = nodeStates.get(sourceId);
            if (upstreamState == null || upstreamState == NodeState.NOT_VISITED) {
                return true;
            }
            if (connection.sourceNode instanceof BaseNode baseSource && baseSource.isDirty()) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> collectNodeInputs(INode node) {
        Map<String, Object> inputs = new HashMap<>();
        Map<String, IPort> inputPortsById = new HashMap<>();
        for (IPort port : node.getInputPorts()) {
            inputPortsById.put(port.getId(), port);
        }

        for (NodeGraph.Connection connection : graph.getConnections()) {
            if (!ExecutionPortKind.isDataConnection(connection)) {
                continue;
            }
            if (connection.targetNode.getId().equals(node.getId())) {
                Object value = connection.sourceNode.getOutput(connection.sourcePort.getId());
                mergeCollectedInput(inputs, inputPortsById.get(connection.targetPort.getId()), value);
            }
        }

        return inputs;
    }

    private void mergeCollectedInput(Map<String, Object> inputs, IPort targetPort, Object value) {
        if (targetPort == null) {
            return;
        }

        String portId = targetPort.getId();
        if (targetPort.allowsMultipleIncomingConnections()) {
            List<Object> values = null;
            Object existing = inputs.get(portId);
            if (existing instanceof List<?> existingList) {
                values = new ArrayList<>(existingList);
            }
            if (values == null) {
                values = new ArrayList<>();
                if (existing != null) {
                    values.add(existing);
                }
            }
            values.add(value);
            inputs.put(portId, values);
            return;
        }

        inputs.put(portId, value);
    }

    static boolean requiresWorldThread(INode node) {
        if (node == null || node.getTypeId() == null) {
            return false;
        }
        String typeId = node.getTypeId();
        return typeId.startsWith("world.")
                || typeId.startsWith("input.context.")
                || typeId.startsWith("output.execute.")
                || typeId.startsWith("output.preview.");
    }

    private static final class NodeExecutorThreadFactory implements ThreadFactory {
        private static int sequence = 1;

        @Override
        public synchronized Thread newThread(@NonNull Runnable runnable) {
            Thread thread = new Thread(runnable, "nodecraft-graph-worker-" + sequence++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
