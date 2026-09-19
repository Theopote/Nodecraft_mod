package com.nodecraft.gui.editor.viewport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorViewportStateTest {

    @Test
    void defaultsMatchLegacyEditor() {
        EditorViewportState viewport = new EditorViewportState();
        assertEquals(1.0f, viewport.getZoom());
        assertEquals(0.0f, viewport.getOffsetX());
        assertEquals(0.0f, viewport.getOffsetY());
        assertTrue(viewport.isShowGrid());
    }

    @Test
    void setViewClampsZoomAndStoresOffset() {
        EditorViewportState viewport = new EditorViewportState();
        viewport.setView(99.0f, 12.0f, -8.0f);
        assertEquals(EditorViewportState.MAX_ZOOM, viewport.getZoom());
        assertEquals(12.0f, viewport.getOffsetX());
        assertEquals(-8.0f, viewport.getOffsetY());

        viewport.setZoom(0.01f);
        assertEquals(EditorViewportState.MIN_ZOOM, viewport.getZoom());
    }

    @Test
    void resetViewKeepsGridFlag() {
        EditorViewportState viewport = new EditorViewportState();
        viewport.setView(2.0f, 10.0f, 20.0f);
        viewport.setShowGrid(false);
        viewport.resetView();
        assertEquals(1.0f, viewport.getZoom());
        assertEquals(0.0f, viewport.getOffsetX());
        assertEquals(0.0f, viewport.getOffsetY());
        assertFalse(viewport.isShowGrid());
    }

    @Test
    void screenWorldRoundTrip() {
        EditorViewportState viewport = new EditorViewportState();
        viewport.setView(2.0f, 5.0f, 7.0f);
        float screenX = viewport.worldToScreenX(10.0f, 100.0f);
        float screenY = viewport.worldToScreenY(20.0f, 200.0f);
        assertEquals(10.0f, viewport.screenToWorldX(screenX, 100.0f), 1.0e-5f);
        assertEquals(20.0f, viewport.screenToWorldY(screenY, 200.0f), 1.0e-5f);
    }

    @Test
    void nanZoomFallsBackToDefault() {
        EditorViewportState viewport = new EditorViewportState();
        viewport.setZoom(Float.NaN);
        assertEquals(EditorViewportState.DEFAULT_ZOOM, viewport.getZoom());
    }
}
