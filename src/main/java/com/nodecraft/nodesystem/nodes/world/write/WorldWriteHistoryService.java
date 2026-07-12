package com.nodecraft.nodesystem.nodes.world.write;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores undo records for direct world.write operations.
 */
public final class WorldWriteHistoryService {
    private static final int MAX_UNDO_STACK_SIZE = 32;
    public static final UUID SERVER_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final WorldWriteHistoryService INSTANCE = new WorldWriteHistoryService();

    private final Map<UUID, ActorHistory> histories = new HashMap<>();

    private WorldWriteHistoryService() {
    }

    public static WorldWriteHistoryService getInstance() {
        return INSTANCE;
    }

    public static UUID resolveActorId(@Nullable ServerPlayerEntity player) {
        return player != null ? player.getUuid() : SERVER_ACTOR_ID;
    }

    public synchronized void push(UUID actorId, UndoRecord record) {
        historyFor(actorId).push(record);
    }

    public synchronized UndoRecord peek(UUID actorId) {
        return historyFor(actorId).peek();
    }

    public synchronized int size(UUID actorId) {
        return historyFor(actorId).size();
    }

    public synchronized int redoSize(UUID actorId) {
        return historyFor(actorId).redoSize();
    }

    public synchronized boolean undoLast(UUID actorId, World world) {
        return historyFor(actorId).undoLast(world);
    }

    public synchronized boolean redoLast(UUID actorId, World world) {
        return historyFor(actorId).redoLast(world);
    }

    public synchronized void clear(UUID actorId) {
        historyFor(actorId).clear();
    }

    private ActorHistory historyFor(UUID actorId) {
        UUID resolvedActorId = actorId != null ? actorId : SERVER_ACTOR_ID;
        return histories.computeIfAbsent(resolvedActorId, ignored -> new ActorHistory());
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
                undoStack.remove(0);
            }
        }

        private UndoRecord peek() {
            return undoStack.isEmpty() ? null : undoStack.get(undoStack.size() - 1);
        }

        private int size() {
            return undoStack.size();
        }

        private int redoSize() {
            return redoStack.size();
        }

        private boolean undoLast(World world) {
            UndoRecord record = undoStack.isEmpty() ? null : undoStack.remove(undoStack.size() - 1);
            if (record == null || world == null) {
                return false;
            }
            UndoRecord redoRecord = record.applyAndCaptureInverse(world);
            if (redoRecord != null && redoRecord.size() > 0) {
                redoStack.add(redoRecord);
                trimRedoStack();
            }
            return true;
        }

        private boolean redoLast(World world) {
            UndoRecord record = redoStack.isEmpty() ? null : redoStack.remove(redoStack.size() - 1);
            if (record == null || world == null) {
                return false;
            }
            UndoRecord undoRecord = record.applyAndCaptureInverse(world);
            if (undoRecord != null && undoRecord.size() > 0) {
                undoStack.add(undoRecord);
                while (undoStack.size() > MAX_UNDO_STACK_SIZE) {
                    undoStack.remove(0);
                }
            }
            return true;
        }

        private void clear() {
            undoStack.clear();
            redoStack.clear();
        }

        private void trimRedoStack() {
            while (redoStack.size() > MAX_UNDO_STACK_SIZE) {
                redoStack.remove(0);
            }
        }
    }

    public static final class UndoRecord {
        private final List<BlockPos> positions = new ArrayList<>();
        private final List<BlockState> previousStates = new ArrayList<>();

        public void add(BlockPos pos, BlockState previousState) {
            if (pos == null || previousState == null) {
                return;
            }
            positions.add(pos.toImmutable());
            previousStates.add(previousState);
        }

        public int size() {
            return positions.size();
        }

        public List<BlockPos> getPositions() {
            return Collections.unmodifiableList(positions);
        }

        public boolean apply(World world) {
            if (world == null) {
                return false;
            }
            for (int i = 0; i < positions.size(); i++) {
                world.setBlockState(positions.get(i), previousStates.get(i), 3);
            }
            return true;
        }

        private UndoRecord applyAndCaptureInverse(World world) {
            if (world == null) {
                return null;
            }
            UndoRecord inverse = new UndoRecord();
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                BlockState targetState = previousStates.get(i);
                BlockState currentState = world.getBlockState(pos);
                inverse.add(pos, currentState);
                world.setBlockState(pos, targetState, 3);
            }
            return inverse;
        }
    }
}
