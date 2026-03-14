package com.nodecraft.nodesystem.bake;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bake 放置任务
 * 表示一批待放置的方块，支持覆盖/增量模式及撤销记录
 */
public class BakeTask {

    private final UUID taskId;
    private final World world;
    private final List<BlockPos> positions;
    private final BlockState targetState;
    private final PlacementMode placementMode;
    private final boolean recordUndo;
    private final int blocksPerTick;
    private final Runnable onComplete;

    private final List<BakeUndoRecord> undoRecords = new ArrayList<>();
    private int nextIndex = 0;
    private boolean completed = false;
    private int placedCount = 0;

    /** 统一方块：所有坐标放置同一 BlockState */
    public BakeTask(UUID taskId, World world, List<BlockPos> positions, BlockState targetState,
                    PlacementMode placementMode, boolean recordUndo, int blocksPerTick, Runnable onComplete) {
        this.taskId = taskId != null ? taskId : UUID.randomUUID();
        this.world = world;
        this.positions = new ArrayList<>(positions);
        this.targetState = targetState;
        this.placementMode = placementMode;
        this.recordUndo = recordUndo;
        this.blocksPerTick = Math.max(1, blocksPerTick);
        this.onComplete = onComplete;
    }

    public UUID getTaskId() { return taskId; }
    public World getWorld() { return world; }
    public boolean isCompleted() { return completed; }
    public int getPlacedCount() { return placedCount; }
    public int getTotalCount() { return positions.size(); }

    /**
     * 处理本 tick 的放置工作
     * @return 本 tick 实际放置的方块数，若任务完成返回 -1
     */
    public int processTick() {
        if (completed || world == null || targetState == null) return -1;

        int limit = Math.min(nextIndex + blocksPerTick, positions.size());
        int placedThisTick = 0;

        for (int i = nextIndex; i < limit; i++) {
            BlockPos pos = positions.get(i);
            if (!world.isChunkLoaded(pos)) continue;

            if (placementMode == PlacementMode.INCREMENTAL) {
                if (!world.isAir(pos)) continue;  // 增量模式：仅空气位置放置
            }

            BlockState previous = null;
            if (recordUndo) {
                previous = world.getBlockState(pos);
            }

            if (world.setBlockState(pos.toImmutable(), targetState, net.minecraft.block.Block.NOTIFY_ALL)) {
                placedThisTick++;
                placedCount++;
                if (recordUndo && previous != null) {
                    undoRecords.add(new BakeUndoRecord(pos.toImmutable(), previous));
                }
            }
        }

        nextIndex = limit;
        if (nextIndex >= positions.size()) {
            completed = true;
            if (onComplete != null) {
                onComplete.run();
            }
            return placedThisTick;
        }
        return placedThisTick;
    }

    /**
     * 撤销本次 Bake 放置的方块
     */
    public void undo() {
        for (BakeUndoRecord rec : undoRecords) {
            world.setBlockState(rec.pos(), rec.previousState(), net.minecraft.block.Block.NOTIFY_ALL);
        }
    }

    public List<BakeUndoRecord> getUndoRecords() { return new ArrayList<>(undoRecords); }

    public record BakeUndoRecord(BlockPos pos, BlockState previousState) {}
}
