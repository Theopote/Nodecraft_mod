package com.nodecraft.nodesystem.bake;

/**
 * Lifecycle states for a {@link BakeTask}.
 * <p>
 * Transaction model:
 * <ul>
 *   <li>{@link #COMPLETED} — commit (history may be updated)</li>
 *   <li>{@link #CANCELLED} / {@link #TIMED_OUT} — rollback succeeded (world restored, history unchanged)</li>
 *   <li>{@link #ROLLBACK_FAILED} — abort attempted but one or more restores failed; world may be inconsistent</li>
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
    TIMED_OUT,
    /** Abort rollback finished attempting restores, but at least one {@code setBlockState} failed. */
    ROLLBACK_FAILED;

    public boolean isTerminal() {
        return this == COMPLETED
            || this == CANCELLED
            || this == TIMED_OUT
            || this == FAILED
            || this == ROLLBACK_FAILED;
    }

    public boolean isAbort() {
        return this == CANCELLED
            || this == TIMED_OUT
            || this == FAILED
            || this == ROLLBACK_FAILED;
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
            case ROLLBACK_FAILED -> "Rollback Failed";
        };
    }
}
