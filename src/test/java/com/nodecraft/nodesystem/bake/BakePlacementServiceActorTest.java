package com.nodecraft.nodesystem.bake;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class BakePlacementServiceActorTest {

    @Test
    void getHistoryReturnsDistinctInstancesPerActor() {
        BakePlacementService service = BakePlacementService.getInstance();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        BakeHistory historyA = service.getHistory(playerA);
        BakeHistory historyB = service.getHistory(playerB);

        assertNotSame(historyA, historyB);
        assertSame(historyA, service.getHistory(playerA));
    }

    @Test
    void resolveActorIdFallsBackToServerActorWhenPlayerMissing() {
        assertEquals(BakePlacementService.SERVER_ACTOR_ID, BakePlacementService.resolveActorId(null));
    }

    @Test
    void serverActorUsesSharedHistoryBucket() {
        BakePlacementService service = BakePlacementService.getInstance();
        assertSame(service.getHistory(null), service.getHistory(BakePlacementService.SERVER_ACTOR_ID));
    }
}
