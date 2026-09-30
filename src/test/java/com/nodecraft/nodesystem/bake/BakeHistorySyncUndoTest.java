package com.nodecraft.nodesystem.bake;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sync undo/redo partial-failure tests without Minecraft block registry bootstrap.
 * Uses opaque token states through {@link BakeHistory.TransactionalApply}.
 */
class BakeHistorySyncUndoTest {

    private BakeHistory history;
    private TokenWorld world;

    @BeforeEach
    void setUp() {
        history = new BakeHistory();
        world = new TokenWorld();
    }

    @Test
    void partialApplyFailureRollsBackWorldAndKeepsUndoStackSemantics() {
        BlockPos a = new BlockPos(0, 64, 0);
        BlockPos b = new BlockPos(1, 64, 0);
        BlockPos c = new BlockPos(2, 64, 0);
        world.set(a, "STONE");
        world.set(b, "STONE");
        world.set(c, "STONE");

        LinkedHashMap<BlockPos, String> originals = new LinkedHashMap<>();
        originals.put(a, "AIR");
        originals.put(b, "AIR");
        originals.put(c, "AIR");

        world.failWritesAt(b);

        BakeHistory.TransactionalApply.Result<String> result =
                BakeHistory.TransactionalApply.applyAllOrRollback(
                        originals,
                        world::get,
                        world::trySet);

        assertFalse(result.fullySucceeded());
        assertTrue(result.rollback().attempted());
        assertTrue(result.rollback().succeeded());
        assertEquals("STONE", world.get(a));
        assertEquals("STONE", world.get(b));
        assertEquals("STONE", world.get(c));

        // Stack must remain when apply is not fully successful (mirrors undoLast).
        history.push(BakeHistory.UndoRecord.syntheticForStackTest(UUID.randomUUID()));
        assertEquals(1, history.size());
    }

    @Test
    void successfulTransactionalApplyRestoresAllTokens() {
        BlockPos a = new BlockPos(0, 64, 0);
        world.set(a, "GOLD");

        LinkedHashMap<BlockPos, String> originals = new LinkedHashMap<>();
        originals.put(a, "AIR");

        BakeHistory.TransactionalApply.Result<String> result =
                BakeHistory.TransactionalApply.applyAllOrRollback(
                        originals,
                        world::get,
                        world::trySet);

        assertTrue(result.fullySucceeded());
        assertEquals("AIR", world.get(a));
        assertEquals(1, result.inverse().size());
        assertEquals("GOLD", result.inverse().get(a));
    }

    @Test
    void restoreOrderIsLifoSoLaterFailureStillRollsEarlierSuccess() {
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(1, 64, 0);
        world.set(first, "STONE");
        world.set(second, "STONE");

        LinkedHashMap<BlockPos, String> originals = new LinkedHashMap<>();
        originals.put(first, "AIR");
        originals.put(second, "AIR");

        // LIFO restore: second first, then first. Fail on first after second succeeded.
        world.failWritesAt(first);

        BakeHistory.TransactionalApply.Result<String> result =
                BakeHistory.TransactionalApply.applyAllOrRollback(
                        originals,
                        world::get,
                        world::trySet);

        assertFalse(result.fullySucceeded());
        assertEquals("STONE", world.get(first));
        assertEquals("STONE", world.get(second), "second must be rolled back after first fails");
    }

    private static final class TokenWorld {
        private final Map<BlockPos, String> states = new HashMap<>();
        private final Set<BlockPos> failWrites = new HashSet<>();

        void set(BlockPos pos, String state) {
            states.put(pos.toImmutable(), state);
        }

        String get(BlockPos pos) {
            return states.getOrDefault(pos, "AIR");
        }

        boolean trySet(BlockPos pos, String state) {
            if (failWrites.contains(pos.toImmutable())) {
                return false;
            }
            states.put(pos.toImmutable(), state);
            return true;
        }

        void failWritesAt(BlockPos pos) {
            failWrites.add(pos.toImmutable());
        }
    }
}
