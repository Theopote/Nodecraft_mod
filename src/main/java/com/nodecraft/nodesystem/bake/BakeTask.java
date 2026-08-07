package com.nodecraft.nodesystem.bake;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Time-sliced block placement task driven from the server tick.
 * <p>
 * Cancel / timeout always rolls back in-world progress using captured previous states,
 * so World and BakeHistory stay consistent (COMPLETED = commit, CANCELLED/TIMED_OUT = rollback).
 */
public class BakeTask {

    private final UUID taskId;
    private final World world;
    private final List<Placement> placements;
    private final PlacementMode placementMode;
    private final UUID actorId;
    private final boolean recordUndo;
    private final BakeOperationKind operationKind;
    private final int blocksPerTick;
    private final long timeBudgetNanos;
    private final Runnable onComplete;
    private final Runnable onCancel;

    private final List<BakeUndoRecord> undoRecords = new ArrayList<>();
    private int nextIndex = 0;
    private int rollbackIndex = 0;
    private BakeTaskState state = BakeTaskState.QUEUED;
    private BakeTaskState pendingAbortState = BakeTaskState.CANCELLED;
    private int placedCount = 0;
    private int skippedCount = 0;

    public BakeTask(UUID taskId,
                    World world,
                    List<BlockPos> positions,
                    BlockState targetState,
                    PlacementMode placementMode,
                    boolean recordUndo,
                    int blocksPerTick,
                    Runnable onComplete) {
        this(taskId, world, toPlacements(positions, targetState), placementMode, recordUndo,
             BakeOperationKind.APPLY, blocksPerTick, 0L, BakePlacementService.SERVER_ACTOR_ID, onComplete);
    }

    public BakeTask(UUID taskId,
                    World world,
                    List<Placement> placements,
                    PlacementMode placementMode,
                    boolean recordUndo,
                    int blocksPerTick,
                    long timeBudgetNanos,
                    UUID actorId,
                    Runnable onComplete) {
        this(taskId, world, placements, placementMode, recordUndo,
             BakeOperationKind.APPLY, blocksPerTick, timeBudgetNanos, actorId, onComplete);
    }

    public BakeTask(UUID taskId,
                    World world,
                    List<Placement> placements,
                    PlacementMode placementMode,
                    boolean recordUndo,
                    BakeOperationKind operationKind,
                    int blocksPerTick,
                    long timeBudgetNanos,
                    UUID actorId,
                    Runnable onComplete) {
        this(taskId, world, placements, placementMode, recordUndo,
             operationKind, blocksPerTick, timeBudgetNanos, actorId, onComplete, null);
    }

