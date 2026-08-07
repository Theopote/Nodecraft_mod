package com.nodecraft.nodesystem.bake;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests undo/redo history stack routing semantics introduced with async bake support.
 *
 * Verifies that:
 * - APPLY operations push to undoStack and clear redoStack
 * - UNDO operations push inverse records to redoStack
 * - REDO operations push inverse records to undoStack (preserving redoStack)
 * - Multi-transaction sequences maintain correct stack sizes
 */
class BakeAsyncUndoRedoTest {

    private BakeHistory history;

    @BeforeEach
    void setUp() {
        history = new BakeHistory();
    }

    @Test
    void pushClearsRedoStack() {
        history.pushRedo(record("A"));
        assertEquals(1, history.redoSize());

        history.push(record("B"));

        assertEquals(1, history.size());
        assertEquals(0, history.redoSize(), "New APPLY should clear redo stack");
    }

    @Test
    void undoRoutesInverseToRedoStack() {
        history.push(record("build"));
        assertEquals(1, history.size());
        assertEquals(0, history.redoSize());

        history.pop();
        history.pushRedo(record("undo-inverse"));

        assertEquals(0, history.size());
        assertEquals(1, history.redoSize());
    }

    @Test
    void redoRoutesInverseToUndoStackWithoutClearingRedo() {
        history.pushRedo(record("redo-target"));
        history.pushUndo(record("redo-inverse"));

        assertEquals(1, history.size());
        assertEquals(1, history.redoSize(), "REDO inverse should not clear redo stack");
    }

    @Test
    void multiTransactionUndoMaintainsLifoOrder() {
        history.push(record("A"));
        history.push(record("B"));
        assertEquals(2, history.size());

        BakeHistory.UndoRecord top = history.peek();
        assertNotNull(top);
        assertEquals(UUID.nameUUIDFromBytes("B".getBytes()), top.getBakeId());

        history.pop();
        history.pushRedo(record("B-inverse"));
        assertEquals(1, history.size());
        assertEquals(1, history.redoSize());

        history.pop();
        history.pushRedo(record("A-inverse"));
        assertEquals(0, history.size());
        assertEquals(2, history.redoSize());
    }

    @Test
    void newApplyAfterUndoClearsRedo() {
        history.push(record("A"));
        history.pop();
        history.pushRedo(record("A-inverse"));
        assertEquals(1, history.redoSize());

        history.push(record("B"));

        assertEquals(1, history.size());
        assertEquals(0, history.redoSize());
    }

    @Test
    void pushRedoDoesNotClearUndoStack() {
        history.push(record("A"));
        history.pushRedo(record("B-inverse"));

        assertEquals(1, history.size());
        assertEquals(1, history.redoSize());
    }

    @Test
    void asyncUndoCancelRestoresUndoWithoutClearingRedo() {
        history.push(record("A"));
        history.pushRedo(record("pre-existing-redo"));
        assertEquals(1, history.size());
        assertEquals(1, history.redoSize());

        BakeHistory.UndoRecord popped = history.pop();
        assertNotNull(popped);
        assertEquals(0, history.size());

        // Cancel restore must use pushUndo (not push) so redo survives.
        history.pushUndo(popped);

        assertEquals(1, history.size());
        assertEquals(1, history.redoSize(), "Cancel restore must not clear redo");
    }

    @Test
    void cancelledApplyShouldCommitPartialUndoLikeSuccessfulFinish() {
        // Policy mirror of BakePlacementService.finalizeCancelledTask(APPLY):
        // partial undoRecords are pushed so timeout/cancel remains undoable.
        history.push(record("partial-apply"));
        assertEquals(1, history.size());
        assertEquals(0, history.redoSize());
    }

    @Test
    void cancelledUndoShouldAbortWithoutLeavingRedoResidue() {
        // Policy mirror of BakePlacementService.finalizeCancelledTask(UNDO):
        // roll back in-world progress, then restore the popped undo record.
        history.push(record("full-apply"));
        BakeHistory.UndoRecord popped = history.pop();
        assertNotNull(popped);
        assertEquals(0, history.size());

        history.pushUndo(popped);
        assertEquals(1, history.size());
        assertEquals(0, history.redoSize(), "Aborted undo must not push redo");
    }

    @Test
    void emptyRecordsAreIgnored() {
        BakeHistory.UndoRecord empty = new BakeHistory.UndoRecord(UUID.randomUUID());
        history.push(empty);
        history.pushRedo(empty);
        history.pushUndo(empty);

        assertEquals(0, history.size());
        assertEquals(0, history.redoSize());
    }

    private static BakeHistory.UndoRecord record(String label) {
        return BakeHistory.UndoRecord.syntheticForStackTest(UUID.nameUUIDFromBytes(label.getBytes()));
    }
}
