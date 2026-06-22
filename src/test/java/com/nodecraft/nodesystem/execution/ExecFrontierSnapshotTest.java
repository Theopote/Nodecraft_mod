package com.nodecraft.nodesystem.execution;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecFrontierSnapshotTest {

    @Test
    void highlightsExecWireByEndpoint() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ExecFrontierSnapshot snapshot = new ExecFrontierSnapshot(
                true,
                Set.of(sourceId),
                Set.of(new ExecFrontierSnapshot.ExecWire(sourceId, "exec_out", targetId, "exec_in")),
                Set.of(targetId),
                1L
        );

        assertTrue(snapshot.highlightsExecWire(sourceId, "exec_out", targetId, "exec_in"));
        assertFalse(snapshot.highlightsExecWire(sourceId, "exec_out", targetId, "exec_body"));
    }

    @Test
    void emptySnapshotDoesNotHighlight() {
        UUID nodeId = UUID.randomUUID();
        assertFalse(ExecFrontierSnapshot.EMPTY.isActive());
        assertFalse(ExecFrontierSnapshot.EMPTY.isActiveNode(nodeId));
        assertFalse(ExecFrontierSnapshot.EMPTY.isPendingNode(nodeId));
    }
}
