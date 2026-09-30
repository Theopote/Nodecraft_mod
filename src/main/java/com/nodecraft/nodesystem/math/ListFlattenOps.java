package com.nodecraft.nodesystem.math;

import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Safe bounded flattening for nested {@link List} structures.
 * <p>
 * Null leaf elements are preserved (compatible with Create List driven-null retention).
 * Do not use {@link List#copyOf} for successful results — it rejects nulls.
 */
public final class ListFlattenOps {

    public static final String ERROR_CYCLE_REFERENCE = "cycle_reference";
    public static final String ERROR_DEPTH_EXCEEDED = "depth_exceeded";
    public static final String ERROR_ELEMENT_LIMIT = "element_limit";

    public enum FlattenDepthMode {
        /** Fully flatten within the depth cap; fail if recursion would exceed cap. */
        FULL_FLATTEN,
        /** At depth limit, preserve remainder without recursing into nested lists. */
        PARTIAL_DEPTH
    }

    public record FlattenResult(List<Object> items, boolean valid, String error) {
        /** Null-tolerant immutable snapshot (unlike {@link List#copyOf}). */
        public static FlattenResult ok(List<Object> items) {
            return new FlattenResult(Collections.unmodifiableList(new ArrayList<>(items)), true, "");
        }

        public static FlattenResult invalid(String error) {
            return new FlattenResult(List.of(), false, error == null ? "" : error);
        }
    }

    private ListFlattenOps() {
    }

    public static FlattenResult flatten(List<?> input, int depthLimit, FlattenDepthMode mode) {
        return flatten(input, depthLimit, mode, GenerationLimits.MAX_LIST_ELEMENTS);
    }

    /** Package-private for fast unit/contract tests with a smaller element cap. */
    static FlattenResult flatten(List<?> input, int depthLimit, FlattenDepthMode mode, int elementLimit) {
        List<Object> output = new ArrayList<>();
        IdentityHashMap<List<?>, Boolean> activePath = new IdentityHashMap<>();
        FlattenResult result = flattenRecursive(input, output, 0, depthLimit, mode, activePath, elementLimit);
        if (!result.valid()) {
            return result;
        }
        return FlattenResult.ok(output);
    }

    private static FlattenResult flattenRecursive(
            List<?> input,
            List<Object> output,
            int currentDepth,
            int depthLimit,
            FlattenDepthMode mode,
            IdentityHashMap<List<?>, Boolean> activePath,
            int elementLimit
    ) {
        if (currentDepth > GenerationLimits.MAX_FORMAT_DEPTH) {
            return FlattenResult.invalid(ERROR_DEPTH_EXCEEDED);
        }

        if (activePath.containsKey(input)) {
            return FlattenResult.invalid(ERROR_CYCLE_REFERENCE);
        }
        activePath.put(input, Boolean.TRUE);

        try {
            if (mode == FlattenDepthMode.PARTIAL_DEPTH && depthLimit >= 0 && currentDepth >= depthLimit) {
                return appendItems(input, output, elementLimit);
            }

            for (Object item : input) {
                if (item instanceof List<?> nested) {
                    if (currentDepth + 1 > depthLimit) {
                        return FlattenResult.invalid(ERROR_DEPTH_EXCEEDED);
                    }
                    FlattenResult nestedResult = flattenRecursive(
                            nested, output, currentDepth + 1, depthLimit, mode, activePath, elementLimit);
                    if (!nestedResult.valid()) {
                        return nestedResult;
                    }
                } else {
                    FlattenResult appendResult = appendValue(output, item, elementLimit);
                    if (!appendResult.valid()) {
                        return appendResult;
                    }
                }
            }
            return FlattenResult.ok(output);
        } finally {
            activePath.remove(input);
        }
    }

    private static FlattenResult appendItems(List<?> input, List<Object> output, int elementLimit) {
        for (Object item : input) {
            FlattenResult appendResult = appendValue(output, item, elementLimit);
            if (!appendResult.valid()) {
                return appendResult;
            }
        }
        return FlattenResult.ok(output);
    }

    private static FlattenResult appendValue(List<Object> output, @Nullable Object value, int elementLimit) {
        if (output.size() >= elementLimit) {
            return FlattenResult.invalid(ERROR_ELEMENT_LIMIT);
        }
        output.add(value);
        return FlattenResult.ok(output);
    }
}
