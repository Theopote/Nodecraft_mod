package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.execution.ExecutionContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores undo records for direct world.write operations, keyed by (actor, worldKey).
 */
public final class WorldWriteHistoryService {
    private static final int MAX_UNDO_STACK_SIZE = 32;
    public static final UUID SERVER_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final WorldWriteHistoryService INSTANCE = new WorldWriteHistoryService();

    private final Map<HistoryKey, ActorHistory> histories = new HashMap<>();

    private WorldWriteHistoryService() {
    }

    public static WorldWriteHistoryService getInstance() {
        return INSTANCE;
    }

    public static UUID resolveActorId(@Nullable ServerPlayerEntity player) {
        return player != null ? player.getUuid() : SERVER_ACTOR_ID;
    }

    public synchronized void push(UUID actorId, String worldKey, UndoRecord record) {
        historyFor(actorId, worldKey).push(record);
    }

    public synchronized UndoRecord peek(UUID actorId, String worldKey) {
        return historyFor(actorId, worldKey).peek();
    }

    public synchronized UndoRecord peek(UUID actorId, World world) {
        return peek(actorId, WorldWriteUtils.worldKey(world));
    }

    public synchronized int size(UUID actorId, String worldKey) {
        return historyFor(actorId, worldKey).size();
    }

    public synchronized int size(UUID actorId, World world) {
        return size(actorId, WorldWriteUtils.worldKey(world));
    }

    public synchronized int redoSize(UUID actorId, String worldKey) {
        return historyFor(actorId, worldKey).redoSize();
    }

    public synchronized int redoSize(UUID actorId, World world) {
        return redoSize(actorId, WorldWriteUtils.worldKey(world));
    }

    /**
     * Undo last write for this actor. Requires {@link ExecutionContext} so inverse snapshots
     * can capture block-entity NBT via the world's registry manager.
     */
    public synchronized UndoApplyResult undoLast(UUID actorId, ExecutionContext context) {
        if (context == null || context.getWorld() == null) {
            return UndoApplyResult.failed("Missing world");
        }
        String key = WorldWriteUtils.worldKey(context.getWorld());
        return historyFor(actorId, key).undoLast(context, key);
    }

    public synchronized UndoApplyResult redoLast(UUID actorId, ExecutionContext context) {
        if (context == null || context.getWorld() == null) {
            return UndoApplyResult.failed("Missing world");
        }
        String key = WorldWriteUtils.worldKey(context.getWorld());
        return historyFor(actorId, key).redoLast(context, key);
    }

    public synchronized void clear(UUID actorId, String worldKey) {
        historyFor(actorId, worldKey).clear();
    }

    public synchronized void clear(UUID actorId) {
        UUID resolved = actorId != null ? actorId : SERVER_ACTOR_ID;
        histories.entrySet().removeIf(entry -> entry.getKey().actorId.equals(resolved));
    }

    private ActorHistory historyFor(UUID actorId, String worldKey) {
        UUID resolvedActorId = actorId != null ? actorId : SERVER_ACTOR_ID;
        String resolvedWorld = worldKey == null || worldKey.isBlank() ? "unknown" : worldKey;
        return histories.computeIfAbsent(new HistoryKey(resolvedActorId, resolvedWorld), ignored -> new ActorHistory());
    }

    private record HistoryKey(UUID actorId, String worldKey) {
        HistoryKey {
            Objects.requireNonNull(actorId);
            worldKey = worldKey == null ? "unknown" : worldKey;
        }
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

    private static final class ActorHistory {
        private final List<UndoRecord> undoStack = new ArrayList<>();
        private final List<UndoRecord> redoStack = new ArrayList<>();

        private void push(UndoRecord record) {
            if (record == null || record.size() == 0) {
                return;
            }
            undoStack.add(record);
            redoStack.clear();
            while (undoStack.size() > MAX_UNDO_STACK_SIZE) {
                undoStack.removeFirst();
            }
        }

        private UndoRecord peek() {
            return undoStack.isEmpty() ? null : undoStack.getLast();
        }

        private int size() {
            return undoStack.size();
        }

        private int redoSize() {
            return redoStack.size();
        }

