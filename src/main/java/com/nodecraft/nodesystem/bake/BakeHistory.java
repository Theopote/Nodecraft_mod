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
        if (world == null) {
            return false;
        }
        return undoLast(worldAccess(world));
    }

    boolean undoLast(BlockStateAccess access) {
        if (access == null || undoStack.isEmpty()) {
            return false;
        }
        UndoRecord record = undoStack.getLast();
        UndoApplyResult result = record.applyAndCaptureInverseStrict(access);
        if (!result.fullySucceeded(record.size())) {
            return false;
        }
        undoStack.removeLast();
        if (result.inverse() != null && result.inverse().size() > 0) {
            redoStack.add(result.inverse());
            trim(redoStack);
        }
        return true;
    }

    /**
     * Synchronous redo - executes all block restores in a single operation.
     * WARNING: This can cause server lag for large builds. Consider using redoLastAsync instead.
     */
    public boolean redoLast(World world) {
        if (world == null) {
            return false;
        }
        return redoLast(worldAccess(world));
    }

    boolean redoLast(BlockStateAccess access) {
        if (access == null || redoStack.isEmpty()) {
            return false;
        }
        UndoRecord record = redoStack.getLast();
        UndoApplyResult result = record.applyAndCaptureInverseStrict(access);
        if (!result.fullySucceeded(record.size())) {
            return false;
        }
        redoStack.removeLast();
        if (result.inverse() != null && result.inverse().size() > 0) {
            pushUndo(result.inverse());
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

    private static BlockStateAccess worldAccess(World world) {
        return new BlockStateAccess() {
            @Override
            public BlockState getBlockState(BlockPos pos) {
                return world.getBlockState(pos);
            }

            @Override
            public boolean setBlockState(BlockPos pos, BlockState state) {
                return world.setBlockState(pos, state, 3);
            }
        };
    }

    public record UndoApplyResult(
            UndoRecord inverse,
            int restoredCount,
            int failedCount,
            RollbackResult rollback) {
        public static UndoApplyResult failed(int expected) {
            return new UndoApplyResult(new UndoRecord(UUID.randomUUID()), 0, expected, RollbackResult.notNeeded());
        }

        public boolean fullySucceeded(int expectedEntries) {
            return expectedEntries > 0
                    && failedCount == 0
                    && restoredCount == expectedEntries
                    && rollback.succeeded();
        }
    }

    public record RollbackResult(boolean attempted, int attemptedCount, int failedCount) {
        public static RollbackResult notNeeded() {
            return new RollbackResult(false, 0, 0);
        }

        public static RollbackResult succeeded(int attemptedCount) {
            return new RollbackResult(true, attemptedCount, 0);
        }

        public static RollbackResult failed(int attemptedCount, int failedCount) {
            return new RollbackResult(true, attemptedCount, failedCount);
        }

        public boolean succeeded() {
            return !attempted || failedCount == 0;
        }
    }

    /**
     * Minimal block mutation surface for atomic undo/redo apply (and unit tests without a full {@link World}).
     */
    interface BlockStateAccess {
        BlockState getBlockState(BlockPos pos);

        boolean setBlockState(BlockPos pos, BlockState state);
    }

    /**
     * Shared all-or-rollback apply used by sync undo/redo and unit-tested with opaque tokens.
     */
    static final class TransactionalApply {
        private TransactionalApply() {
        }

        record Result<T>(LinkedHashMap<BlockPos, T> inverse, int restoredCount, int failedCount, RollbackResult rollback) {
            boolean fullySucceeded() {
                return failedCount == 0
                        && restoredCount > 0
                        && inverse.size() == restoredCount
                        && rollback.succeeded();
            }
        }

        static <T> Result<T> applyAllOrRollback(
                LinkedHashMap<BlockPos, T> originals,
                java.util.function.Function<BlockPos, T> get,
                java.util.function.BiFunction<BlockPos, T, Boolean> set) {
            LinkedHashMap<BlockPos, T> inverse = new LinkedHashMap<>();
            List<Map.Entry<BlockPos, T>> entries = new ArrayList<>(originals.entrySet());
            for (int i = entries.size() - 1; i >= 0; i--) {
                Map.Entry<BlockPos, T> entry = entries.get(i);
                BlockPos pos = entry.getKey();
                T target = entry.getValue();
                if (pos == null || target == null) {
                    RollbackResult rollback = rollbackPartial(inverse, set);
                    return new Result<>(new LinkedHashMap<>(), 0, entries.size(), rollback);
                }
                T current = get.apply(pos);
                if (!Boolean.TRUE.equals(set.apply(pos, target))) {
                    RollbackResult rollback = rollbackPartial(inverse, set);
                    return new Result<>(new LinkedHashMap<>(), 0, entries.size(), rollback);
                }
                inverse.putIfAbsent(pos.toImmutable(), current);
            }
            return new Result<>(inverse, inverse.size(), 0, RollbackResult.notNeeded());
        }

        private static <T> RollbackResult rollbackPartial(
                LinkedHashMap<BlockPos, T> partialInverse,
                java.util.function.BiFunction<BlockPos, T, Boolean> set) {
            if (partialInverse.isEmpty()) {
                return RollbackResult.notNeeded();
            }
            int attempted = 0;
            int rollbackFailed = 0;
            List<Map.Entry<BlockPos, T>> entries = new ArrayList<>(partialInverse.entrySet());
            for (int i = entries.size() - 1; i >= 0; i--) {
                Map.Entry<BlockPos, T> entry = entries.get(i);
                attempted++;
                if (!Boolean.TRUE.equals(set.apply(entry.getKey(), entry.getValue()))) {
                    rollbackFailed++;
                }
            }
            return rollbackFailed == 0
                    ? RollbackResult.succeeded(attempted)
                    : RollbackResult.failed(attempted, rollbackFailed);
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
            applyAndCaptureInverseStrict(world);
            originalStates.clear();
        }

        UndoApplyResult applyAndCaptureInverseStrict(World world) {
            if (world == null) {
                return UndoApplyResult.failed(size());
            }
            return applyAndCaptureInverseStrict(BakeHistory.worldAccess(world));
        }

        UndoApplyResult applyAndCaptureInverseStrict(BlockStateAccess access) {
            TransactionalApply.Result<BlockState> applied = TransactionalApply.applyAllOrRollback(
                    originalStates,
                    access::getBlockState,
                    access::setBlockState);
            if (!applied.fullySucceeded() || applied.restoredCount() != size()) {
                return new UndoApplyResult(
                        new UndoRecord(bakeId),
                        0,
                        size(),
                        applied.rollback());
            }
            UndoRecord inverse = new UndoRecord(bakeId);
            for (Map.Entry<BlockPos, BlockState> entry : applied.inverse().entrySet()) {
                inverse.add(entry.getKey(), entry.getValue());
            }
            return new UndoApplyResult(inverse, applied.restoredCount(), 0, RollbackResult.notNeeded());
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
