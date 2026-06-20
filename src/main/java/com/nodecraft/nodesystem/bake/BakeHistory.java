package com.nodecraft.nodesystem.bake;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Undo/redo transaction history for baked world changes.
 */
public class BakeHistory {

    private static final int MAX_UNDO_STACK_SIZE = 32;

    private final List<UndoRecord> undoStack = new ArrayList<>();
    private final List<UndoRecord> redoStack = new ArrayList<>();

    public void push(UndoRecord record) {
        if (record == null || record.size() == 0) {
            return;
        }
        undoStack.add(record);
        redoStack.clear();
        trim(undoStack);
    }

    public UndoRecord pop() {
        return undoStack.isEmpty() ? null : undoStack.remove(undoStack.size() - 1);
    }

    public UndoRecord peek() {
        return undoStack.isEmpty() ? null : undoStack.get(undoStack.size() - 1);
    }

    public boolean undoLast(World world) {
        UndoRecord record = pop();
        if (record == null || world == null) {
            return false;
        }
        UndoRecord redoRecord = record.applyAndCaptureInverse(world);
        if (redoRecord != null && redoRecord.size() > 0) {
            redoStack.add(redoRecord);
            trim(redoStack);
        }
        return true;
    }

    public boolean redoLast(World world) {
        UndoRecord record = redoStack.isEmpty() ? null : redoStack.remove(redoStack.size() - 1);
        if (record == null || world == null) {
            return false;
        }
        UndoRecord undoRecord = record.applyAndCaptureInverse(world);
        if (undoRecord != null && undoRecord.size() > 0) {
            undoStack.add(undoRecord);
            trim(undoStack);
        }
        return true;
    }

    public boolean hasUndo() {
        return !undoStack.isEmpty();
    }

    public int size() {
        return undoStack.size();
    }

    public int redoSize() {
        return redoStack.size();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    private void trim(List<UndoRecord> stack) {
        while (stack.size() > MAX_UNDO_STACK_SIZE) {
            stack.remove(0);
        }
    }

    public static class UndoRecord {
        private final UUID bakeId;
        private final List<BlockPos> positions = new ArrayList<>();
        private final List<BlockState> previousStates = new ArrayList<>();

        public UndoRecord(UUID bakeId) {
            this.bakeId = bakeId;
        }

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

        public void apply(World world) {
            applyAndCaptureInverse(world);
            positions.clear();
            previousStates.clear();
        }

        private UndoRecord applyAndCaptureInverse(World world) {
            if (world == null) {
                return null;
            }
            UndoRecord inverse = new UndoRecord(bakeId);
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                BlockState targetState = previousStates.get(i);
                BlockState currentState = world.getBlockState(pos);
                inverse.add(pos, currentState);
                world.setBlockState(pos, targetState, 3);
            }
            return inverse;
        }

        public UUID getBakeId() {
            return bakeId;
        }

        public List<BlockPos> getPositions() {
            return Collections.unmodifiableList(positions);
        }

        public List<BlockState> getPreviousStates() {
            return Collections.unmodifiableList(previousStates);
        }
    }
}
