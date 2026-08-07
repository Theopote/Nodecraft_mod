package com.nodecraft.nodesystem.execution.runtime;

final class NoneCancellationToken implements CancellationToken {
    static final NoneCancellationToken INSTANCE = new NoneCancellationToken();

    private NoneCancellationToken() {
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void cancel() {
        // no-op
    }
}