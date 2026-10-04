package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.nodes.material.basic_assignment.BasicAssignmentUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void emptyPaletteStaysEmpty() {
        BlockPaletteData palette = BlockPaletteData.empty();
        assertTrue(palette.isEmpty());
        assertEquals(0, palette.size());
    }

    @Test
    void weightedEntriesPreserveWeights() {
        BlockPaletteData palette = BasicAssignmentUtils.buildPalette(
            List.of("minecraft:stone", "minecraft:dirt"),
            List.of(4.0d, 1.0d)
        );
        assertEquals(4.0d, palette.weights().getFirst(), 1.0e-12);
        assertTrue(palette.entries().get(1).weight() > 0.0d);
    }

    @Test
    void compactCtorRejectsIllegalEntries() {
        assertThrows(IllegalArgumentException.class, () -> new BlockPaletteEntry("", 1.0d));
        assertThrows(IllegalArgumentException.class, () -> new BlockPaletteEntry("minecraft:stone", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new BlockPaletteEntry("minecraft:stone", -1.0d));
        assertThrows(IllegalArgumentException.class, () -> new BlockPaletteData(java.util.Arrays.asList(
            new BlockPaletteEntry("minecraft:stone"),
            null
        )));
        assertNull(BlockPaletteData.canonical(null));
    }
}
