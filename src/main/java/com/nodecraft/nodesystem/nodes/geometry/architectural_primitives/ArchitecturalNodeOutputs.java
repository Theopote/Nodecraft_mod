package com.nodecraft.nodesystem.nodes.geometry.architectural_primitives;

import com.nodecraft.nodesystem.util.PrimitiveNumericUtils;

import java.util.List;
import java.util.Map;

/**
 * Shared invalid-output policy for architectural nodes:
 * object ports {@code null}, list ports {@code List.of()}, count {@code 0}, error non-blank.
 */
final class ArchitecturalNodeOutputs {

    private ArchitecturalNodeOutputs() {
    }

    static void markInvalid(Map<String, Object> outputValues, String error) {
        outputValues.put("output_valid", false);
        outputValues.put("output_error", (error == null || error.isBlank()) ? "Invalid" : error);
    }

    static void putNull(Map<String, Object> outputValues, String... ids) {
        for (String id : ids) {
            outputValues.put(id, null);
        }
    }

    static void putEmptyLists(Map<String, Object> outputValues, String... ids) {
        for (String id : ids) {
            outputValues.put(id, List.of());
        }
    }

    static void putCount(Map<String, Object> outputValues, String id, int count) {
        outputValues.put(id, count);
    }

    static void putNans(Map<String, Object> outputValues, String... ids) {
        for (String id : ids) {
            outputValues.put(id, Double.NaN);
        }
    }

    static boolean allFinite(double... values) {
        return PrimitiveNumericUtils.allFinite(values);
    }
}
