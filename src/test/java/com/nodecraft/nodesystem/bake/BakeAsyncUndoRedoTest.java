package com.nodecraft.nodesystem.bake;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
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

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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
    void emptyRecordsAreIgnored() {
        BakeHistory.UndoRecord empty = new BakeHistory.UndoRecord(UUID.randomUUID());
        history.push(empty);
        history.pushRedo(empty);
        history.pushUndo(empty);

        assertEquals(0, history.size());
        assertEquals(0, history.redoSize());
    }

    private static BakeHistory.UndoRecord record(String label) {
        BakeHistory.UndoRecord record = new BakeHistory.UndoRecord(UUID.nameUUIDFromBytes(label.getBytes()));
        record.add(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState());
        return record;
    }
}
