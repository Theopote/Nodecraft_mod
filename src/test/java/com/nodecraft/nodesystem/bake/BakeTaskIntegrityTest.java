package com.nodecraft.nodesystem.bake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BakeTaskIntegrityTest {

    @Test
    void policySkipsAloneCompleteSuccessfully() {
        assertEquals(BakeTaskState.COMPLETED, BakeTask.resolveCompletionState(false, false));
        assertEquals(BakeTaskState.COMPLETED, BakeTask.resolveCompletionState(false, true));
    }

    @Test
    void integrityFailureWithoutWritesFailsClosed() {
        assertEquals(BakeTaskState.FAILED, BakeTask.resolveCompletionState(true, false));
    }

    @Test
    void integrityFailureWithPartialWritesRollsBack() {
        assertEquals(BakeTaskState.ROLLING_BACK, BakeTask.resolveCompletionState(true, true));
    }
}
