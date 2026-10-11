package com.nodecraft.gui.components.property.core;

/**
 * Thrown when a property / node-state snapshot cannot be isolated from live mutable data.
 * Callers must not fall back to sharing the original reference for undo/reset.
 */
public final class SnapshotIsolationException extends RuntimeException {

    public SnapshotIsolationException(String message) {
        super(message);
    }

    public SnapshotIsolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
