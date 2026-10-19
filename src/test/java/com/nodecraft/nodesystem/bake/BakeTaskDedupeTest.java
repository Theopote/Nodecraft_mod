package com.nodecraft.nodesystem.bake;

import com.nodecraft.nodesystem.util.LastWriteWinsByPos;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BakeTask delegates LWW to {@link LastWriteWinsByPos}; full BlockState wiring is covered by
 * GameTest ({@code bakeDuplicatePosIncrementalUsesLastWriteWins}).
 */
class BakeTaskDedupeTest {

    private record Cell(BlockPos pos, String block) {
    }

    @Test
    void bakeDedupeUsesSameCollapseAsSharedHelper() {
        BlockPos p = new BlockPos(0, 64, 0);
        BlockPos q = new BlockPos(1, 64, 0);
        List<Cell> unique = LastWriteWinsByPos.collapse(
                List.of(
                        new Cell(p, "stone"),
                        new Cell(p, "gold"),
                        new Cell(q, "stone")
                ),
                Cell::pos,
                c -> c != null && c.block() != null,
                (key, c) -> new Cell(key, c.block())
        );
        assertEquals(2, unique.size());
        assertEquals("gold", unique.get(0).block());
        assertEquals(p, unique.get(0).pos());
        assertEquals(q, unique.get(1).pos());
    }

    @Test
    void bakeDedupeEmptyAndNullMatchSharedHelper() {
        assertEquals(List.of(), BakeTask.dedupeLastWriteWins(null));
        assertEquals(List.of(), BakeTask.dedupeLastWriteWins(List.of()));
    }
}
