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

    @Test
    void emptyRegionHoverDoesNotMarkWidgetActive() {
        renderer.mergeInteractionSnapshot(nodeA, true, false, false, false);
        assertFalse(renderer.isCustomUIWidgetActive());
        assertEquals(nodeA, renderer.getCustomUIHoveredEmptyNodeId());
        assertFalse(renderer.isCustomUIWidgetHovered(nodeA));
    }

    @Test
    void directDrawAndImGuiPathsShareMergeSemantics() {
        // Direct-draw path records interacted=true as (region, widgetHovered, widgetActive).
        renderer.mergeInteractionSnapshot(nodeA, true, true, true, false);
        // Later ImGui path on another node with only empty hover must not clear active.
        renderer.mergeInteractionSnapshot(nodeB, true, false, false, false);
        assertTrue(renderer.isCustomUIWidgetActive());
        assertEquals(nodeA, renderer.getActiveWidgetNodeId());
    }

    @Test
    void directDrawStaysActiveWhenHeldWithoutValueChange() {
        // Frame 1: drag starts, value changes.
        renderer.applyDirectDrawInteractionState(
                nodeA, true, true, new DirectDrawInteraction(true, true, true), false);
        assertTrue(renderer.isCustomUIWidgetActive());
        assertEquals(nodeA, renderer.getDirectDrawPointerCaptureNodeId());

        // Frame 2: mouse still down, no value delta — must remain active (blocks node drag).
        renderer.beginInteractionFrame();
        renderer.applyDirectDrawInteractionState(
                nodeA, true, true, new DirectDrawInteraction(true, false, false), false);
        assertTrue(renderer.isCustomUIWidgetActive(),
                "Held direct-draw control must stay active when changed=false");
        assertEquals(nodeA, renderer.getActiveWidgetNodeId());
        assertEquals(nodeA, renderer.getDirectDrawPointerCaptureNodeId());

        // Frame 3: mouse released — capture clears, no longer active.
        renderer.beginInteractionFrame();
        renderer.applyDirectDrawInteractionState(
                nodeA, true, false, DirectDrawInteraction.NONE, false);
        assertFalse(renderer.isCustomUIWidgetActive());
        assertEquals(null, renderer.getDirectDrawPointerCaptureNodeId());
    }

    @Test
    void directDrawCaptureSurvivesOtherNodeEmptyHover() {
        renderer.applyDirectDrawInteractionState(
                nodeA, true, true, new DirectDrawInteraction(true, true, true), false);
        renderer.beginInteractionFrame();
        renderer.applyDirectDrawInteractionState(
                nodeA, false, true, new DirectDrawInteraction(false, false, false), false);
        renderer.applyDirectDrawInteractionState(
                nodeB, true, false, DirectDrawInteraction.NONE, false);

        assertTrue(renderer.isCustomUIWidgetActive());
        assertEquals(nodeA, renderer.getActiveWidgetNodeId());
    }
}
