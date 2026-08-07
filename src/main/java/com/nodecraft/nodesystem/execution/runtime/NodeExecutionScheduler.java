package com.nodecraft.nodesystem.execution.runtime;

import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.graph.NodeGraph;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Long-lived execution coordinator. Editor auto-preview uses {@link #client()}.
 */
public interface NodeExecutionScheduler {
    ExecutionSession submit(
            NodeGraph graph,
            @Nullable ExecutionContext context,
            ExecutionPlan plan,
            long generation
    );

    void cancelPreview();

    Optional<ExecutionSession> activePreview();

    static NodeExecutionScheduler client() {
        return ClientNodeExecutionScheduler.getInstance();
    }
}