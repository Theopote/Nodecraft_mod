package com.nodecraft.nodesystem.bake;

/**
 * Lifecycle states for a {@link BakeTask}.
 * <p>
 * Transaction model:
 * <ul>
 *   <li>{@link #COMPLETED} — commit (history may be updated)</li>
 *   <li>{@link #CANCELLED} / {@link #TIMED_OUT} — rollback (world restored, history unchanged)</li>
 * </ul>
 */
public enum BakeTaskState {
    QUEUED,
    RUNNING,
    COMPLETED,
    CANCELLING,
    ROLLING_BACK,
    CANCELLED,
    FAILED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == COMPLETED
            || this == CANCELLED
            || this == TIMED_OUT
            || this == FAILED;
    }

    public boolean isAbort() {
        return this == CANCELLED || this == TIMED_OUT || this == FAILED;
    }

    public String displayName() {
        return switch (this) {
            case QUEUED -> "Queued";
            case RUNNING -> "Running";
            case COMPLETED -> "Completed";
            case CANCELLING -> "Cancelling";
            case ROLLING_BACK -> "Rolling Back";
            case CANCELLED -> "Cancelled";
            case FAILED -> "Failed";
            case TIMED_OUT -> "Timed Out";
        };
    }
}
