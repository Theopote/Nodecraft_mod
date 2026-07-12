package com.nodecraft.nodesystem.nodes.world.write;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class WorldWriteHistoryServiceActorTest {

    @Test
    void resolveActorIdFallsBackToServerActorWhenPlayerMissing() {
        assertEquals(WorldWriteHistoryService.SERVER_ACTOR_ID, WorldWriteHistoryService.resolveActorId(null));
    }

    @Test
    void serverActorUsesSharedHistoryBucket() {
        WorldWriteHistoryService service = WorldWriteHistoryService.getInstance();
        assertEquals(0, service.size(null));
        assertEquals(0, service.size(WorldWriteHistoryService.SERVER_ACTOR_ID));
    }

    @Test
    void distinctActorsHaveIndependentHistorySizes() {
        WorldWriteHistoryService service = WorldWriteHistoryService.getInstance();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        assertEquals(0, service.size(playerA));
        assertEquals(0, service.size(playerB));
        assertNotSame(playerA, playerB);
    }
}
