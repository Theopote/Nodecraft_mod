package com.nodecraft.nodesystem.nodes.world.write;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

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
}
