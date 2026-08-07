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
     * @param actorId Actor performing the undo
     * @param world Target world
     * @param blocksPerTick Maximum blocks to restore per tick
     * @param tickBudgetNanos Maximum time budget per tick in nanoseconds
     * @return Task ID if undo was queued, null if nothing to undo
     */
    public UUID undoLastAsync(UUID actorId, World world, int blocksPerTick, long tickBudgetNanos) {
        UndoRecord record = pop();
        if (record == null || world == null) {
            return null;
        }

        // Convert UndoRecord to placements
        List<BakeTask.Placement> placements = new ArrayList<>(record.size());
        for (int i = 0; i < record.size(); i++) {
            placements.add(new BakeTask.Placement(
                record.getPositions().get(i),
                record.getPreviousStates().get(i)
            ));
        }

        // Enqueue undo as a bake task with inverse recording
        UUID taskId = BakePlacementService.getInstance().enqueuePlacements(
            world,
            placements,
            PlacementMode.OVERWRITE,
            true, // Record undo so we can redo
            blocksPerTick,
            tickBudgetNanos,
            actorId,
            () -> {
                // On completion, push the inverse to redo stack
                // This will be handled by BakePlacementService.finishTask
            }
        );

        return taskId;
    }

    /**
     * Asynchronous redo - executes block restores across multiple ticks via BakePlacementService.
     * This prevents server lag on large builds.
     *
     * @param actorId Actor performing the redo
     * @param world Target world
     * @param blocksPerTick Maximum blocks to restore per tick
     * @param tickBudgetNanos Maximum time budget per tick in nanoseconds
     * @return Task ID if redo was queued, null if nothing to redo
     */
    public UUID redoLastAsync(UUID actorId, World world, int blocksPerTick, long tickBudgetNanos) {
        UndoRecord record = redoStack.isEmpty() ? null : redoStack.removeLast();
        if (record == null || world == null) {
            return null;
        }

        // Convert UndoRecord to placements
        List<BakeTask.Placement> placements = new ArrayList<>(record.size());
        for (int i = 0; i < record.size(); i++) {
            placements.add(new BakeTask.Placement(
                record.getPositions().get(i),
                record.getPreviousStates().get(i)
            ));
        }

        // Enqueue redo as a bake task with inverse recording
        UUID taskId = BakePlacementService.getInstance().enqueuePlacements(
            world,
            placements,
            PlacementMode.OVERWRITE,
            true, // Record undo so we can undo again
            blocksPerTick,
            tickBudgetNanos,
            actorId,
            () -> {
                // On completion, push the inverse to undo stack
                // This will be handled by BakePlacementService.finishTask
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
