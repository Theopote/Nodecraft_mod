package com.nodecraft.nodesystem.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockPaletteDataTest {

    @Test
    void fromLegacyObjectAcceptsLegacyStringList() {
        BlockPaletteData palette = BlockPaletteData.fromLegacyObject(List.of("minecraft:stone", "minecraft:andesite"));
        assertEquals(2, palette.size());
        assertEquals("minecraft:andesite", palette.blockIdAt(1, "minecraft:dirt"));
    }

    @Test
    void requireTypedRejectsRawLists() {
        assertTrue(BlockPaletteData.requireTyped(List.of("minecraft:stone")).isEmpty());
        assertEquals(1, BlockPaletteData.requireTyped(
            BlockPaletteData.ofBlockIds(List.of("minecraft:stone"))
        ).size());
    }

    @Test
    void withFallbackFillsEmptyPalette() {
        BlockPaletteData palette = BlockPaletteData.empty().withFallback("minecraft:cobblestone");
        assertEquals(1, palette.size());
        assertEquals("minecraft:cobblestone", palette.blockIds().getFirst());
    }

    @Test
    void weightedEntriesPreserveWeights() {
        BlockPaletteData palette = BlockPaletteData.ofBlockIdsAndWeights(
            List.of("minecraft:stone", "minecraft:dirt"),
            List.of(4.0d, 1.0d)
        );
        assertEquals(4.0d, palette.weights().getFirst(), 1.0e-12);
        assertTrue(palette.entries().get(1).weight() > 0.0d);
    }
}
