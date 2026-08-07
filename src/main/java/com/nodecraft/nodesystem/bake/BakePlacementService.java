package com.nodecraft.nodesystem.bake;

import com.nodecraft.core.NodeCraft;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-tick driven placement queue for large block writes.
 */
public class BakePlacementService {

    public static final int DEFAULT_BLOCKS_PER_TICK = 2000;
    public static final long DEFAULT_TICK_BUDGET_NANOS = 4_000_000L;

    public static final UUID SERVER_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static BakePlacementService instance;

    private static final int RECENT_TASK_SNAPSHOT_LIMIT = 128;

    private final Deque<BakeTask> queue = new ArrayDeque<>();
    private final Map<UUID, BakeHistory> histories = new HashMap<>();
    private final Map<UUID, TaskSnapshot> recentTaskSnapshots = new LinkedHashMap<>(
        RECENT_TASK_SNAPSHOT_LIMIT,
        0.75f,
        true
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, TaskSnapshot> eldest) {
            return size() > RECENT_TASK_SNAPSHOT_LIMIT;
        }
    };
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

    public static UUID resolveActorId(@Nullable ServerPlayerEntity player) {
        return player != null ? player.getUuid() : SERVER_ACTOR_ID;
    }

    public UUID enqueue(World world,
                        List<BlockPos> positions,
                        BlockState targetState,
                        PlacementMode mode,
                        boolean recordUndo,
                        int blocksPerTick,
                        @Nullable UUID actorId,
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
        return enqueuePlacements(world, placements, mode, recordUndo, BakeOperationKind.APPLY, blocksPerTick, defaultTickBudgetNanos, actorId, onComplete);
    }

    /**
     * Enqueue placements with explicit operation kind for history semantics.
     * This is the preferred method for undo/redo operations.
     */
    public UUID enqueuePlacements(World world,
                                  List<BakeTask.Placement> placements,
                                  PlacementMode mode,
                                  boolean recordUndo,
                                  BakeOperationKind operationKind,
                                  int blocksPerTick,
                                  long tickBudgetNanos,
                                  @Nullable UUID actorId,
                                  Runnable onComplete) {
        return enqueuePlacements(
            world,
            placements,
            mode,
            recordUndo,
            operationKind,
            blocksPerTick,
            tickBudgetNanos,
            actorId,
            onComplete,
            null
        );
    }

    public UUID enqueuePlacements(World world,
                                  List<BakeTask.Placement> placements,
                                  PlacementMode mode,
                                  boolean recordUndo,
                                  BakeOperationKind operationKind,
                                  int blocksPerTick,
                                  long tickBudgetNanos,
                                  @Nullable UUID actorId,
                                  @Nullable Runnable onComplete,
                                  @Nullable Runnable onCancel) {
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
            operationKind,
            resolveBlocksPerTick(blocksPerTick),
            resolveTickBudgetNanos(tickBudgetNanos),
            resolveActorId(actorId),
            onComplete,
            onCancel
        );
        synchronized (queue) {
            queue.addLast(task);
        }
        NodeCraft.LOGGER.debug("Queued {} bake task {} with {} placements",
                              operationKind, taskId, placements.size());
        return taskId;
    }

    /**
     * Legacy method - defaults to APPLY operation kind.
     */
    public UUID enqueuePlacements(World world,
                                  List<BakeTask.Placement> placements,
                                  PlacementMode mode,
                                  boolean recordUndo,
                                  int blocksPerTick,
                                  long tickBudgetNanos,
                                  @Nullable UUID actorId,
                                  Runnable onComplete) {
        return enqueuePlacements(world, placements, mode, recordUndo, 
                                BakeOperationKind.APPLY, blocksPerTick, 
                                tickBudgetNanos, actorId, onComplete);

    }
    public void processTick() {
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
        return cancelTask(taskId, BakeTaskState.CANCELLED);
    }

    /**
     * Cancels a queued/running task and rolls back its in-world progress.
     *
     * @param terminalState {@link BakeTaskState#CANCELLED} or {@link BakeTaskState#TIMED_OUT}
     */
    public boolean cancelTask(UUID taskId, BakeTaskState terminalState) {
        if (taskId == null) {
            return false;
        }

        BakeTask task = null;
        synchronized (queue) {
            for (BakeTask candidate : queue) {
                if (taskId.equals(candidate.getTaskId())) {
                    task = candidate;
                    break;
                }
            }
            if (task == null) {
                return false;
            }
            task.requestCancel(terminalState != null ? terminalState : BakeTaskState.CANCELLED);
            queue.remove(task);
        }

        finalizeCancelledTask(task);
        NodeCraft.LOGGER.info("Cancelled bake task {} ({})", taskId, task.getState());
        return true;
    }

    public int cancelAll() {
        List<BakeTask> cancelledTasks;
        synchronized (queue) {
            cancelledTasks = new ArrayList<>(queue);
            for (BakeTask task : cancelledTasks) {
                task.requestCancel(BakeTaskState.CANCELLED);
            }
            queue.clear();
        }

        for (BakeTask task : cancelledTasks) {
            finalizeCancelledTask(task);
        }

        if (!cancelledTasks.isEmpty()) {
            NodeCraft.LOGGER.info("Cancelled {} queued bake tasks", cancelledTasks.size());
        }
        return cancelledTasks.size();
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

    /**
     * Returns the live or most recently finished snapshot for a bake task.
     */
    @Nullable
    public TaskSnapshot getTaskSnapshot(UUID taskId) {
        if (taskId == null) {
            return null;
        }

        synchronized (queue) {
            for (BakeTask task : queue) {
                if (taskId.equals(task.getTaskId())) {
                    return TaskSnapshot.from(task);
                }
            }
        }

        synchronized (recentTaskSnapshots) {
            return recentTaskSnapshots.get(taskId);
        }
    }

    /**
     * Drains the bake queue until the task finishes or the deadline is reached.
     * Must run on the Minecraft server thread.
     */
    public boolean awaitTaskCompletion(UUID taskId, long deadlineMillis) {
        if (taskId == null) {
            return false;
        }

        long deadline = deadlineMillis > 0L ? deadlineMillis : Long.MAX_VALUE;
        while (System.currentTimeMillis() < deadline) {
            processTick();
            if (isTaskFinished(taskId)) {
                TaskSnapshot snapshot = getTaskSnapshot(taskId);
                return snapshot != null && snapshot.state() == BakeTaskState.COMPLETED;
            }
        }
        return false;
    }

    private boolean isTaskFinished(UUID taskId) {
        synchronized (queue) {
            for (BakeTask task : queue) {
                if (taskId.equals(task.getTaskId())) {
                    return false;
                }
            }
        }
        synchronized (recentTaskSnapshots) {
            return recentTaskSnapshots.containsKey(taskId);
        }
    }

    private void rememberTaskSnapshot(BakeTask task) {
        if (task == null) {
            return;
        }
        TaskSnapshot snapshot = TaskSnapshot.from(task);
        synchronized (recentTaskSnapshots) {
            recentTaskSnapshots.put(task.getTaskId(), snapshot);
        }
    }

    public BakeHistory getHistory(UUID actorId) {
        UUID resolvedActorId = resolveActorId(actorId);
        synchronized (histories) {
            return histories.computeIfAbsent(resolvedActorId, ignored -> new BakeHistory());
        }
    }

    public boolean undoLast(UUID actorId, World world) {
        if (world == null) {
            return false;
        }
        BakeHistory history = getHistory(actorId);
        boolean success = history.undoLast(world);
        if (success) {
            NodeCraft.LOGGER.debug("Undid last baked transaction for actor {}", resolveActorId(actorId));
        }
        return success;
    }

    /**
     * Asynchronously undo the last baked transaction for the given actor.
     * This uses the tick-sliced system to prevent server lag on large builds.
     *
     * @param actorId Actor performing the undo
     * @param world Target world
     * @return Task ID if undo was queued, null if nothing to undo
     */
    public UUID undoLastAsync(UUID actorId, World world) {
        return undoLastAsync(actorId, world, defaultBlocksPerTick, defaultTickBudgetNanos);
    }

    /**
     * Asynchronously undo the last baked transaction for the given actor with custom settings.
     *
     * @param actorId Actor performing the undo
     * @param world Target world
     * @param blocksPerTick Maximum blocks to restore per tick
     * @param tickBudgetNanos Maximum time budget per tick in nanoseconds
     * @return Task ID if undo was queued, null if nothing to undo
     */
    public UUID undoLastAsync(UUID actorId, World world, int blocksPerTick, long tickBudgetNanos) {
        if (world == null) {
            return null;
        }
        BakeHistory history = getHistory(actorId);
        UUID taskId = history.undoLastAsync(resolveActorId(actorId), world, blocksPerTick, tickBudgetNanos);
        if (taskId != null) {
            NodeCraft.LOGGER.debug("Queued async undo for actor {} (task: {})", resolveActorId(actorId), taskId);
        }
        return taskId;
    }

    public boolean redoLast(UUID actorId, World world) {
        if (world == null) {
            return false;
        }
        BakeHistory history = getHistory(actorId);
        boolean success = history.redoLast(world);
        if (success) {
            NodeCraft.LOGGER.debug("Redid last baked transaction for actor {}", resolveActorId(actorId));
        }
        return success;
    }

    /**
     * Asynchronously redo the last undone transaction for the given actor.
     * This uses the tick-sliced system to prevent server lag on large builds.
     *
     * @param actorId Actor performing the redo
     * @param world Target world
     * @return Task ID if redo was queued, null if nothing to redo
     */
    public UUID redoLastAsync(UUID actorId, World world) {
        return redoLastAsync(actorId, world, defaultBlocksPerTick, defaultTickBudgetNanos);
    }

    /**
     * Asynchronously redo the last undone transaction for the given actor with custom settings.
     *
     * @param actorId Actor performing the redo
     * @param world Target world
     * @param blocksPerTick Maximum blocks to restore per tick
     * @param tickBudgetNanos Maximum time budget per tick in nanoseconds
     * @return Task ID if redo was queued, null if nothing to redo
     */
    public UUID redoLastAsync(UUID actorId, World world, int blocksPerTick, long tickBudgetNanos) {
        if (world == null) {
            return null;
        }
        BakeHistory history = getHistory(actorId);
        UUID taskId = history.redoLastAsync(resolveActorId(actorId), world, blocksPerTick, tickBudgetNanos);
        if (taskId != null) {
            NodeCraft.LOGGER.debug("Queued async redo for actor {} (task: {})", resolveActorId(actorId), taskId);
        }
        return taskId;
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
        rememberTaskSnapshot(task);
        if (task.getState().isAbort()
            || task.getState() == BakeTaskState.CANCELLING
            || task.getState() == BakeTaskState.ROLLING_BACK) {
            // Cancel path owns rollback / stack restore via finalizeCancelledTask.
            return;
        }

        commitTaskHistory(task);

        if (task.getOnComplete() != null) {
            task.getOnComplete().run();
        }

        NodeCraft.LOGGER.debug(
            "Bake task {} ({}) completed. placed={}, skipped={}, total={}",
            task.getTaskId(),
            task.getOperationKind(),
            task.getPlacedCount(),
            task.getSkippedCount(),
            task.getTotalCount()
        );
    }

    /**
     * Cancel / timeout = rollback current task (database-style abort).
     * <ol>
     *   <li>Restore world using captured previous states</li>
     *   <li>Restore UNDO/REDO stacks via {@link BakeTask#getOnCancel()} when present</li>
     *   <li>Do not commit history — World and History stay aligned</li>
     * </ol>
     * Large synchronous rollbacks may lag a tick; a future {@code RollbackTask} can time-slice this.
     */
    private void finalizeCancelledTask(BakeTask task) {
        if (!task.getUndoRecords().isEmpty()) {
            task.rollback();
        }
        if (task.getOnCancel() != null) {
            task.getOnCancel().run();
        }
        task.markAborted();
        rememberTaskSnapshot(task);

        NodeCraft.LOGGER.debug(
            "Bake task {} ({}) {} after rollback. placed={}, skipped={}, total={}",
            task.getTaskId(),
            task.getOperationKind(),
            task.getState(),
            task.getPlacedCount(),
            task.getSkippedCount(),
            task.getTotalCount()
        );
    }

    private void commitTaskHistory(BakeTask task) {
        if (!task.isRecordUndo() || task.getUndoRecords().isEmpty()) {
            return;
        }

        BakeHistory.UndoRecord record = new BakeHistory.UndoRecord(task.getTaskId());
        for (BakeTask.BakeUndoRecord ur : task.getUndoRecords()) {
            record.add(ur.pos(), ur.previousState());
        }

        BakeHistory history = getHistory(task.getActorId());
        switch (task.getOperationKind()) {
            case APPLY -> history.push(record);
            case UNDO -> history.pushRedo(record);
            case REDO -> history.pushUndo(record);
            case NONE -> { /* No history recording */ }
        }
    }

    private UUID resolveActorId(@Nullable UUID actorId) {
        return actorId != null ? actorId : SERVER_ACTOR_ID;
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
                               BakeTaskState state) {
        static TaskSnapshot from(BakeTask task) {
            return new TaskSnapshot(
                task.getTaskId(),
                task.getPlacedCount(),
                task.getSkippedCount(),
                task.getTotalCount(),
                task.getRemainingCount(),
                task.getProgress(),
                task.getState()
            );
        }

        public boolean cancelled() {
            return state != null && state.isAbort();
        }

        public String resolveState() {
            return state != null ? state.displayName() : BakeTaskState.FAILED.displayName();
        }
    }
}
