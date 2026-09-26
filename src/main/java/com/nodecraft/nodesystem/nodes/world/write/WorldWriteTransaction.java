package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.execution.ExecutionContext;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates before-snapshots for a single world.write execution, then pushes undo history.
 * Callers must {@link #captureCurrent} before mutation, then {@link #recordSuccess} with that snapshot.
 */
public final class WorldWriteTransaction {

    private final String worldKey;
    private final List<BlockSnapshot> beforeSnapshots = new ArrayList<>();
    private int successCount;
    private int failureCount;
    private boolean hitLimit;
    private boolean complete = true;

    public WorldWriteTransaction(String worldKey) {
        this.worldKey = worldKey == null ? "unknown" : worldKey;
    }

    public String worldKey() {
        return worldKey;
    }

    public int successCount() {
        return successCount;
    }

    public int failureCount() {
        return failureCount;
    }

    public boolean isComplete() {
        return complete && !hitLimit && failureCount == 0;
    }

    public boolean hitLimit() {
        return hitLimit;
    }

    public void markHitLimit() {
        this.hitLimit = true;
        this.complete = false;
    }

    public void markIncomplete() {
        this.complete = false;
    }

    public boolean canAcceptMoreEntries() {
        return beforeSnapshots.size() < GenerationLimits.MAX_WORLD_WRITE_BLOCKS;
    }

    /**
     * Captures current block state + block-entity NBT at {@code pos} before any mutation.
     */
    public static @Nullable BlockSnapshot captureCurrent(ExecutionContext context, BlockPos pos) {
        if (context == null || context.getWorld() == null || pos == null) {
            return null;
        }
        World world = context.getWorld();
        BlockState state = world.getBlockState(pos);
        NbtCompound nbt = null;
        BlockEntity be = world.getBlockEntity(pos);
        if (be != null) {
            nbt = WorldWriteNbtUtils.extractBlockEntityNbt(be, context);
        }
        return new BlockSnapshot(pos, state, nbt);
    }

    /**
     * Records a successful mutation using a snapshot captured before the write.
     */
    public void recordSuccess(BlockSnapshot before) {
        if (before == null) {
            recordFailure();
            return;
        }
        beforeSnapshots.add(before);
        successCount++;
    }

    public void recordFailure() {
        failureCount++;
        complete = false;
    }

    public boolean hasEntries() {
        return !beforeSnapshots.isEmpty();
    }

    public WorldWriteHistoryService.UndoRecord toUndoRecord() {
        WorldWriteHistoryService.UndoRecord record = new WorldWriteHistoryService.UndoRecord(worldKey);
        for (BlockSnapshot snapshot : beforeSnapshots) {
            record.add(snapshot);
        }
        return record;
    }

    public void pushIfNeeded(ExecutionContext context, boolean recordUndo) {
        if (!recordUndo || !hasEntries() || context == null) {
            return;
        }
        WorldWriteHistoryService.getInstance().push(
            WorldWriteHistoryService.resolveActorId(context.getPlayer()),
            worldKey,
            toUndoRecord()
        );
    }
}
