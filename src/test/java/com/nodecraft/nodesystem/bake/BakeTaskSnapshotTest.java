package com.nodecraft.nodesystem.bake;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BakeTaskSnapshotTest {

    @Test
    void resolveStateReportsQueuedRunningCompletedAndCancelled() {
        UUID taskId = UUID.randomUUID();

        BakePlacementService.TaskSnapshot queued = new BakePlacementService.TaskSnapshot(
            taskId, 0, 0, 10, 10, 0.0d, false
        );
        BakePlacementService.TaskSnapshot running = new BakePlacementService.TaskSnapshot(
            taskId, 3, 1, 10, 6, 0.4d, false
        );
        BakePlacementService.TaskSnapshot completed = new BakePlacementService.TaskSnapshot(
            taskId, 9, 1, 10, 0, 1.0d, false
        );
        BakePlacementService.TaskSnapshot cancelled = new BakePlacementService.TaskSnapshot(
            taskId, 2, 0, 10, 8, 0.2d, true
        );

        assertEquals("Queued", queued.resolveState());
        assertEquals("Running", running.resolveState());
        assertEquals("Completed", completed.resolveState());
        assertEquals("Cancelled", cancelled.resolveState());
    }

    @Test
    void getTaskSnapshotReturnsNullForUnknownTask() {
        assertNull(BakePlacementService.getInstance().getTaskSnapshot(UUID.randomUUID()));
    }
}
