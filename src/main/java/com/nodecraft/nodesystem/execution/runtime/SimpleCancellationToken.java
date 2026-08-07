package com.nodecraft.nodesystem.execution.runtime;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mutable cancellation token shared by a session and its worker.
 */
public final class SimpleCancellationToken implements CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }
}