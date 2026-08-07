package com.nodecraft.gui.editor.interaction;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorInteractionStateTest {

    @Test
    void tryEnterOnlyAllowsExclusiveModesFromIdle() {
        EditorInteractionState state = new EditorInteractionState();
        assertTrue(state.tryEnter(EditorInteractionMode.DRAGGING_NODE));
        assertEquals(EditorInteractionMode.DRAGGING_NODE, state.getMode());

        assertFalse(state.tryEnter(EditorInteractionMode.BOX_SELECTING));
        assertEquals(EditorInteractionMode.DRAGGING_NODE, state.getMode());

        assertTrue(state.tryEnter(EditorInteractionMode.IDLE));
        assertTrue(state.isIdle());
        assertTrue(state.tryEnter(EditorInteractionMode.CREATING_CONNECTION));
    }

    @Test
    void forceModeBypassesIdleGate() {
        EditorInteractionState state = new EditorInteractionState();
        state.tryEnter(EditorInteractionMode.PANNING_CANVAS);
        state.forceMode(EditorInteractionMode.CREATING_CONNECTION);
        assertEquals(EditorInteractionMode.CREATING_CONNECTION, state.getMode());
        state.resetToIdle();
        assertTrue(state.isIdle());
    }

    @Test
    void selectionPrimaryAndClearSemantics() {
        EditorInteractionState state = new EditorInteractionState();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        state.setPrimarySelectedNodeId(a);
        state.getSelectedNodeIds().add(b);
        assertEquals(a, state.getPrimarySelectedNodeId());
        assertEquals(2, state.getSelectedNodeIds().size());

        state.removeFromSelection(a);
        assertEquals(b, state.getPrimarySelectedNodeId());

        state.clearSelection();
        assertNull(state.getPrimarySelectedNodeId());
        assertFalse(state.hasSelection());
    }
}
