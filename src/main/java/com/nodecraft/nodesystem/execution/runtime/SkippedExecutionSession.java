package com.nodecraft.nodesystem.execution.runtime;

import com.nodecraft.nodesystem.execution.ExecutionProfiler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Placeholder session returned when a preview submit is skipped (e.g. manual Run is active).
 */
final class SkippedExecutionSession implements ExecutionSession {
    private final UUID sessionId = UUID.randomUUID();
    private final ExecutionMode mode;
    private final long generation;
    private final CancellationToken cancellation;
    private final CompletableFuture<Boolean> result = CompletableFuture.completedFuture(false);

    SkippedExecutionSession(ExecutionMode mode, long generation, CancellationToken cancellation) {
        this.mode = mode;
        this.generation = generation;
        this.cancellation = cancellation;
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
        return null;
    }

    @Override
    public boolean isExecuting() {
        return false;
    }
}
