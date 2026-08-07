package com.nodecraft.nodesystem.execution.runtime;

/**
 * Cooperative cancellation for an {@link ExecutionSession}.
 */
public interface CancellationToken {
    boolean isCancelled();

    void cancel();

    static CancellationToken none() {
        return NoneCancellationToken.INSTANCE;
    }
}