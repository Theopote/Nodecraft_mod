package com.nodecraft.nodesystem.nodes.input.context;

import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.math.NumericInputUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Shared helpers for {@code input.context.*} WORLD_READ nodes.
 */
public final class ContextReadUtils {

    private ContextReadUtils() {
    }

    public static boolean isLiveContextAvailable(@Nullable ExecutionContext context) {
        return context != null
                && context.getWorld() != null
                && context.getPlayer() != null
                && context.getPlayerAccessor() != null;
    }

    /** Clamp raycast max distance to a finite value in {@code [0, 1000]}. */
    public static double sanitizeMaxDistance(double candidate, double previous) {
        double accepted = NumericInputUtils.finiteOrFallback(candidate, previous);
        return Math.max(0.0d, Math.min(1000.0d, accepted));
    }
}
