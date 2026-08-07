package com.nodecraft.nodesystem.execution.runtime;

import com.nodecraft.nodesystem.execution.ExecFrontierSnapshot;
import com.nodecraft.nodesystem.execution.ExecutionProfiler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One logical graph run bound to a scheduler generation.
 */
public interface ExecutionSession {
    UUID sessionId();

    long generation();

    ExecutionMode mode();

    CancellationToken cancellation();

    CompletableFuture<Boolean> result();

    @Nullable
    ExecutionProfiler.Profile lastProfile();

    default ExecFrontierSnapshot execFrontierSnapshot() {
        return ExecFrontierSnapshot.EMPTY;
    }

    default boolean isExecuting() {
        return result() != null && !result().isDone();
    }

    default boolean isPreview() {
        return mode() == ExecutionMode.PREVIEW;
    }
}