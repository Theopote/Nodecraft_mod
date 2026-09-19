package com.nodecraft.nodesystem.preview;

import com.nodecraft.nodesystem.nodes.output.preview.GeometryViewerNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackedWorldCompatGateTest {

    @AfterEach
    void clearGateProperty() {
        System.clearProperty(TrackedWorldCompatGate.SYSTEM_PROPERTY);
    }

    @Test
    void freezeDefaultsClosed() {
        assertTrue(TrackedWorldCapabilityInventory.FEATURE_FROZEN);
        assertFalse(TrackedWorldCompatGate.isCompatSelectionEnabled());
        assertArrayEquals(
                new PreviewBackend[]{PreviewBackend.GHOST},
                TrackedWorldCompatGate.backendsForEditor(PreviewBackend.GHOST));
        assertEquals(PreviewBackend.GHOST, TrackedWorldCompatGate.sanitizeSelection(PreviewBackend.TRACKED_WORLD));
    }

    @Test
    void legacyTrackedNodeStillSeesBothBackendsUntilMigrated() {
        assertArrayEquals(
                PreviewBackend.values(),
                TrackedWorldCompatGate.backendsForEditor(PreviewBackend.TRACKED_WORLD));
        assertEquals(
                PreviewBackend.TRACKED_WORLD,
                TrackedWorldCompatGate.sanitizeRestored(PreviewBackend.TRACKED_WORLD));
    }

    @Test
    void enablingCompatUnlocksSelection() {
        System.setProperty(TrackedWorldCompatGate.SYSTEM_PROPERTY, "true");
        assertTrue(TrackedWorldCompatGate.isCompatSelectionEnabled());
        assertEquals(PreviewBackend.TRACKED_WORLD, TrackedWorldCompatGate.sanitizeSelection(PreviewBackend.TRACKED_WORLD));
        assertArrayEquals(PreviewBackend.values(), TrackedWorldCompatGate.backendsForEditor(PreviewBackend.GHOST));
    }

    @Test
    void geometryViewerUiSetCoercesWhenFrozen_restoreKeepsLegacy() {
        GeometryViewerNode node = new GeometryViewerNode();
        node.setPreviewBackend(PreviewBackend.TRACKED_WORLD, true);
        assertEquals(PreviewBackend.TRACKED_WORLD, node.getPreviewBackend());

        node.setPreviewBackend(PreviewBackend.GHOST);
        assertEquals(PreviewBackend.GHOST, node.getPreviewBackend());

        node.setPreviewBackend(PreviewBackend.TRACKED_WORLD);
        assertEquals(PreviewBackend.GHOST, node.getPreviewBackend());
    }
}
