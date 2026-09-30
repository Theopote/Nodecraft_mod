package com.nodecraft.nodesystem.math;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Result of {@link SequenceOps} range/series generation with explicit validity and error diagnostics.
 */
public record SequenceResult(boolean valid, List<Double> values, String error) {

    public static SequenceResult ok(List<Double> values) {
        return new SequenceResult(true, values, "");
    }

    public static SequenceResult invalid(String error) {
        return new SequenceResult(false, Collections.emptyList(), error == null ? "" : error);
    }

    public @Nullable String errorOrNull() {
        return error == null || error.isEmpty() ? null : error;
    }
}
