package com.nodecraft.nodesystem.nodes.world.write;

import com.nodecraft.nodesystem.util.GenerationLimits;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldWriteHistoryServiceActorTest {

    @Test
    void resolveActorIdFallsBackToServerActorWhenPlayerMissing() {
        assertEquals(WorldWriteHistoryService.SERVER_ACTOR_ID, WorldWriteHistoryService.resolveActorId(null));
    }

    @Test
    void serverActorUsesSharedHistoryBucketPerWorld() {
        WorldWriteHistoryService service = WorldWriteHistoryService.getInstance();
        assertEquals(0, service.size(null, "minecraft:overworld"));
        assertEquals(0, service.size(WorldWriteHistoryService.SERVER_ACTOR_ID, "minecraft:overworld"));
    }

    @Test
    void distinctActorsHaveIndependentHistorySizes() {
        WorldWriteHistoryService service = WorldWriteHistoryService.getInstance();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        assertEquals(0, service.size(playerA, "minecraft:overworld"));
        assertEquals(0, service.size(playerB, "minecraft:overworld"));
        assertNotSame(playerA, playerB);
    }

    @Test
    void trimUndoStackDropsOldestBeyondRecordCap() {
        List<WorldWriteHistoryService.UndoRecord> stack = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            stack.add(new WorldWriteHistoryService.UndoRecord("minecraft:overworld"));
        }
        WorldWriteHistoryService.trimUndoStack(stack);
        assertEquals(32, stack.size());
        assertEquals(0, WorldWriteHistoryService.snapshotCount(stack));
    }

    @Test
    void undoTotalBlockCapMatchesBakeCeiling() throws Exception {
        assertEquals(262_144, GenerationLimits.MAX_UNDO_TOTAL_BLOCKS_PER_ACTOR);
        assertEquals(GenerationLimits.MAX_WORLD_WRITE_BLOCKS, GenerationLimits.MAX_UNDO_TOTAL_BLOCKS_PER_ACTOR);
        String history = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/com/nodecraft/nodesystem/nodes/world/write/WorldWriteHistoryService.java"));
        assertTrue(history.contains("MAX_UNDO_TOTAL_BLOCKS_PER_ACTOR"));
        assertTrue(history.contains("trimUndoStack(undoStack)"));
    }
}
