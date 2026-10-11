package com.nodecraft.nodesystem.preview;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewManagerEmptyGraceTest {

    @AfterEach
    void tearDown() {
        PreviewManager.clearEmptyInputGrace();
    }

    @Test
    void clearEmptyInputGraceDropsRetentionWindow() {
        String nodeId = "preview-grace-node";
        PreviewManager.touchNonEmptyForTests(nodeId, "ghost_block");
        assertTrue(PreviewManager.withinEmptyGraceForTests(nodeId, "ghost_block"));

        PreviewManager.clearEmptyInputGrace();
        assertFalse(PreviewManager.withinEmptyGraceForTests(nodeId, "ghost_block"));
    }

    @Test
    void hideNodePreviewsClearsNodeEmptyInputGrace() {
        String nodeId = "preview-hide-grace-node";
        String otherId = "preview-other-node";
        PreviewManager.touchNonEmptyForTests(nodeId, "ghost_block");
        PreviewManager.touchNonEmptyForTests(otherId, "ghost_block");
        assertTrue(PreviewManager.withinEmptyGraceForTests(nodeId, "ghost_block"));
        assertTrue(PreviewManager.withinEmptyGraceForTests(otherId, "ghost_block"));

        PreviewManager.hideNodePreviews(nodeId);
        assertFalse(PreviewManager.withinEmptyGraceForTests(nodeId, "ghost_block"));
        assertTrue(PreviewManager.withinEmptyGraceForTests(otherId, "ghost_block"));
    }

    @Test
    void hidePreviewsNotOwnedByClearsDeletedOwnersWithoutTouchingLive() {
        String deleted = "deleted-owner";
        String live = "live-owner";
        PreviewManager.touchNonEmptyForTests(deleted, "ghost_block");
        PreviewManager.touchNonEmptyForTests(live, "ghost_block");

        PreviewManager.hidePreviewsNotOwnedBy(java.util.Set.of(live));

        assertFalse(PreviewManager.withinEmptyGraceForTests(deleted, "ghost_block"));
        assertTrue(PreviewManager.withinEmptyGraceForTests(live, "ghost_block"));
    }
}
