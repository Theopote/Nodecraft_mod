package com.nodecraft.nodesystem.bake;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 烘焙历史记录，用于撤销 (Undo) 操作。
 * 记录每次 Bake 时被覆盖的原有方块信息。
 */
public class BakeHistory {

    /**
     * 单次 Bake 操作的 Undo 记录
     */
    public static class UndoRecord {
        private final UUID bakeId;
        private final List<BlockPos> positions = new ArrayList<>();
        private final List<BlockState> previousStates = new ArrayList<>();

        public UndoRecord(UUID bakeId) {
            this.bakeId = bakeId;
        }

        public void add(BlockPos pos, BlockState previousState) {
            positions.add(pos.toImmutable());
            previousStates.add(previousState);
        }

        public int size() {
            return positions.size();
        }

        /** 应用撤销：恢复原有方块 */
        public void apply(World world) {
            if (world == null) return;
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                BlockState prev = previousStates.get(i);
                if (prev != null && pos != null) {
                    world.setBlockState(pos, prev, 3); // Block.NOTIFY_ALL
                }
            }
            positions.clear();
            previousStates.clear();
        }

        public UUID getBakeId() { return bakeId; }
        public List<BlockPos> getPositions() { return Collections.unmodifiableList(positions); }
        public List<BlockState> getPreviousStates() { return Collections.unmodifiableList(previousStates); }
    }

    private static final int MAX_UNDO_STACK_SIZE = 32;
    private final List<UndoRecord> undoStack = new ArrayList<>();

    /** 压入一条 Undo 记录 */
    public void push(UndoRecord record) {
        if (record == null || record.size() == 0) return;
        undoStack.add(record);
        while (undoStack.size() > MAX_UNDO_STACK_SIZE) {
            undoStack.remove(0);
        }
    }

    /** 弹出并返回最后一条记录，用于撤销 */
    public UndoRecord pop() {
        return undoStack.isEmpty() ? null : undoStack.remove(undoStack.size() - 1);
    }

    /** 获取最后一条记录（不移除） */
    public UndoRecord peek() {
        return undoStack.isEmpty() ? null : undoStack.get(undoStack.size() - 1);
    }

    public boolean hasUndo() { return !undoStack.isEmpty(); }
    public int size() { return undoStack.size(); }
    public void clear() { undoStack.clear(); }
}
