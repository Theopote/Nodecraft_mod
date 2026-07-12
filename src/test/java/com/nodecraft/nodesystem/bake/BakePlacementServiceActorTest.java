package com.nodecraft.nodesystem.bake;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
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
    void historiesAreIsolatedPerActor() {
        BakePlacementService service = BakePlacementService.getInstance();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        BakeHistory.UndoRecord record = new BakeHistory.UndoRecord(UUID.randomUUID());
        record.add(new BlockPos(0, 0, 0), Blocks.STONE.getDefaultState());
        service.getHistory(playerA).push(record);

        assertEquals(1, service.getHistory(playerA).size());
        assertEquals(0, service.getHistory(playerB).size());
    }

    @Test
    void resolveActorIdFallsBackToServerActorWhenPlayerMissing() {
        assertEquals(BakePlacementService.SERVER_ACTOR_ID, BakePlacementService.resolveActorId(null));
    }
}
