package com.nodecraft.nodesystem.execution;

import com.nodecraft.core.exception.NodeExecutionException;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks per-run execution budget and run-local control-flow flags (e.g. Do Once).
 */
public final class ExecutionRunGuard {

    private static final ThreadLocal<ExecutionRunGuard> CURRENT_RUN = new ThreadLocal<>();

    private final ExecutionRunLimits limits;
    private final long startedAtMs;
    private long steps;
    private final Map<String, Boolean> runLocalFlags = new HashMap<>();
    private final Map<String, Integer> runLocalInts = new HashMap<>();

    public ExecutionRunGuard() {
        this(ExecutionRunLimits.defaults());
    }

    public ExecutionRunGuard(ExecutionRunLimits limits) {
        this.limits = limits == null ? ExecutionRunLimits.defaults() : limits;
        this.startedAtMs = System.currentTimeMillis();
    }

    /** Bind the active guard for this thread for the duration of a NodeExecutor run. */
    public static void bindCurrent(@Nullable ExecutionRunGuard guard) {
        if (guard == null) {
            CURRENT_RUN.remove();
        } else {
            CURRENT_RUN.set(guard);
        }
    }

    public static void clearCurrent() {
        CURRENT_RUN.remove();
    }

    /** Active run guard for this thread, or null outside an executor run. */
    public static @Nullable ExecutionRunGuard current() {
        return CURRENT_RUN.get();
    }

    public long steps() {
        return steps;
    }

    public long elapsedMs() {
        return System.currentTimeMillis() - startedAtMs;
    }

    public void recordStep() {
        steps++;
        checkLimits();
    }

    public void checkLimits() {
        if (steps > limits.maxSteps()) {
            throw new NodeExecutionException(
                    "Graph execution exceeded max steps (" + limits.maxSteps() + ")"
            );
        }
        long elapsed = elapsedMs();
        if (elapsed > limits.maxDurationMs()) {
            throw new NodeExecutionException(
                    "Graph execution exceeded max duration (" + limits.maxDurationMs() + " ms, elapsed=" + elapsed + " ms)"
            );
        }
    }

    /** Run-local boolean flag (Do Once gate, etc.). Absent → {@code false}. */
    public boolean getRunLocalFlag(String key) {
        if (key == null) {
            return false;
        }
        return Boolean.TRUE.equals(runLocalFlags.get(key));
    }

    public void setRunLocalFlag(String key, boolean value) {
        if (key == null) {
            return;
        }
        if (value) {
            runLocalFlags.put(key, true);
        } else {
            runLocalFlags.remove(key);
        }
    }

    public void clearRunLocalFlag(String key) {
        if (key != null) {
            runLocalFlags.remove(key);
        }
    }

    /** Run-local integer (While iteration count, etc.). Absent → {@code 0}. */
    public int getRunLocalInt(String key) {
        if (key == null) {
            return 0;
        }
        Integer value = runLocalInts.get(key);
        return value == null ? 0 : value;
    }

    public void setRunLocalInt(String key, int value) {
        if (key == null) {
            return;
        }
        if (value == 0) {
            runLocalInts.remove(key);
        } else {
            runLocalInts.put(key, value);
        }
    }

    public void clearRunLocalInt(String key) {
        if (key != null) {
            runLocalInts.remove(key);
        }
    }
}
