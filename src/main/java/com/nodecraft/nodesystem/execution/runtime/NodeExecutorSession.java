package com.nodecraft.nodesystem.execution.runtime;

import com.nodecraft.nodesystem.execution.ExecFrontierSnapshot;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.execution.ExecutionProfiler;
import com.nodecraft.nodesystem.execution.NodeExecutor;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Session backed by a {@link NodeExecutor} running on a shared worker.
 */
final class NodeExecutorSession implements ExecutionSession {
    private final UUID sessionId = UUID.randomUUID();
    private final long generation;
    private final ExecutionMode mode;
    private final CancellationToken cancellation;
    private final NodeExecutor executor;
    private final CompletableFuture<Boolean> result;

    NodeExecutorSession(
            NodeGraph graph,
            @Nullable ExecutionContext context,
            ExecutionPlan plan,
            long generation,
            CancellationToken cancellation,
            ExecutorService sharedWorker
    ) {
        this.generation = generation;
        this.mode = plan.mode();
        this.cancellation = cancellation;
        this.executor = new NodeExecutor(
                graph,
                context,
                plan.scopeNodeIds(),
                plan.options(),
                null,
                cancellation,
                plan.skipOutputExecuteSideEffects(),
                sharedWorker,
                false
        );
        this.result = executor.executeAsync();
    }

    @Override
    public UUID sessionId() {
        return sessionId;
    }

    @Override
    public long generation() {
        return generation;
    }

    @Override
    public ExecutionMode mode() {
        return mode;
    }

    @Override
    public CancellationToken cancellation() {
        return cancellation;
    }

    @Override
    public CompletableFuture<Boolean> result() {
        return result;
    }

    @Override
    public @Nullable ExecutionProfiler.Profile lastProfile() {
        return executor.getLastExecutionProfile();
    }

    @Override
    public ExecFrontierSnapshot execFrontierSnapshot() {
        return executor.getExecFrontierSnapshot();
    }

    @Override
    public boolean isExecuting() {
        return executor.isExecuting();
    }

    void requestCancel() {
        cancellation.cancel();
        executor.stop();
    }

    NodeExecutor executor() {
        return executor;
    }
}