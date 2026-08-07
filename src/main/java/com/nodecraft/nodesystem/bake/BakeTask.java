package com.nodecraft.nodesystem.bake;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Time-sliced block placement task driven from the server tick.
 * <p>
 * Cancel / timeout always rolls back in-world progress using captured previous states,
 * so World and BakeHistory stay consistent (COMPLETED = commit, CANCELLED/TIMED_OUT = rollback).
 * <p>
 * Per-position transaction semantics: only the first successful write to a {@link BlockPos}
 * records the pre-transaction state ({@code putIfAbsent}). Repeated writes to the same
 * coordinate do not create intermediate undo entries, so rollback always restores the
 * world as it was when the task began — independent of upstream position uniqueness.
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

    /** Insertion-ordered original states; one entry per written BlockPos. */
    private final LinkedHashMap<BlockPos, BlockState> originalStates = new LinkedHashMap<>();
    /** Snapshot of originalStates entries for indexed LIFO rollback; set in beginTimeSlicedRollback. */
    private List<Map.Entry<BlockPos, BlockState>> rollbackEntries = List.of();
    private int nextIndex = 0;
    /** During rollback: remaining entries to restore (counts down from size to 0). */
    private int rollbackRemaining = 0;
    private int rollbackAttemptedCount = 0;
    private int rollbackRestoredCount = 0;
    private int rollbackFailedCount = 0;
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
            || state == BakeTaskState.ROLLBACK_FAILED
            || state == BakeTaskState.CANCELLING
            || state == BakeTaskState.ROLLING_BACK;
    }

    public int getRollbackAttemptedCount() {
        return rollbackAttemptedCount;
    }

    public int getRollbackRestoredCount() {
        return rollbackRestoredCount;
    }

    public int getRollbackFailedCount() {
        return rollbackFailedCount;
    }

    public boolean isRollbackFinished() {
        return rollbackRemaining <= 0;
    }

    public int getPlacedCount() {
        return placedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public int getTotalCount() {
        return state == BakeTaskState.ROLLING_BACK ? originalStates.size() : placements.size();
    }

    public int getRemainingCount() {
        if (state == BakeTaskState.ROLLING_BACK) {
            return Math.max(0, rollbackRemaining);
        }
        return Math.max(0, placements.size() - nextIndex);
    }

    public double getProgress() {
        if (state == BakeTaskState.ROLLING_BACK) {
            int total = originalStates.size();
            if (total == 0) {
                return 1.0d;
            }
            return Math.min(1.0d, (double) (total - rollbackRemaining) / (double) total);
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

            // Capture pre-transaction state once per position (first successful write wins).
            BlockState previous = world.getBlockState(pos);
            if (world.setBlockState(pos, targetState, Block.NOTIFY_ALL)) {
                placedThisTick++;
                placedCount++;
                originalStates.putIfAbsent(pos.toImmutable(), previous);
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

        // LIFO over insertion order: last unique write first, then earlier ones.
        // With one entry per pos this equals restoring every recorded original state.
        int limit = Math.max(0, rollbackRemaining - blocksPerTick);
        long deadline = timeBudgetNanos > 0L ? System.nanoTime() + timeBudgetNanos : Long.MAX_VALUE;
        int restoredThisTick = 0;

        while (rollbackRemaining > limit && System.nanoTime() < deadline) {
            rollbackRemaining--;
            Map.Entry<BlockPos, BlockState> entry = rollbackEntries.get(rollbackRemaining);
            BlockPos pos = entry.getKey();
            BlockState previous = entry.getValue();
            rollbackAttemptedCount++;
            if (pos == null || previous == null) {
                rollbackFailedCount++;
                continue;
            }
            if (world.setBlockState(pos, previous, Block.NOTIFY_ALL)) {
                restoredThisTick++;
                rollbackRestoredCount++;
            } else {
                // Advance past the entry so the task can terminate, but record the failure
                // so the terminal state is ROLLBACK_FAILED rather than a clean CANCELLED.
                rollbackFailedCount++;
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
     * Switches this task into time-sliced rollback mode using captured original states.
     * The task must be re-queued by {@link BakePlacementService}.
     */
    public void beginTimeSlicedRollback() {
        state = BakeTaskState.ROLLING_BACK;
        rollbackEntries = List.copyOf(originalStates.entrySet());
        rollbackRemaining = rollbackEntries.size();
        rollbackAttemptedCount = 0;
        rollbackRestoredCount = 0;
        rollbackFailedCount = 0;
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
        if (rollbackFailedCount > 0) {
            state = BakeTaskState.ROLLBACK_FAILED;
            return;
        }
        state = pendingAbortState != null ? pendingAbortState : BakeTaskState.CANCELLED;
    }

    public void undo() {
        beginTimeSlicedRollback();
        while (!isRollbackFinished()) {
            processRollbackTick();
        }
    }

    /**
     * Original pre-transaction states for positions successfully written by this task.
     * One record per {@link BlockPos}; insertion order follows first successful write.
     */
    public List<BakeUndoRecord> getUndoRecords() {
        List<BakeUndoRecord> out = new ArrayList<>(originalStates.size());
        for (Map.Entry<BlockPos, BlockState> entry : originalStates.entrySet()) {
            out.add(new BakeUndoRecord(entry.getKey(), entry.getValue()));
        }
        return out;
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