        private UndoApplyResult undoLast(ExecutionContext context, String expectedWorldKey) {
            if (undoStack.isEmpty()) {
                return UndoApplyResult.failed("Nothing to undo");
            }
            UndoRecord record = undoStack.getLast();
            if (!expectedWorldKey.equals(record.worldKey())) {
                return UndoApplyResult.failed("Undo record world mismatch");
            }
            undoStack.removeLast();
            ApplyOutcome outcome = record.applyAndCaptureInverse(context);
            if (outcome.inverse() != null && outcome.inverse().size() > 0) {
                if (outcome.failureCount() > 0) {
                    undoStack.add(record.withoutApplied(outcome.appliedIndices()));
                }
                redoStack.add(outcome.inverse());
                trimRedoStack();
            } else if (outcome.failureCount() > 0) {
                undoStack.add(record);
                return UndoApplyResult.failed("Undo failed to apply");
            }
            return UndoApplyResult.ok(outcome.successCount(), outcome.failureCount());
        }

        private UndoApplyResult redoLast(ExecutionContext context, String expectedWorldKey) {
            if (redoStack.isEmpty()) {
                return UndoApplyResult.failed("Nothing to redo");
            }
            UndoRecord record = redoStack.getLast();
            if (!expectedWorldKey.equals(record.worldKey())) {
                return UndoApplyResult.failed("Redo record world mismatch");
            }
            redoStack.removeLast();
            ApplyOutcome outcome = record.applyAndCaptureInverse(context);
            if (outcome.inverse() != null && outcome.inverse().size() > 0) {
                if (outcome.failureCount() > 0) {
                    redoStack.add(record.withoutApplied(outcome.appliedIndices()));
                }
                undoStack.add(outcome.inverse());
                while (undoStack.size() > MAX_UNDO_STACK_SIZE) {
                    undoStack.removeFirst();
                }
            } else if (outcome.failureCount() > 0) {
                redoStack.add(record);
                return UndoApplyResult.failed("Redo failed to apply");
            }
            return UndoApplyResult.ok(outcome.successCount(), outcome.failureCount());
        }

        private void clear() {
            undoStack.clear();
            redoStack.clear();
        }

        private void trimRedoStack() {
            while (redoStack.size() > MAX_UNDO_STACK_SIZE) {
                redoStack.removeFirst();
            }
        }
    }

    private record ApplyOutcome(UndoRecord inverse, int successCount, int failureCount, List<Integer> appliedIndices) {
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

        /** Compatibility: state-only snapshot (no BE NBT). */
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

        /** Package-visible for contract tests asserting NBT restore failure accounting. */
        List<BlockSnapshot> snapshots() {
            return Collections.unmodifiableList(snapshots);
        }

        UndoRecord withoutApplied(List<Integer> appliedIndices) {
            UndoRecord remaining = new UndoRecord(worldKey);
            java.util.HashSet<Integer> applied = new java.util.HashSet<>(appliedIndices);
            for (int i = 0; i < snapshots.size(); i++) {
                if (!applied.contains(i)) {
                    remaining.add(snapshots.get(i));
                }
            }
            return remaining;
        }

        public boolean apply(ExecutionContext context) {
            return applyAndCaptureInverse(context).failureCount() == 0;
        }

        /**
         * Restores each snapshot. Inverse entries are captured only for cells that fully
         * restored (state + optional BE NBT). NBT restore failure → failureCount.
         */
        private ApplyOutcome applyAndCaptureInverse(ExecutionContext context) {
            if (context == null || context.getWorld() == null) {
                return new ApplyOutcome(null, 0, snapshots.size(), List.of());
            }
            World world = context.getWorld();
            UndoRecord inverse = new UndoRecord(worldKey);
            int success = 0;
            int failure = 0;
            List<Integer> applied = new ArrayList<>();
            for (int i = 0; i < snapshots.size(); i++) {
                BlockSnapshot target = snapshots.get(i);
                BlockPos pos = target.pos();
                BlockSnapshot current = WorldWriteTransaction.captureCurrent(context, pos);
                if (current == null) {
                    failure++;
                    continue;
                }
                boolean placed = world.setBlockState(pos, target.state(), 3);
                if (!placed) {
                    failure++;
                    continue;
                }
                NbtCompound targetNbt = target.blockEntityNbt();
                if (targetNbt != null) {
                    BlockEntity restored = world.getBlockEntity(pos);
                    if (restored == null) {
                        failure++;
                        continue;
                    }
                    if (!WorldWriteNbtUtils.applyBlockEntityNbt(restored, targetNbt, context)) {
                        failure++;
                        continue;
                    }
                    restored.markDirty();
                }
                inverse.add(current);
                success++;
                applied.add(i);
            }
            return new ApplyOutcome(inverse, success, failure, applied);
        }

        /**
         * Pure outcome classifier for NBT restore accounting (unit-tested without a live world).
         *
         * @return {@code true} when the cell counts as a full success
         */
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
}
