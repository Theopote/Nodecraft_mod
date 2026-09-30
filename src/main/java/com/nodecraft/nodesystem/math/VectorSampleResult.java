package com.nodecraft.nodesystem.math;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.List;

/**
 * Result of a vector sampling operation under the Random v2 valid/vector contract.
 * When {@link #valid()} is false, {@link #vector()} is {@code null}.
 */
public record VectorSampleResult(boolean valid, @Nullable Vector3d vector, @Nullable String error) {

    public static VectorSampleResult ok(Vector3d vector) {
        return new VectorSampleResult(true, vector, "");
    }

    public static VectorSampleResult invalid(String error) {
        return new VectorSampleResult(false, null, error == null ? "" : error);
    }

    /**
     * Transactional batch vector sampling result.
     * When {@link #valid()} is false, {@link #vectors()} is empty.
     */
    public record ListResult(boolean valid, List<Vector3d> vectors, @Nullable String error) {
        public static ListResult ok(List<Vector3d> vectors) {
            return new ListResult(true, List.copyOf(vectors), "");
        }

        public static ListResult invalid(String error) {
            return new ListResult(false, Collections.emptyList(), error == null ? "" : error);
        }
    }
}
