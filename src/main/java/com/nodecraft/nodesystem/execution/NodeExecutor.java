package com.nodecraft.nodesystem.execution;

import com.nodecraft.nodesystem.api.INode;
import com.nodecraft.nodesystem.api.IPort;
import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.graph.NodeGraph;
import com.nodecraft.nodesystem.preview.PreviewManager;
import com.nodecraft.nodesystem.preview.TrackedPreviewPlacementService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 * Executes a node graph as a DAG dataflow engine using static topological order.
 *
 * <p>Each node is evaluated at most once per run in topological order. The
 * {@link ExecutorService} only offloads {@link #executeAsync()} onto a single
 * background worker thread; node bodies still run serially inside that worker.</p>
 *
 * <p>Data dependencies come from port connections; cycles are rejected. Flow control
 * nodes route values in a single pass and do not skip unselected branches.</p>
 *
 * <p>Parallel-ready batches are computed by {@link GraphExecutionPlanner} but are not
 * executed concurrently yet. See {@link #getLastExecutionProfile()} for per-run timing.</p>
 *
 * <p>Partial execution scope: when a scope is provided, only nodes inside that scope
 * are recomputed. Upstream nodes outside the scope reuse their previously computed
 * outputs.</p>
 */
public class NodeExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeExecutor.class);

    private final NodeGraph graph;
    private final ExecutionContext context;
    private final Set<UUID> executionScopeNodeIds;
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
        this.graph = graph;
        this.context = context;
        this.executionScopeNodeIds = executionScopeNodeIds == null ? null : new HashSet<>(executionScopeNodeIds);
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
            LOGGER.error("Node graph contains a cycle and cannot be executed.");
            clearGraphPreviews();
            lastExecutionProfile = new ExecutionProfiler.Profile(0L, 0, List.of());
            return false;
        }

        List<INode> sortedNodes = plan.topologicalOrder();
        boolean partialExecution = executionScopeNodeIds != null && !executionScopeNodeIds.isEmpty();
        profiler.beginRun();
        LOGGER.debug(
                "Starting {} node graph execution. nodes={}, levels={}, maxParallelWidth={}, scopeSize={}",
                partialExecution ? "partial" : "full",
                sortedNodes.size(),
                plan.levels().size(),
                plan.maxParallelWidth(),
                partialExecution ? executionScopeNodeIds.size() : 0
        );

        int executedCount = 0;
        for (INode node : sortedNodes) {
            if (Thread.currentThread().isInterrupted()) {
                lastExecutionProfile = profiler.finish();
                clearGraphPreviews();
                return false;
            }
            if (!shouldExecuteNode(node)) {
                nodeStates.put(node.getId(), NodeState.VISITED);
                continue;
            }

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
                profiler.recordNode(node, System.nanoTime() - startedAt);
                executedCount++;
                nodeStates.put(node.getId(), NodeState.VISITED);
            } catch (Exception e) {
                LOGGER.error("Node {} failed during execution: {}", node.getDisplayName(), e.getMessage(), e);
                nodeStates.put(node.getId(), NodeState.ERROR);
                lastExecutionProfile = profiler.finish();
                clearGraphPreviews();
                return false;
            }
        }

        lastExecutionProfile = profiler.finish();
        LOGGER.debug(
                "Node graph execution finished. executed={}/{}, mode={}, profile={}",
                executedCount,
                sortedNodes.size(),
                partialExecution ? "partial" : "full",
                lastExecutionProfile.formatSummary(5)
        );
        return true;
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

    private boolean shouldExecuteNode(INode node) {
        return executionScopeNodeIds == null || executionScopeNodeIds.isEmpty() || executionScopeNodeIds.contains(node.getId());
    }

    private Map<String, Object> collectNodeInputs(INode node) {
        Map<String, Object> inputs = new HashMap<>();
        Map<String, IPort> inputPortsById = new HashMap<>();
        for (IPort port : node.getInputPorts()) {
            inputPortsById.put(port.getId(), port);
        }

        for (NodeGraph.Connection connection : graph.getConnections()) {
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
