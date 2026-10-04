package com.nodecraft.nodesystem.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugValueFormatterTest {

    @Test
    void formatsPrimitivesAndNull() {
        assertEquals("null", DebugValueFormatter.format(null, DebugValueFormatter.DEFAULT_OPTIONS).text());
        assertEquals("true", DebugValueFormatter.format(true, DebugValueFormatter.DEFAULT_OPTIONS).text());
        assertEquals("42", DebugValueFormatter.format(42, DebugValueFormatter.DEFAULT_OPTIONS).text());
        assertTrue(DebugValueFormatter.format("hi", DebugValueFormatter.DEFAULT_OPTIONS).text().contains("hi"));
    }

    @Test
    void capsListItemsWithoutCallingContainerToString() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            list.add(i);
        }
        DebugValueFormatter.FormatResult result = DebugValueFormatter.format(
            list,
            new DebugValueFormatter.FormatOptions(2_000, 8, 4, false)
        );
        assertTrue(result.truncated());
        assertTrue(result.text().contains("..."));
        assertTrue(result.text().length() <= 2_000);
        // First few items present; far-tail item string must not appear as a leaf
        assertTrue(result.text().contains("0"));
        assertFalse(result.text().contains("9999"));
    }

    @Test
    void respectsCharBudget() {
        String huge = "x".repeat(50_000);
        DebugValueFormatter.FormatResult result = DebugValueFormatter.format(
            huge,
            new DebugValueFormatter.FormatOptions(64, 8, 4, false)
        );
        assertTrue(result.truncated());
        assertTrue(result.text().length() <= 64);
        assertTrue(result.text().endsWith("...") || result.text().contains("..."));
    }

    @Test
    void detectsCycles() {
        List<Object> cyclic = new ArrayList<>();
        cyclic.add("a");
        cyclic.add(cyclic);
        DebugValueFormatter.FormatResult result = DebugValueFormatter.format(
            cyclic,
            new DebugValueFormatter.FormatOptions(512, 16, 8, false)
        );
        assertTrue(result.text().contains("<cycle>") || result.truncated());
    }

    @Test
    void depthCapTruncatesNestedLists() {
        Object nested = "leaf";
        for (int i = 0; i < 12; i++) {
            nested = List.of(nested);
        }
        DebugValueFormatter.FormatResult result = DebugValueFormatter.format(
            nested,
            new DebugValueFormatter.FormatOptions(2_000, 8, 3, false)
        );
        assertTrue(result.truncated());
        assertTrue(result.text().contains("..."));
    }

    @Test
    void prettyAddsNewlinesForContainers() {
        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        DebugValueFormatter.FormatResult pretty = DebugValueFormatter.format(
            map,
            new DebugValueFormatter.FormatOptions(512, 16, 4, true)
        );
        DebugValueFormatter.FormatResult compact = DebugValueFormatter.format(
            map,
            new DebugValueFormatter.FormatOptions(512, 16, 4, false)
        );
        assertTrue(pretty.text().contains("\n"));
        assertFalse(compact.text().contains("\n"));
    }

    @Test
    void typeLabelForList() {
        assertEquals("List (size=2)", DebugValueFormatter.typeLabel(List.of(1, 2)));
        assertEquals("null", DebugValueFormatter.typeLabel(null));
    }
}
