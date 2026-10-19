package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Registry-free coverage of the shared LWW collapse used by BakeTask and PlacementSubmitPlan.
 */
class LastWriteWinsByPosTest {

    private record Cell(BlockPos pos, String block) {
    }

    @Test
    void collapseLastWriteWinsPreservesFirstSeenOrder() {
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
        assertEquals(p, unique.get(0).pos());
        assertEquals("gold", unique.get(0).block());
        assertEquals(q, unique.get(1).pos());
        assertEquals("stone", unique.get(1).block());
    }

    @Test
    void collapseSkipsNullEntriesAndEmptyInput() {
        assertEquals(List.of(), LastWriteWinsByPos.collapse(null, Cell::pos, c -> true, (k, c) -> c));
        assertEquals(List.of(), LastWriteWinsByPos.collapse(List.of(), Cell::pos, c -> true, (k, c) -> c));

        BlockPos p = new BlockPos(0, 0, 0);
        Cell kept = new Cell(p, "stone");
        List<Cell> unique = LastWriteWinsByPos.collapse(
                java.util.Arrays.asList(
                        null,
                        new Cell(p, null),
                        kept
                ),
                Cell::pos,
                c -> c != null && c.block() != null,
                (key, c) -> new Cell(key, c.block())
        );
        assertEquals(1, unique.size());
        assertEquals("stone", unique.getFirst().block());
        assertEquals(p, unique.getFirst().pos());
    }

    @Test
    void collapseRemapsToImmutableKey() {
        BlockPos mutable = new BlockPos(2, 3, 4);
        Cell in = new Cell(mutable, "oak");
        List<Cell> unique = LastWriteWinsByPos.collapse(
                List.of(in),
                Cell::pos,
                c -> true,
                (key, c) -> new Cell(key, c.block())
        );
        assertEquals(1, unique.size());
        assertSame(unique.getFirst().pos(), unique.getFirst().pos().toImmutable());
        assertEquals(new BlockPos(2, 3, 4), unique.getFirst().pos());
    }
}
