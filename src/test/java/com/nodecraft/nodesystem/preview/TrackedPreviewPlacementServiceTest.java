package com.nodecraft.nodesystem.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lightweight unit tests for TrackedPreviewPlacementService guard paths.
 * Full thread-safety behavior is covered by {@code NodeCraftGameTest} GameTests.
 */
class TrackedPreviewPlacementServiceTest {

    @Test
    void clearTrackedPreviewReturnsZeroForNullWorld() {
        assertEquals(0, TrackedPreviewPlacementService.getInstance().clearTrackedPreview(null, "node"));
    }

    @Test
    void clearTrackedPreviewReturnsZeroForEmptyNodeId() {
        assertEquals(0, TrackedPreviewPlacementService.getInstance().clearTrackedPreview(null, ""));
    }

    @Test
    void clearTrackedPreviewOnWorldThreadReturnsZeroForNullWorld() {
        assertEquals(
            0,
            TrackedPreviewPlacementService.getInstance().clearTrackedPreviewOnWorldThread(null, "node", null)
        );
    }

    @Test
    void getTrackedCountReturnsZeroForMissingPreview() {
        assertEquals(0, TrackedPreviewPlacementService.getInstance().getTrackedCount(null, "missing"));
    }
}
