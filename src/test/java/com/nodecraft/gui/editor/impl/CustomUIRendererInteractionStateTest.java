package com.nodecraft.gui.editor.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomUIRendererInteractionStateTest {

    private CustomUIRenderer renderer;
    private UUID nodeA;
    private UUID nodeB;

    @BeforeEach
    void setUp() {
        renderer = new CustomUIRenderer(null);
        nodeA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        nodeB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        renderer.beginInteractionFrame();
    }

    @Test
    void activeWidgetOnFirstNodeSurvivesSecondNodeRender() {
        renderer.mergeInteractionSnapshot(nodeA, true, true, true, false);
        assertTrue(renderer.isCustomUIWidgetActive());
        assertEquals(nodeA, renderer.getActiveWidgetNodeId());

        renderer.mergeInteractionSnapshot(nodeB, true, false, false, false);

        assertTrue(renderer.isCustomUIWidgetActive(), "Active slider on node A must not be cleared by node B");
        assertEquals(nodeA, renderer.getActiveWidgetNodeId());
    }

    @Test
    void hoverOnSecondNodeUpdatesWhenNothingActive() {
        renderer.mergeInteractionSnapshot(nodeA, true, false, false, false);
        assertEquals(nodeA, renderer.getCustomUIHoveredEmptyNodeId());

        renderer.mergeInteractionSnapshot(nodeB, true, true, false, false);

        assertFalse(renderer.isCustomUIWidgetActive());
        assertTrue(renderer.isCustomUIWidgetHovered(nodeB));
    }

    @Test
    void beginInteractionFrameClearsPriorState() {
        renderer.mergeInteractionSnapshot(nodeA, true, true, true, false);
        renderer.beginInteractionFrame();
        assertFalse(renderer.isCustomUIWidgetActive());
    }
}
