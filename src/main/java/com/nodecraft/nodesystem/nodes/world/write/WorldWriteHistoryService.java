package com.nodecraft.nodesystem.nodes.world.write;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores undo records for direct world.write operations.
 */
public final class WorldWriteHistoryService {
    private static final int MAX_UNDO_STACK_SIZE = 32;
    private static final WorldWriteHistoryService INSTANCE = new WorldWriteHistoryService();

    private final List<UndoRecord> undoStack = new ArrayList<>();
    private final List<UndoRecord> redoStack = new ArrayList<>();

    private WorldWriteHistoryService() {
    }

    public static WorldWriteHistoryService getInstance() {
        return INSTANCE;
    }

    public synchronized void push(UndoRecord record) {
        if (record == null || record.size() == 0) {
            return;
        }
        undoStack.add(record);
        redoStack.clear();
        while (undoStack.size() > MAX_UNDO_STACK_SIZE) {
            undoStack.remove(0);
        }
    }

    public synchronized UndoRecord pop() {
        return undoStack.isEmpty() ? null : undoStack.remove(undoStack.size() - 1);
    }

    public synchronized UndoRecord peek() {
        return undoStack.isEmpty() ? null : undoStack.get(undoStack.size() - 1);
    }

    public synchronized int size() {
        return undoStack.size();
    }

    public synchronized int redoSize() {
        return redoStack.size();
    }

    public synchronized boolean undoLast(World world) {
        UndoRecord record = pop();
        if (record == null || world == null) {
            return false;
        }
        UndoRecord redoRecord = record.applyAndCaptureInverse(world);
        if (redoRecord != null && redoRecord.size() > 0) {
            redoStack.add(redoRecord);
            trimRedoStack();
        }
        return true;
    }

    public synchronized boolean redoLast(World world) {
        UndoRecord record = redoStack.isEmpty() ? null : redoStack.remove(redoStack.size() - 1);
        if (record == null || world == null) {
            return false;
        }
        UndoRecord undoRecord = record.applyAndCaptureInverse(world);
        if (undoRecord != null && undoRecord.size() > 0) {
            undoStack.add(undoRecord);
            while (undoStack.size() > MAX_UNDO_STACK_SIZE) {
                undoStack.remove(0);
            }
        }
        return true;
    }

    public synchronized void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    private void trimRedoStack() {
        while (redoStack.size() > MAX_UNDO_STACK_SIZE) {
            redoStack.remove(0);
        }
    }

    public static final class UndoRecord {
        private final List<BlockPos> positions = new ArrayList<>();
        private final List<BlockState> previousStates = new ArrayList<>();

        public void add(BlockPos pos, BlockState previousState) {
            if (pos == null || previousState == null) {
                return;
            }
            positions.add(pos.toImmutable());
            previousStates.add(previousState);
        }

        public int size() {
            return positions.size();
        }

        public List<BlockPos> getPositions() {
            return Collections.unmodifiableList(positions);
        }

        public boolean apply(World world) {
            if (world == null) {
                return false;
            }
            for (int i = 0; i < positions.size(); i++) {
                world.setBlockState(positions.get(i), previousStates.get(i), 3);
            }
            return true;
        }

        private UndoRecord applyAndCaptureInverse(World world) {
            if (world == null) {
                return null;
            }
            UndoRecord inverse = new UndoRecord();
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                BlockState targetState = previousStates.get(i);
                BlockState currentState = world.getBlockState(pos);
                inverse.add(pos, currentState);
                world.setBlockState(pos, targetState, 3);
            }
            return inverse;
        }
    }
}
