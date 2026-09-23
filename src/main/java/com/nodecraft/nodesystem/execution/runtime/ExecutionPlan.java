package com.nodecraft.nodesystem.execution.runtime;

import com.nodecraft.nodesystem.execution.IncrementalExecutionOptions;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable description of one graph run.
 */
public record ExecutionPlan(
        ExecutionMode mode,
        @Nullable Set<UUID> scopeNodeIds,
        IncrementalExecutionOptions options,
        boolean skipOutputExecuteSideEffects
) {
    public ExecutionPlan {
        mode = mode != null ? mode : ExecutionMode.MANUAL;
        options = options != null ? options : IncrementalExecutionOptions.defaults();
        if (scopeNodeIds != null) {
            scopeNodeIds = Set.copyOf(scopeNodeIds);
        }
    }

    public static ExecutionPlan preview(@Nullable Set<UUID> scopeNodeIds) {
        return new ExecutionPlan(
                ExecutionMode.PREVIEW,
                scopeNodeIds,
                IncrementalExecutionOptions.previewDefaults(),
                true
        );
    }

    public static ExecutionPlan manual(@Nullable Set<UUID> scopeNodeIds) {
        return new ExecutionPlan(
                ExecutionMode.MANUAL,
                scopeNodeIds,
                IncrementalExecutionOptions.defaults(),
                false
        );
    }

    public boolean isPartialScope() {
        return scopeNodeIds != null && !scopeNodeIds.isEmpty();
    }
}