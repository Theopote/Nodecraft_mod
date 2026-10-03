package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.bake.BakeHistory;
import com.nodecraft.nodesystem.bake.BakePlacementService;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Facade over {@link BakePlacementService} history. World.write undo nodes share the
 * same actor+world stack as Apply Changes.
 */
public final class WorldWriteHistoryService {
    public static final UUID SERVER_ACTOR_ID = BakePlacementService.SERVER_ACTOR_ID;
    private static final WorldWriteHistoryService INSTANCE = new WorldWriteHistoryService();

    private WorldWriteHistoryService() {
    }

    public static WorldWriteHistoryService getInstance() {
        return INSTANCE;
    }

    public static UUID resolveActorId(@Nullable ServerPlayerEntity player) {
        return BakePlacementService.resolveActorId(player);
    }

    public synchronized void push(UUID actorId, String worldKey, UndoRecord record) {
        if (record == null || record.size() == 0) {
            return;
        }
        BakeHistory.UndoRecord bakeRecord = toBake(record);
        BakePlacementService.getInstance().getHistory(actorId, worldKey).push(bakeRecord);
    }

    public synchronized UndoRecord peek(UUID actorId, String worldKey) {
        BakeHistory.UndoRecord bake = BakePlacementService.getInstance().getHistory(actorId, worldKey).peek();
        return bake == null ? null : fromBake(bake, worldKey);
    }

    public synchronized UndoRecord peek(UUID actorId, World world) {
        return peek(actorId, BakePlacementService.worldKey(world));
    }

    public synchronized int size(UUID actorId, String worldKey) {
        return BakePlacementService.getInstance().getHistory(actorId, worldKey).size();
    }

    public synchronized int size(UUID actorId, World world) {
        return size(actorId, BakePlacementService.worldKey(world));
    }

    public synchronized int redoSize(UUID actorId, String worldKey) {
        return BakePlacementService.getInstance().getHistory(actorId, worldKey).redoSize();
    }

    public synchronized int redoSize(UUID actorId, World world) {
        return redoSize(actorId, BakePlacementService.worldKey(world));
    }

    public synchronized UndoApplyResult undoLast(UUID actorId, ExecutionContext context) {
        if (context == null || context.getWorld() == null) {
            return UndoApplyResult.failed("Missing world");
        }
        BakePlacementService service = BakePlacementService.getInstance();
        BakeHistory history = service.getHistory(actorId, context.getWorld());
        BakeHistory.UndoRecord peek = history.peek();
        if (peek == null || peek.size() == 0) {
            return UndoApplyResult.failed("Nothing to undo");
        }
        int expected = peek.size();
        boolean success = service.undoLast(actorId, context.getWorld());
        if (!success) {
            return UndoApplyResult.failed("Undo failed to apply");
        }
        return UndoApplyResult.ok(expected, 0);
    }

    public synchronized UndoApplyResult redoLast(UUID actorId, ExecutionContext context) {
        if (context == null || context.getWorld() == null) {
            return UndoApplyResult.failed("Missing world");
        }
        BakePlacementService service = BakePlacementService.getInstance();
        BakeHistory history = service.getHistory(actorId, context.getWorld());
        int expected = history.redoSize() > 0 ? 1 : 0;
        boolean success = service.redoLast(actorId, context.getWorld());
        if (!success) {
            return UndoApplyResult.failed("Redo failed to apply");
        }
        return UndoApplyResult.ok(Math.max(expected, 1), 0);
    }

    public synchronized void clear(UUID actorId, String worldKey) {
        BakePlacementService.getInstance().clearHistory(actorId, worldKey);
    }

    public synchronized void clear(UUID actorId) {
        BakePlacementService.getInstance().clearHistory(actorId);
    }

    static void trimUndoStack(List<BakeHistory.UndoRecord> stack) {
        BakeHistory.snapshotCount(stack);
    }

    static int snapshotCount(List<BakeHistory.UndoRecord> stack) {
        return BakeHistory.snapshotCount(stack);
    }

    public record UndoApplyResult(boolean success, boolean complete, int successCount, int failureCount, String error) {
        static UndoApplyResult failed(String error) {
            return new UndoApplyResult(false, false, 0, 0, error == null ? "" : error);
        }

        static UndoApplyResult ok(int successCount, int failureCount) {
            boolean complete = failureCount == 0;
            return new UndoApplyResult(true, complete, successCount, failureCount, complete ? "" : "Partial undo/redo");
        }
    }

    public static final class UndoRecord {
        private final String worldKey;
        private final List<BlockSnapshot> snapshots = new ArrayList<>();

        public UndoRecord(String worldKey) {
            this.worldKey = worldKey == null ? "unknown" : worldKey;
        }

        public String worldKey() {
            return worldKey;
        }

        public void add(BlockSnapshot snapshot) {
            if (snapshot == null) {
                return;
            }
            snapshots.add(snapshot);
        }

        public void add(BlockPos pos, BlockState previousState) {
            if (pos == null || previousState == null) {
                return;
            }
            snapshots.add(new BlockSnapshot(pos, previousState, null));
        }

        public int size() {
            return snapshots.size();
        }

        public List<BlockPos> getPositions() {
            List<BlockPos> positions = new ArrayList<>(snapshots.size());
            for (BlockSnapshot snapshot : snapshots) {
                positions.add(snapshot.pos());
            }
            return Collections.unmodifiableList(positions);
        }

        List<BlockSnapshot> snapshots() {
            return Collections.unmodifiableList(snapshots);
        }

        public boolean apply(ExecutionContext context) {
            return true;
        }

        static boolean isFullRestoreSuccess(
            boolean statePlaced,
            boolean targetHasNbt,
            boolean blockEntityPresent,
            boolean nbtApplySucceeded
        ) {
            if (!statePlaced) {
                return false;
            }
            if (!targetHasNbt) {
                return true;
            }
            return blockEntityPresent && nbtApplySucceeded;
        }
    }

    private static BakeHistory.UndoRecord toBake(UndoRecord record) {
        BakeHistory.UndoRecord bake = new BakeHistory.UndoRecord(UUID.randomUUID());
        for (BlockSnapshot snapshot : record.snapshots()) {
            bake.add(snapshot.pos(), snapshot.state(), snapshot.blockEntityNbt());
        }
        return bake;
    }

    private static UndoRecord fromBake(BakeHistory.UndoRecord bake, String worldKey) {
        UndoRecord record = new UndoRecord(worldKey);
        List<BlockPos> positions = bake.getPositions();
        List<BlockState> states = bake.getPreviousStates();
        List<NbtCompound> nbts = bake.getPreviousNbt();
        for (int i = 0; i < positions.size(); i++) {
            BlockState state = states.get(i);
            if (state == null) {
                continue;
            }
            record.add(new BlockSnapshot(positions.get(i), state, nbts.get(i)));
        }
        return record;
    }
}
