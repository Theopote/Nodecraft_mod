package com.nodecraft.nodesystem.bake;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Undo/redo transaction history for baked world changes.
 */
public class BakeHistory {

    private static final int MAX_UNDO_STACK_SIZE = 32;

    private final List<UndoRecord> undoStack = new ArrayList<>();
    private final List<UndoRecord> redoStack = new ArrayList<>();

    /**
     * Push a record to the undo stack.
     * This is for normal APPLY operations - the inverse of what was just done.
     * Clears the redo stack (standard undo/redo semantics).
     */
    public void push(UndoRecord record) {
        if (record == null || record.size() == 0) {
            return;
        }
        undoStack.add(record);
        redoStack.clear();
        trim(undoStack);
    }

    /**
     * Push a record to the undo stack without clearing redo stack.
     * This is for REDO operations - the inverse goes back to undo.
     */
    public void pushUndo(UndoRecord record) {
        if (record == null || record.size() == 0) {
            return;
        }
        undoStack.add(record);
        trim(undoStack);
    }

    /**
     * Push a record to the redo stack.
     * This is for UNDO operations - the inverse goes to redo.
     */
    public void pushRedo(UndoRecord record) {
        if (record == null || record.size() == 0) {
            return;
        }
        redoStack.add(record);
        trim(redoStack);
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
        UndoRecord inverse = record.captureInverseAfterApply(world);
        if (inverse != null && inverse.size() > 0) {
            pushUndo(inverse);
        }
        return true;
    }
    /**
     * Asynchronous undo - executes block restores across multiple ticks via BakePlacementService.
     * This prevents server lag on large builds.
     * <p>
     * With BakeOperationKind.UNDO, the service automatically routes the inverse to redoStack.
     *
     * @param actorId Actor performing the undo
     * @param world Target world
     * @param blocksPerTick Maximum blocks to restore per tick
     * @param tickBudgetNanos Maximum time budget per tick in nanoseconds
     * @return Task ID if undo was queued, null if nothing to undo
     */
    public UUID undoLastAsync(UUID actorId, World world, int blocksPerTick, long tickBudgetNanos) {
        UndoRecord undoRecord = pop();
        if (undoRecord == null || world == null) {
            return null;
        }

        List<BakeTask.Placement> placements = new ArrayList<>(undoRecord.size());
        for (int i = 0; i < undoRecord.size(); i++) {
            placements.add(new BakeTask.Placement(
                undoRecord.getPositions().get(i),
                undoRecord.getPreviousStates().get(i)
            ));
        }

        UUID taskId = BakePlacementService.getInstance().enqueuePlacements(
            world,
            placements,
            PlacementMode.OVERWRITE,
            true,
            BakeOperationKind.UNDO,
            blocksPerTick,
            tickBudgetNanos,
            actorId,
            null,
            () -> pushUndo(undoRecord)
        );
        if (taskId == null) {
            pushUndo(undoRecord);
        }
        return taskId;
    }

    /**
     * Asynchronous redo - executes block restores across multiple ticks via BakePlacementService.
     * This prevents server lag on large builds.
     * <p>
     * With BakeOperationKind.REDO, the service automatically routes the inverse to undoStack.
     *
     * @param actorId Actor performing the redo
     * @param world Target world
     * @param blocksPerTick Maximum blocks to restore per tick
     * @param tickBudgetNanos Maximum time budget per tick in nanoseconds
     * @return Task ID if redo was queued, null if nothing to redo
     */
    public UUID redoLastAsync(UUID actorId, World world, int blocksPerTick, long tickBudgetNanos) {
        UndoRecord redoRecord = redoStack.isEmpty() ? null : redoStack.removeLast();
        if (redoRecord == null || world == null) {
            return null;
        }

        List<BakeTask.Placement> placements = new ArrayList<>(redoRecord.size());
        for (int i = 0; i < redoRecord.size(); i++) {
            placements.add(new BakeTask.Placement(
                redoRecord.getPositions().get(i),
                redoRecord.getPreviousStates().get(i)
            ));
        }

        UUID taskId = BakePlacementService.getInstance().enqueuePlacements(
            world,
            placements,
            PlacementMode.OVERWRITE,
            true,
            BakeOperationKind.REDO,
            blocksPerTick,
            tickBudgetNanos,
            actorId,
            null,
            () -> pushRedo(redoRecord)
        );
        if (taskId == null) {
            pushRedo(redoRecord);
        }
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
        /** First-write-wins original states; preserves insertion order for apply/async enqueue. */
        private final LinkedHashMap<BlockPos, BlockState> originalStates = new LinkedHashMap<>();

        public UndoRecord(UUID bakeId) {
            this.bakeId = bakeId;
        }

        /**
         * Creates a non-empty record for stack-semantics unit tests without bootstrapping MC registries.
         */
        static UndoRecord syntheticForStackTest(UUID bakeId) {
            UndoRecord record = new UndoRecord(bakeId);
            record.originalStates.put(BlockPos.ORIGIN, null);
            return record;
        }

        /**
         * Records the pre-transaction state for {@code pos}. Subsequent calls for the same
         * position are ignored so history stores transaction-start state only.
         */
        public void add(BlockPos pos, BlockState previousState) {
            if (pos == null || previousState == null) {
                return;
            }
            originalStates.putIfAbsent(pos.toImmutable(), previousState);
        }

        public int size() {
            return originalStates.size();
        }

        public void apply(World world) {
            captureInverseAfterApply(world);
            originalStates.clear();
        }

        UndoRecord captureInverseAfterApply(World world) {
            return applyAndCaptureInverse(world);
        }

        private UndoRecord applyAndCaptureInverse(World world) {
            if (world == null) {
                return null;
            }
            UndoRecord inverse = new UndoRecord(bakeId);
            // Restore in reverse insertion order (LIFO).
            List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(originalStates.entrySet());
            for (int i = entries.size() - 1; i >= 0; i--) {
                Map.Entry<BlockPos, BlockState> entry = entries.get(i);
                BlockPos pos = entry.getKey();
                BlockState targetState = entry.getValue();
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
            return List.copyOf(originalStates.keySet());
        }

        public List<BlockState> getPreviousStates() {
            return List.copyOf(originalStates.values());
        }
    }
}
