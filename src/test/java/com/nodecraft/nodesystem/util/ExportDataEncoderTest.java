package com.nodecraft.nodesystem.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportDataEncoderTest {

    @Test
    void rejectsCyclicJson() {
        List<Object> cyclic = new ArrayList<>();
        cyclic.add(cyclic);

        ExportDataEncoder.Result result = ExportDataEncoder.encodeJson(cyclic, false);
        assertFalse(result.valid());
        assertTrue(result.error().contains("cyclic"));
    }

    @Test
    void rejectsDepthOverflow() {
        Object nested = "leaf";
        for (int i = 0; i < GenerationLimits.MAX_EXPORT_DEPTH + 3; i++) {
            nested = List.of(nested);
        }
        ExportDataEncoder.Result result = ExportDataEncoder.encodeJson(nested, false);
        assertFalse(result.valid());
        assertTrue(result.error().contains("MAX_EXPORT_DEPTH"));
    }

    @Test
    void rejectsOversizedText() {
        String huge = "x".repeat(GenerationLimits.MAX_EXPORT_TEXT_CHARS + 100);
        ExportDataEncoder.Result result = ExportDataEncoder.encodeJson(List.of(huge), false);
        assertFalse(result.valid());
        assertTrue(result.error().contains("MAX_EXPORT_TEXT_CHARS"));
    }

    @Test
    void csvUsesStableHeaderOrder() {
        List<Map<String, Object>> rows = List.of(
            Map.of("b", 1, "a", 2),
            Map.of("a", 3, "c", 4)
        );
        ExportDataEncoder.Result result = ExportDataEncoder.encodeCsv(rows);
        assertTrue(result.valid());
        String firstLine = result.text().lines().findFirst().orElse("");
        assertTrue(firstLine.contains("b") && firstLine.contains("a") && firstLine.contains("c"));
    }

    @Test
    void rejectsTooManyRows() {
        List<Integer> rows = new ArrayList<>(GenerationLimits.MAX_EXPORT_ROWS + 1);
        for (int i = 0; i < GenerationLimits.MAX_EXPORT_ROWS + 1; i++) {
            rows.add(i);
        }
        ExportDataEncoder.Result result = ExportDataEncoder.encodeCsv(rows);
        assertFalse(result.valid());
        assertTrue(result.error().contains("MAX_EXPORT_ROWS"));
    }

    @Test
    void encodesSimpleJson() {
        ExportDataEncoder.Result result = ExportDataEncoder.encodeJson(List.of(1, 2, 3), false);
        assertTrue(result.valid());
        assertTrue(result.text().contains("1"));
    }

    @Test
    void rejectsCyclicMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("self", map);
        ExportDataEncoder.Result result = ExportDataEncoder.encodeJson(map, false);
        assertFalse(result.valid());
        assertTrue(result.error().contains("cyclic"));
    }
}
