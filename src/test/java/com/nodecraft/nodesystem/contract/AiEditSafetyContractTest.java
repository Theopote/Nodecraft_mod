package com.nodecraft.nodesystem.contract;

import com.nodecraft.gui.ai.AiRemotePlanningOrchestrator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prompt capability claims must match Apply support for existing-graph edits.
 */
class AiEditSafetyContractTest {

    @Test
    void modifyHintRequiresSelectedStableId() throws Exception {
        String hint = readPrivateHint("MODIFY_PARAM_SYSTEM_HINT");
        assertTrue(hint.contains("Selected node.id") || hint.contains("fullId"));
        assertTrue(hint.toLowerCase(Locale.ROOT).contains("reuse"));
    }

    @Test
    void restructureHintDoesNotPromiseNodeDeleteOrReplace() throws Exception {
        String hint = readPrivateHint("RESTRUCTURE_SYSTEM_HINT");
        String lower = hint.toLowerCase(Locale.ROOT);

        assertTrue(lower.contains("insert"));
        assertTrue(lower.contains("rewire") || lower.contains("reconnect"));
        assertTrue(hint.contains("does not remove it from the canvas"));
        assertFalse(lower.contains("e.g., deleting, replacing"));
        assertFalse(hint.contains("deleting, replacing, or inserting"));
    }

    private static String readPrivateHint(String fieldName) throws Exception {
        Field field = AiRemotePlanningOrchestrator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}