    public BakeTask(UUID taskId,
                    World world,
                    List<Placement> placements,
                    PlacementMode placementMode,
                    boolean recordUndo,
                    BakeOperationKind operationKind,
                    int blocksPerTick,
                    long timeBudgetNanos,
                    UUID actorId,
                    Runnable onComplete,
                    @Nullable Runnable onCancel) {
        this.taskId = taskId != null ? taskId : UUID.randomUUID();
        this.world = world;
        this.placements = copyPlacements(placements);
        this.placementMode = placementMode != null ? placementMode : PlacementMode.OVERWRITE;
        this.recordUndo = recordUndo;
        this.operationKind = operationKind != null ? operationKind : BakeOperationKind.APPLY;
        this.actorId = actorId != null ? actorId : BakePlacementService.SERVER_ACTOR_ID;
        this.blocksPerTick = Math.max(1, blocksPerTick);
        this.timeBudgetNanos = Math.max(0L, timeBudgetNanos);
        this.onComplete = onComplete;
        this.onCancel = onCancel;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public World getWorld() {
        return world;
    }

    public UUID getActorId() {
        return actorId;
    }

    public BakeOperationKind getOperationKind() {
        return operationKind;
    }

    public boolean isRecordUndo() {
        return recordUndo;
    }

    public Runnable getOnComplete() {
        return onComplete;
    }

    public Runnable getOnCancel() {
        return onCancel;
    }

    public BakeTaskState getState() {
        return state;
    }

    /**
     * Whether this task should leave the active queue.
     * {@link BakeTaskState#ROLLING_BACK} stays queued until rollback finishes.
     */
    public boolean isCompleted() {
        if (state == BakeTaskState.ROLLING_BACK) {
            return isRollbackFinished();
        }
        return state.isTerminal() || state == BakeTaskState.CANCELLING;
    }

    public boolean isCancelled() {
        return state == BakeTaskState.CANCELLED
            || state == BakeTaskState.TIMED_OUT
            || state == BakeTaskState.CANCELLING
            || state == BakeTaskState.ROLLING_BACK;
    }

    public boolean isRollbackFinished() {
        return rollbackIndex >= undoRecords.size();
    }

    public int getPlacedCount() {
        return placedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public int getTotalCount() {
        return state == BakeTaskState.ROLLING_BACK ? undoRecords.size() : placements.size();
    }

    public int getRemainingCount() {
        if (state == BakeTaskState.ROLLING_BACK) {
            return Math.max(0, undoRecords.size() - rollbackIndex);
        }
        return Math.max(0, placements.size() - nextIndex);
    }

    public double getProgress() {
        if (state == BakeTaskState.ROLLING_BACK) {
            if (undoRecords.isEmpty()) {
                return 1.0d;
            }
            return Math.min(1.0d, (double) rollbackIndex / (double) undoRecords.size());
        }
        return placements.isEmpty() ? 1.0d : Math.min(1.0d, (double) nextIndex / (double) placements.size());
    }

    @SuppressWarnings("deprecation")
    public int processTick() {
        if (state == BakeTaskState.ROLLING_BACK) {
            return processRollbackTick();
        }
        if (state != BakeTaskState.QUEUED && state != BakeTaskState.RUNNING) {
            return -1;
        }
        if (world == null) {
            state = BakeTaskState.FAILED;
            return -1;
        }

        state = BakeTaskState.RUNNING;

        int limit = Math.min(nextIndex + blocksPerTick, placements.size());
        long deadline = timeBudgetNanos > 0L ? System.nanoTime() + timeBudgetNanos : Long.MAX_VALUE;
        int placedThisTick = 0;

        while (nextIndex < limit && System.nanoTime() < deadline) {
            Placement placement = placements.get(nextIndex++);
            BlockPos pos = placement.pos();
            BlockState targetState = placement.state();
            if (pos == null || targetState == null || !world.isChunkLoaded(pos)) {
                skippedCount++;
                continue;
            }

            if (placementMode == PlacementMode.INCREMENTAL && !world.isAir(pos)) {
                skippedCount++;
                continue;
            }

            // Always capture previous state so cancel/timeout can roll back the transaction.
            BlockState previous = world.getBlockState(pos);
            if (world.setBlockState(pos, targetState, Block.NOTIFY_ALL)) {
                placedThisTick++;
                placedCount++;
                undoRecords.add(new BakeUndoRecord(pos, previous));
            } else {
                skippedCount++;
            }
        }

        if (nextIndex >= placements.size()) {
            state = BakeTaskState.COMPLETED;
        }
        return placedThisTick;
    }

    @SuppressWarnings("deprecation")
    private int processRollbackTick() {
        if (world == null) {
            state = BakeTaskState.FAILED;
            return -1;
        }

        int limit = Math.min(rollbackIndex + blocksPerTick, undoRecords.size());
        long deadline = timeBudgetNanos > 0L ? System.nanoTime() + timeBudgetNanos : Long.MAX_VALUE;
        int restoredThisTick = 0;

        while (rollbackIndex < limit && System.nanoTime() < deadline) {
            BakeUndoRecord rec = undoRecords.get(rollbackIndex++);
            if (rec.pos() == null || rec.previousState() == null) {
                continue;
            }
            if (world.setBlockState(rec.pos(), rec.previousState(), Block.NOTIFY_ALL)) {
                restoredThisTick++;
            }
        }
        return restoredThisTick;
    }

    /**
     * Requests cancellation. Prefer {@link #requestCancel(BakeTaskState)} with
     * {@link BakeTaskState#CANCELLED} or {@link BakeTaskState#TIMED_OUT}.
     */
    public void cancel() {
        requestCancel(BakeTaskState.CANCELLED);
    }

    public void requestCancel(BakeTaskState terminalState) {
        if (state.isTerminal() || state == BakeTaskState.CANCELLING || state == BakeTaskState.ROLLING_BACK) {
            // Rollback must run to completion to keep World/History consistent.
            return;
        }
        BakeTaskState resolved = terminalState == BakeTaskState.TIMED_OUT
            ? BakeTaskState.TIMED_OUT
            : BakeTaskState.CANCELLED;
        state = BakeTaskState.CANCELLING;
        this.pendingAbortState = resolved;
    }

    BakeTaskState getPendingAbortState() {
        return pendingAbortState;
    }

    /**
     * Switches this task into time-sliced rollback mode using captured undo records.
     * The task must be re-queued by {@link BakePlacementService}.
     */
    public void beginTimeSlicedRollback() {
        state = BakeTaskState.ROLLING_BACK;
        rollbackIndex = 0;
    }

    /**
     * Synchronous full rollback. Prefer {@link #beginTimeSlicedRollback()} for large tasks.
     */
    public void rollback() {
        beginTimeSlicedRollback();
        while (!isRollbackFinished()) {
            processRollbackTick();
        }
    }

    void markAborted() {
        state = pendingAbortState != null ? pendingAbortState : BakeTaskState.CANCELLED;
    }

    public void undo() {
        beginTimeSlicedRollback();
        while (!isRollbackFinished()) {
            processRollbackTick();
        }
    }

    public List<BakeUndoRecord> getUndoRecords() {
        return new ArrayList<>(undoRecords);
    }

    private static List<Placement> toPlacements(List<BlockPos> positions, BlockState targetState) {
        List<Placement> out = new ArrayList<>();
        if (positions == null || targetState == null) {
            return out;
        }
        for (BlockPos pos : positions) {
            if (pos != null) {
                out.add(new Placement(pos, targetState));
            }
        }
        return out;
    }

    private static List<Placement> copyPlacements(List<Placement> placements) {
        List<Placement> out = new ArrayList<>();
        if (placements == null) {
            return out;
        }
        for (Placement placement : placements) {
            if (placement != null && placement.pos() != null && placement.state() != null) {
                out.add(new Placement(placement.pos(), placement.state()));
            }
        }
        return out;
    }

    public record Placement(BlockPos pos, BlockState state) {
        public Placement {
            pos = pos != null ? pos.toImmutable() : null;
        }
    }

    public record BakeUndoRecord(BlockPos pos, BlockState previousState) {
        public BakeUndoRecord {
            pos = pos != null ? pos.toImmutable() : null;
        }
    }
}
