package com.nodecraft.gui.editor.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoxSelectionRulesTest {

    @Test
    void leftToRightRequiresFullContainment() {
        assertFalse(BoxSelectionRules.isCrossingSelect(10f, 50f));

        // Partial overlap — not selected in window mode
        assertFalse(BoxSelectionRules.nodeHitsSelection(
                0f, 40f, 0f, 40f,
                20f, 60f, 20f, 60f,
                false
        ));

        // Fully inside — selected
        assertTrue(BoxSelectionRules.nodeHitsSelection(
                0f, 100f, 0f, 100f,
                20f, 60f, 20f, 60f,
                false
        ));
    }

    @Test
    void rightToLeftSelectsOnAnyIntersection() {
        assertTrue(BoxSelectionRules.isCrossingSelect(50f, 10f));

        assertTrue(BoxSelectionRules.nodeHitsSelection(
                0f, 40f, 0f, 40f,
                20f, 60f, 20f, 60f,
                true
        ));

        assertFalse(BoxSelectionRules.nodeHitsSelection(
                0f, 10f, 0f, 10f,
                50f, 80f, 50f, 80f,
                true
        ));
    }
}
