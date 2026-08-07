package com.nodecraft.nodesystem.execution.runtime;

import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Long-lived execution coordinator. Editor auto-preview and manual Run use {@link #client()}.
 */
public interface NodeExecutionScheduler {
    ExecutionSession submit(
            NodeGraph graph,
            @Nullable ExecutionContext context,
            ExecutionPlan plan,
            long generation
    );

    void cancelPreview();

    void cancelManual();

    Optional<ExecutionSession> activePreview();

    Optional<ExecutionSession> activeManual();

    default boolean isBusy() {
        return activePreview().map(ExecutionSession::isExecuting).orElse(false)
                || activeManual().map(ExecutionSession::isExecuting).orElse(false);
    }

    static NodeExecutionScheduler client() {
        return ClientNodeExecutionScheduler.getInstance();
    }
}
