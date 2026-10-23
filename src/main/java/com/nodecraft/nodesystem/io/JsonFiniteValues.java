package com.nodecraft.nodesystem.io;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes graph snapshots Gson/JSON-safe by replacing non-finite floats with {@code null}.
 * <p>
 * Standard JSON (and Gson by default) rejects {@code NaN} / {@code ±Infinity}. Node state may still
 * use those values in-memory (e.g. optional unbounded bounds); persist and history deep-copy must
 * not write them as numbers.
 */
public final class JsonFiniteValues {

    private JsonFiniteValues() {
    }

    /**
     * Deep-copies {@code value}, replacing non-finite {@link Double}/{@link Float} with {@code null}.
     * Other values are returned as-is when already JSON-safe (no structural copy needed).
     */
    @SuppressWarnings("unchecked")
    public static @Nullable Object sanitize(@Nullable Object value) {
        return sanitize(value, new IdentityHashMap<>());
    }

    /**
     * Sanitizes each node's {@code state} and non-finite canvas positions in place on a copy-friendly
     * basis: returns the same instance when nothing changes for positions lists that are already safe;
     * node state maps are replaced with sanitized copies when needed.
     */
    public static SavedGraph sanitizeGraph(@Nullable SavedGraph graph) {
        if (graph == null) {
            return null;
        }
        IdentityHashMap<Object, Object> seen = new IdentityHashMap<>();
        if (graph.nodes != null) {
            for (SavedNode node : graph.nodes) {
                if (node != null) {
                    node.state = sanitize(node.state, seen);
                }
            }
        }
        if (graph.nodePositions != null) {
            for (Map.Entry<String, SavedPosition> entry : graph.nodePositions.entrySet()) {
                SavedPosition pos = entry.getValue();
                if (pos == null) {
                    continue;
                }
                if (!Float.isFinite(pos.x) || !Float.isFinite(pos.y)) {
                    entry.setValue(new SavedPosition(
                        Float.isFinite(pos.x) ? pos.x : 0.0f,
                        Float.isFinite(pos.y) ? pos.y : 0.0f
                    ));
                }
            }
        }
        if (graph.subgraphDefinitions != null) {
            for (Map.Entry<String, SavedGraph> entry : graph.subgraphDefinitions.entrySet()) {
                entry.setValue(sanitizeGraph(entry.getValue()));
            }
        }
        return graph;
    }

    /** JSON-safe number: finite Double/Float unchanged; non-finite → {@code null}; other Number unchanged. */
    public static @Nullable Object sanitizeNumber(@Nullable Number number) {
        if (number == null) {
            return null;
        }
        if (number instanceof Double d) {
            return Double.isFinite(d) ? d : null;
        }
        if (number instanceof Float f) {
            return Float.isFinite(f) ? f : null;
        }
        return number;
    }

    private static @Nullable Object sanitize(@Nullable Object value, IdentityHashMap<Object, Object> seen) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return sanitizeNumber(number);
        }
        if (value instanceof Boolean || value instanceof String || value instanceof Character || value instanceof Enum<?>) {
            return value;
        }
        Object prior = seen.get(value);
        if (prior != null) {
            return prior;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            seen.put(value, copy);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    copy.put(key, sanitize(entry.getValue(), seen));
                }
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            seen.put(value, copy);
            for (Object element : list) {
                copy.add(sanitize(element, seen));
            }
            return copy;
        }
        return value;
    }
}
