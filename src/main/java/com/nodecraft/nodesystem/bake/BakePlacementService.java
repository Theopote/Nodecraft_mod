package com.nodecraft.nodesystem.bake;

import com.nodecraft.core.NodeCraft;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Server-tick driven placement queue for large block writes.
 */
public class BakePlacementService {

    public static final int DEFAULT_BLOCKS_PER_TICK = 2000;
    public static final long DEFAULT_TICK_BUDGET_NANOS = 4_000_000L;

    private static BakePlacementService instance;

    private final Deque<BakeTask> queue = new ArrayDeque<>();
    private final BakeHistory history = new BakeHistory();
    private boolean tickRegistered = false;
    private int defaultBlocksPerTick = DEFAULT_BLOCKS_PER_TICK;
    private long defaultTickBudgetNanos = DEFAULT_TICK_BUDGET_NANOS;

    public static synchronized BakePlacementService getInstance() {
        if (instance == null) {
            instance = new BakePlacementService();
        }
        return instance;
    }

    public synchronized void registerTickHandler() {
        if (tickRegistered) {
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        tickRegistered = true;
        NodeCraft.LOGGER.debug("BakePlacementService registered with ServerTickEvents");
    }

    private void onServerTick(MinecraftServer server) {
        processTick();
    }

    public UUID enqueue(World world,
                        List<BlockPos> positions,
                        BlockState targetState,
                        PlacementMode mode,
                        boolean recordUndo,
                        int blocksPerTick,
                        Runnable onComplete) {
        if (positions == null || targetState == null) {
            return null;
        }
        List<BakeTask.Placement> placements = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (pos != null) {
                placements.add(new BakeTask.Placement(pos, targetState));
            }
        }
        return enqueuePlacements(world, placements, mode, recordUndo, blocksPerTick, defaultTickBudgetNanos, onComplete);
    }

    public UUID enqueuePlacements(World world,
                                  List<BakeTask.Placement> placements,
                                  PlacementMode mode,
                                  boolean recordUndo,
                                  int blocksPerTick,
                                  long tickBudgetNanos,
                                  Runnable onComplete) {
        if (world == null || placements == null || placements.isEmpty()) {
            NodeCraft.LOGGER.warn("BakePlacementService: invalid enqueue request");
            return null;
        }

        UUID taskId = UUID.randomUUID();
        BakeTask task = new BakeTask(
            taskId,
            world,
            placements,
            mode,
            recordUndo,
            resolveBlocksPerTick(blocksPerTick),
            resolveTickBudgetNanos(tickBudgetNanos),
            onComplete
        );
        synchronized (queue) {
            queue.addLast(task);
        }
        NodeCraft.LOGGER.debug("Queued bake task {} with {} placements", taskId, placements.size());
        return taskId;
    }

    void processTick() {
        long deadline = System.nanoTime() + defaultTickBudgetNanos;
        while (System.nanoTime() < deadline) {
            BakeTask task;
            synchronized (queue) {
                task = queue.peekFirst();
            }
            if (task == null) {
                return;
            }

            task.processTick();
            if (!task.isCompleted()) {
                return;
            }

            synchronized (queue) {
                if (queue.peekFirst() == task) {
                    queue.pollFirst();
                } else {
                    queue.remove(task);
                }
            }
            finishTask(task);
        }
    }

    public boolean cancelTask(UUID taskId) {
        if (taskId == null) {
            return false;
        }
        synchronized (queue) {
            for (BakeTask task : queue) {
                if (taskId.equals(task.getTaskId())) {
                    task.cancel();
                    queue.remove(task);
                    NodeCraft.LOGGER.info("Cancelled bake task {}", taskId);
                    return true;
                }
            }
        }
        return false;
    }

    public int cancelAll() {
        int count = 0;
        synchronized (queue) {
            for (BakeTask task : queue) {
                task.cancel();
                count++;
            }
            queue.clear();
        }
        if (count > 0) {
            NodeCraft.LOGGER.info("Cancelled {} queued bake tasks", count);
        }
        return count;
    }

    public List<TaskSnapshot> getTaskSnapshots() {
        synchronized (queue) {
            List<TaskSnapshot> snapshots = new ArrayList<>(queue.size());
            for (BakeTask task : queue) {
                snapshots.add(TaskSnapshot.from(task));
            }
            return snapshots;
        }
    }

    public BakeHistory getHistory() {
        return history;
    }

    public boolean undoLast(World world) {
        if (world == null) {
            return false;
        }
        boolean success = history.undoLast(world);
        if (success) {
            NodeCraft.LOGGER.debug("Undid last baked transaction");
        }
        return success;
    }

    public boolean redoLast(World world) {
        if (world == null) {
            return false;
        }
        boolean success = history.redoLast(world);
        if (success) {
            NodeCraft.LOGGER.debug("Redid last baked transaction");
        }
        return success;
    }

    public int getQueueSize() {
        synchronized (queue) {
            return queue.size();
        }
    }

    public int getDefaultBlocksPerTick() {
        return defaultBlocksPerTick;
    }

    public void setDefaultBlocksPerTick(int defaultBlocksPerTick) {
        this.defaultBlocksPerTick = Math.max(1, defaultBlocksPerTick);
    }

    public long getDefaultTickBudgetNanos() {
        return defaultTickBudgetNanos;
    }

    public void setDefaultTickBudgetNanos(long defaultTickBudgetNanos) {
        this.defaultTickBudgetNanos = Math.max(1L, defaultTickBudgetNanos);
    }

    private void finishTask(BakeTask task) {
        if (task.isCancelled()) {
            return;
        }
        if (!task.getUndoRecords().isEmpty()) {
            BakeHistory.UndoRecord record = new BakeHistory.UndoRecord(task.getTaskId());
            for (BakeTask.BakeUndoRecord ur : task.getUndoRecords()) {
                record.add(ur.pos(), ur.previousState());
            }
            history.push(record);
        }
        NodeCraft.LOGGER.debug(
            "Bake task {} completed. placed={}, skipped={}, total={}",
            task.getTaskId(),
            task.getPlacedCount(),
            task.getSkippedCount(),
            task.getTotalCount()
        );
    }

    private int resolveBlocksPerTick(int blocksPerTick) {
        return blocksPerTick > 0 ? blocksPerTick : defaultBlocksPerTick;
    }

    private long resolveTickBudgetNanos(long tickBudgetNanos) {
        return tickBudgetNanos > 0L ? tickBudgetNanos : defaultTickBudgetNanos;
    }

    public record TaskSnapshot(UUID taskId,
                               int placedCount,
                               int skippedCount,
                               int totalCount,
                               int remainingCount,
                               double progress,
                               boolean cancelled) {
        static TaskSnapshot from(BakeTask task) {
            return new TaskSnapshot(
                task.getTaskId(),
                task.getPlacedCount(),
                task.getSkippedCount(),
                task.getTotalCount(),
                task.getRemainingCount(),
                task.getProgress(),
                task.isCancelled()
            );
        }
    }
}
