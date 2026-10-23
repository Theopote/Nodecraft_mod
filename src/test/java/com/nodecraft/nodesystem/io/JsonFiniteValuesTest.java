package com.nodecraft.nodesystem.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFiniteValuesTest {

    private final Gson gson = new GsonBuilder().serializeNulls().create();

    @Test
    void sanitizeNumberNullsNonFinite() {
        assertNull(JsonFiniteValues.sanitizeNumber(Double.NEGATIVE_INFINITY));
        assertNull(JsonFiniteValues.sanitizeNumber(Double.POSITIVE_INFINITY));
        assertNull(JsonFiniteValues.sanitizeNumber(Double.NaN));
        assertNull(JsonFiniteValues.sanitizeNumber(Float.NEGATIVE_INFINITY));
        assertEquals(1.5d, JsonFiniteValues.sanitizeNumber(1.5d));
        assertEquals(3, JsonFiniteValues.sanitizeNumber(3));
    }

    @Test
    void sanitizeGraphAllowsGsonWhenStateHadInfinity() {
        SavedGraph graph = new SavedGraph();
        graph.formatVersion = GraphFormatVersion.CURRENT;
        graph.graphName = "inf-state";
        SavedNode node = new SavedNode();
        node.nodeId = "n1";
        node.typeId = "input.numeric.float";
        Map<String, Object> state = new HashMap<>();
        state.put("value", 1.0d);
        state.put("min", Double.NEGATIVE_INFINITY);
        state.put("max", Double.POSITIVE_INFINITY);
        node.state = state;
        graph.nodes = List.of(node);

        SavedGraph sanitized = JsonFiniteValues.sanitizeGraph(graph);
        @SuppressWarnings("unchecked")
        Map<String, Object> sanitizedState = (Map<String, Object>) ((SavedNode) sanitized.nodes.get(0)).state;
        assertNull(sanitizedState.get("min"));
        assertNull(sanitizedState.get("max"));
        assertEquals(1.0d, ((Number) sanitizedState.get("value")).doubleValue(), 0.0d);

        String json = assertDoesNotThrow(() -> gson.toJson(sanitized));
        assertTrue(json.contains("\"value\""));
        assertTrue(json.contains("\"min\":null") || json.contains("\"min\": null"));
        assertTrue(!json.contains("Infinity") && !json.contains("NaN"));
    }
}
