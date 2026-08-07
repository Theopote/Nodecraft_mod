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
        return undoStack.isEmpty() ? null : undoStack.removeLast();
    }

    public UndoRecord peek() {
        return undoStack.isEmpty() ? null : undoStack.getLast();
    }

    /**
     * Synchronous undo - executes all block restores in a single operation.
     * WARNING: This can cause server lag for large builds. Consider using undoLastAsync instead.
     */
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

    /**
     * Synchronous redo - executes all block restores in a single operation.
     * WARNING: This can cause server lag for large builds. Consider using redoLastAsync instead.
     */
    public boolean redoLast(World world) {
        UndoRecord record = redoStack.isEmpty() ? null : redoStack.removeLast();
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

    /**
     * Asynchronous undo - executes block restores across multiple ticks via BakePlacementService.
     * This prevents server lag on large builds.
     * 
     * CORRECT SEMANTICS:
     * 1. Capture current world state (for redo)
     * 2. Restore previous state from undo record
     * 3. On success: pop from undoStack, push captured state to redoStack
     * 4. On failure: undo record remains in undoStack for retry
     *
     * @param actorId Actor performing the undo
     * @param world Target world
     * @param blocksPerTick Maximum blocks to restore per tick
     * @param tickBudgetNanos Maximum time budget per tick in nanoseconds
     * @return Task ID if undo was queued, null if nothing to undo
     */
    public UUID undoLastAsync(UUID actorId, World world, int blocksPerTick, long tickBudgetNanos) {
        // Peek instead of pop - only remove on success
        UndoRecord undoRecord = peek();
        if (undoRecord == null || world == null) {
            return null;
        }

        // CRITICAL: Capture current world state BEFORE undo (for redo)
        UndoRecord redoRecord = new UndoRecord(UUID.randomUUID());
        for (int i = 0; i < undoRecord.size(); i++) {
            BlockPos pos = undoRecord.getPositions().get(i);
            BlockState currentState = world.getBlockState(pos);
            redoRecord.add(pos, currentState);
        }

        // Convert UndoRecord to placements
        List<BakeTask.Placement> placements = new ArrayList<>(undoRecord.size());
        for (int i = 0; i < undoRecord.size(); i++) {
            placements.add(new BakeTask.Placement(
                undoRecord.getPositions().get(i),
                undoRecord.getPreviousStates().get(i)
            ));
        }

        // Enqueue undo as a bake task WITHOUT recording (manual stack management)
        UUID taskId = BakePlacementService.getInstance().enqueuePlacements(
            world,
            placements,
            PlacementMode.OVERWRITE,
            false, // Don't auto-record - we manage stacks manually
            blocksPerTick,
            tickBudgetNanos,
            actorId,
            () -> {
                // On successful completion: undo → redoStack
                UndoRecord completed = pop();
                if (completed != null) {
                    redoStack.add(redoRecord);  // Push captured state to redo
                    trim(redoStack);
                }
            }
        );

        return taskId;
    }

    /**
     * Asynchronous redo - executes block restores across multiple ticks via BakePlacementService.
     * This prevents server lag on large builds.
     * 
     * CORRECT SEMANTICS:
     * 1. Capture current world state (for undo)
     * 2. Restore state from redo record
     * 3. On success: pop from redoStack, push captured state to undoStack
     * 4. On failure: redo record remains in redoStack for retry
     *
     * @param actorId Actor performing the redo
     * @param world Target world
     * @param blocksPerTick Maximum blocks to restore per tick
     * @param tickBudgetNanos Maximum time budget per tick in nanoseconds
     * @return Task ID if redo was queued, null if nothing to redo
     */
    public UUID redoLastAsync(UUID actorId, World world, int blocksPerTick, long tickBudgetNanos) {
        // Peek instead of removing - only remove on success
        UndoRecord redoRecord = redoStack.isEmpty() ? null : redoStack.getLast();
        if (redoRecord == null || world == null) {
            return null;
        }

        // CRITICAL: Capture current world state BEFORE redo (for undo)
        UndoRecord undoRecord = new UndoRecord(UUID.randomUUID());
        for (int i = 0; i < redoRecord.size(); i++) {
            BlockPos pos = redoRecord.getPositions().get(i);
            BlockState currentState = world.getBlockState(pos);
            undoRecord.add(pos, currentState);
        }

        // Convert UndoRecord to placements
        List<BakeTask.Placement> placements = new ArrayList<>(redoRecord.size());
        for (int i = 0; i < redoRecord.size(); i++) {
            placements.add(new BakeTask.Placement(
                redoRecord.getPositions().get(i),
                redoRecord.getPreviousStates().get(i)
            ));
        }

        // Enqueue redo as a bake task WITHOUT recording (manual stack management)
        UUID taskId = BakePlacementService.getInstance().enqueuePlacements(
            world,
            placements,
            PlacementMode.OVERWRITE,
            false, // Don't auto-record - we manage stacks manually
            blocksPerTick,
            tickBudgetNanos,
            actorId,
            () -> {
                // On successful completion: redo → undoStack
                if (!redoStack.isEmpty()) {
                    redoStack.removeLast();
                    undoStack.add(undoRecord);  // Push captured state to undo
                    trim(undoStack);
                }
            }
        );

        return taskId;
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
            stack.removeFirst();
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
