package com.nodecraft.nodesystem.bake;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Time-sliced block placement task driven from the server tick.
 */
public class BakeTask {

    private final UUID taskId;
    private final World world;
    private final List<Placement> placements;
    private final PlacementMode placementMode;
    private final boolean recordUndo;
    private final int blocksPerTick;
    private final long timeBudgetNanos;
    private final Runnable onComplete;

    private final List<BakeUndoRecord> undoRecords = new ArrayList<>();
    private int nextIndex = 0;
    private boolean completed = false;
    private boolean cancelled = false;
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
        this(taskId, world, toPlacements(positions, targetState), placementMode, recordUndo, blocksPerTick, 0L, onComplete);
    }

    public BakeTask(UUID taskId,
                    World world,
                    List<Placement> placements,
                    PlacementMode placementMode,
                    boolean recordUndo,
                    int blocksPerTick,
                    long timeBudgetNanos,
                    Runnable onComplete) {
        this.taskId = taskId != null ? taskId : UUID.randomUUID();
        this.world = world;
        this.placements = copyPlacements(placements);
        this.placementMode = placementMode != null ? placementMode : PlacementMode.OVERWRITE;
        this.recordUndo = recordUndo;
        this.blocksPerTick = Math.max(1, blocksPerTick);
        this.timeBudgetNanos = Math.max(0L, timeBudgetNanos);
        this.onComplete = onComplete;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public World getWorld() {
        return world;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public int getPlacedCount() {
        return placedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public int getTotalCount() {
        return placements.size();
    }

    public int getRemainingCount() {
        return Math.max(0, placements.size() - nextIndex);
    }

    public double getProgress() {
        return placements.isEmpty() ? 1.0d : Math.min(1.0d, (double) nextIndex / (double) placements.size());
    }

    @SuppressWarnings("deprecation")
    public int processTick() {
        if (completed || cancelled || world == null) {
            return -1;
        }

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

            BlockState previous = recordUndo ? world.getBlockState(pos) : null;
            if (world.setBlockState(pos, targetState, Block.NOTIFY_ALL)) {
                placedThisTick++;
                placedCount++;
                if (recordUndo && previous != null) {
                    undoRecords.add(new BakeUndoRecord(pos, previous));
                }
            } else {
                skippedCount++;
            }
        }

        if (nextIndex >= placements.size()) {
            completed = true;
            if (onComplete != null) {
                onComplete.run();
            }
        }
        return placedThisTick;
    }

    public void cancel() {
        cancelled = true;
        completed = true;
    }

    public void undo() {
        for (BakeUndoRecord rec : undoRecords) {
            world.setBlockState(rec.pos(), rec.previousState(), Block.NOTIFY_ALL);
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
