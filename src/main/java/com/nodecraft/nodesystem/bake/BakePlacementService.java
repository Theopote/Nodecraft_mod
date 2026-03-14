package com.nodecraft.nodesystem.bake;

import com.nodecraft.core.NodeCraft;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
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
 * Bake 放置服务：异步、分 tick 放置方块，避免主线程卡顿。
 * 每 tick 处理一批方块（默认 1000 个），由 ServerTickEvents 驱动。
 */
public class BakePlacementService {

    private static final int DEFAULT_BLOCKS_PER_TICK = 1000;
    private static BakePlacementService instance;
    private boolean tickRegistered = false;

    private final Deque<BakeTask> queue = new ArrayDeque<>();
    private final BakeHistory history = new BakeHistory();

    public static synchronized BakePlacementService getInstance() {
        if (instance == null) {
            instance = new BakePlacementService();
        }
        return instance;
    }

    /** 注册到 Fabric ServerTickEvents（应在 mod 初始化时调用一次） */
    public void registerTickHandler() {
        if (tickRegistered) return;
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        tickRegistered = true;
        NodeCraft.LOGGER.debug("BakePlacementService 已注册到 ServerTickEvents");
    }

    private void onServerTick(MinecraftServer server) {
        processTick();
    }

    /**
     * 提交一个 Bake 任务（由节点调用）
     *
     * @param world         世界
     * @param positions     要放置的坐标列表
     * @param targetState   目标方块状态
     * @param mode          放置模式（覆盖 / 增量）
     * @param recordUndo    是否记录 Undo
     * @param blocksPerTick 每 tick 处理数量
     * @param onComplete    完成回调（可为 null）
     * @return 任务 ID
     */
    public UUID enqueue(
            World world,
            List<BlockPos> positions,
            BlockState targetState,
            PlacementMode mode,
            boolean recordUndo,
            int blocksPerTick,
            Runnable onComplete) {

        if (world == null || positions == null || positions.isEmpty() || targetState == null) {
            NodeCraft.LOGGER.warn("BakePlacementService: 无效的 enqueue 参数");
            return null;
        }

        UUID taskId = UUID.randomUUID();
        BakeTask task = new BakeTask(taskId, world, new ArrayList<>(positions), targetState, mode,
                recordUndo, Math.max(1, blocksPerTick), onComplete);
        synchronized (queue) {
            queue.addLast(task);
        }
        NodeCraft.LOGGER.debug("Bake 任务已入队: {} 个方块, 模式={}", positions.size(), mode);
        return taskId;
    }

    /** 每 tick 处理一批 */
    void processTick() {
        BakeTask task;
        synchronized (queue) {
            task = queue.peekFirst();
        }
        if (task == null) return;

        task.processTick();

        if (task.isCompleted()) {
            synchronized (queue) {
                queue.pollFirst();
            }
            // 将 Undo 记录推入历史（供 UndoLastBakeNode 使用）
            if (!task.getUndoRecords().isEmpty()) {
                BakeHistory.UndoRecord record = new BakeHistory.UndoRecord(task.getTaskId());
                for (BakeTask.BakeUndoRecord ur : task.getUndoRecords()) {
                    record.add(ur.pos(), ur.previousState());
                }
                history.push(record);
            }
        }
    }

    public BakeHistory getHistory() {
        return history;
    }

    /** 撤销最后一次 Bake（由 UndoLastBakeNode 或 UI 调用） */
    public boolean undoLast(World world) {
        BakeHistory.UndoRecord record = history.pop();
        if (record == null || world == null) return false;
        record.apply(world);
        NodeCraft.LOGGER.debug("已撤销 {} 个方块", record.size());
        return true;
    }

    public int getQueueSize() {
        synchronized (queue) {
            return queue.size();
        }
    }
}
