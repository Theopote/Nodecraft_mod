package com.nodecraft.nodesystem.bake;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BakeTaskSnapshotTest {

    @Test
    void resolveStateUsesBakeTaskStateDisplayNames() {
        UUID taskId = UUID.randomUUID();

        assertEquals("Queued", snapshot(taskId, BakeTaskState.QUEUED).resolveState());
        assertEquals("Running", snapshot(taskId, BakeTaskState.RUNNING).resolveState());
        assertEquals("Completed", snapshot(taskId, BakeTaskState.COMPLETED).resolveState());
        assertEquals("Cancelling", snapshot(taskId, BakeTaskState.CANCELLING).resolveState());
        assertEquals("Rolling Back", snapshot(taskId, BakeTaskState.ROLLING_BACK).resolveState());
        assertEquals("Cancelled", snapshot(taskId, BakeTaskState.CANCELLED).resolveState());
        assertEquals("Timed Out", snapshot(taskId, BakeTaskState.TIMED_OUT).resolveState());
        assertEquals("Failed", snapshot(taskId, BakeTaskState.FAILED).resolveState());
        assertEquals("Rollback Failed", snapshot(taskId, BakeTaskState.ROLLBACK_FAILED).resolveState());
    }

    @Test
    void abortStatesReportCancelledFlag() {
        UUID taskId = UUID.randomUUID();
        assertTrue(snapshot(taskId, BakeTaskState.CANCELLED).cancelled());
        assertTrue(snapshot(taskId, BakeTaskState.TIMED_OUT).cancelled());
        assertTrue(snapshot(taskId, BakeTaskState.FAILED).cancelled());
        assertTrue(snapshot(taskId, BakeTaskState.ROLLBACK_FAILED).cancelled());
    }

    @Test
    void getTaskSnapshotReturnsNullForUnknownTask() {
        assertNull(BakePlacementService.getInstance().getTaskSnapshot(UUID.randomUUID()));
    }

    private static BakePlacementService.TaskSnapshot snapshot(UUID taskId, BakeTaskState state) {
        return new BakePlacementService.TaskSnapshot(taskId, 0, 0, 10, 10, 0.0d, state, 0, 0, 0);
    }
}
