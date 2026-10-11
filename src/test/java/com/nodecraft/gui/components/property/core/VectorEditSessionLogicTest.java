package com.nodecraft.gui.components.property.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorEditSessionLogicTest {

    @Test
    void keepsSessionWhenFocusMovesToAnotherAxis() {
        assertFalse(VectorEditSessionLogic.shouldFinishSession(true, true));
    }

    @Test
    void finishesSessionWhenLastAxisDeactivates() {
        assertTrue(VectorEditSessionLogic.shouldFinishSession(false, true));
    }

    @Test
    void doesNotFinishWhileActivelyEditing() {
        assertFalse(VectorEditSessionLogic.shouldFinishSession(true, false));
    }

    @Test
    void doesNotFinishWithoutDeactivation() {
        assertFalse(VectorEditSessionLogic.shouldFinishSession(false, false));
    }
}
