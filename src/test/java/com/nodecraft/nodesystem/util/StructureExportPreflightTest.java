package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureExportPreflightTest {

    @Test
    void rejectsMalformedPlacementMember() {
        List<Object> mixed = new ArrayList<>();
        mixed.add(new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone"));
        mixed.add("not-a-placement");

        StructureExportPreflight.Result result = StructureExportPreflight.prepare(
            mixed,
            null,
            "minecraft:stone",
            StructureExportPreflight.DenseMode.NONE,
            "test",
            "author",
            ""
        );
        assertFalse(result.valid());
        assertEquals(PlacementPreflight.ERROR_INVALID_ENTRY, result.error());
    }

    @Test
    void rejectsHugeDenseVolumeBeforeAllocation() {
        List<BlockPlacementData> placements = List.of(
            new BlockPlacementData(new BlockPos(0, 64, 0), "minecraft:stone"),
            new BlockPlacementData(new BlockPos(100_000, 64, 100_000), "minecraft:stone")
        );

        StructureExportPreflight.Result result = StructureExportPreflight.prepare(
            placements,
            null,
            "minecraft:stone",
            StructureExportPreflight.DenseMode.LITEMATIC,
            "huge",
            "nodecraft",
            ""
        );
        assertFalse(result.valid());
        assertEquals("volume_exceeds_MAX_DENSE_EXPORT_VOLUME", result.error());
    }

    @Test
    void rejectsWorldEditAxisOverflow() {
        List<BlockPlacementData> placements = List.of(
            new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone"),
            new BlockPlacementData(new BlockPos(40_000, 0, 0), "minecraft:stone")
        );

        StructureExportPreflight.Result result = StructureExportPreflight.prepare(
            placements,
            null,
            "minecraft:stone",
            StructureExportPreflight.DenseMode.WORLD_EDIT,
            "wide",
            "nodecraft",
            ""
        );
        assertFalse(result.valid());
        assertEquals("axis_exceeds_MAX_WORLD_EDIT_AXIS", result.error());
    }

    @Test
    void denseIndicesLeaveHolesAsAir() {
        BlockPlacementData a = new BlockPlacementData(new BlockPos(0, 0, 0), "minecraft:stone");
        BlockPlacementData b = new BlockPlacementData(new BlockPos(2, 0, 0), "minecraft:stone");
        List<BlockPlacementData> placements = List.of(a, b);

        ExportBounds bounds = ExportBounds.fromPlacements(placements);
        StructureExportPreflight.StructurePalette palette = paletteWithAir(placements);
        StructureExportPreflight.PreparedStructureExport prepared =
            new StructureExportPreflight.PreparedStructureExport(
                placements,
                bounds,
                palette,
                new StructureExportPreflight.Metadata("hole", "nodecraft", "")
            );

        assertEquals("minecraft:air", palette.entries().getFirst().blockId());
        assertEquals(0, palette.indexByKey().get(StructureExportPreflight.keyFor(
            new BlockPlacementData(BlockPos.ORIGIN, "minecraft:air"))));

        int stoneIndex = palette.indexFor(a);
        assertTrue(stoneIndex > 0);

        int[] litematic = StructureExportPreflight.buildDenseIndices(
            prepared, StructureExportPreflight.IndexOrder.LITEMATIC);
        assertEquals(3, litematic.length);
        assertEquals(stoneIndex, litematic[0]);
        assertEquals(0, litematic[1]); // air hole
        assertEquals(stoneIndex, litematic[2]);

        int[] worldEdit = StructureExportPreflight.buildDenseIndices(
            prepared, StructureExportPreflight.IndexOrder.WORLD_EDIT);
        assertEquals(3, worldEdit.length);
        assertEquals(stoneIndex, worldEdit[0]);
        assertEquals(0, worldEdit[1]);
        assertEquals(stoneIndex, worldEdit[2]);
    }

    private static StructureExportPreflight.StructurePalette paletteWithAir(List<BlockPlacementData> placements) {
        // Mirror production air@0 construction via prepare's palette path through a tiny helper:
        // build by calling prepare when possible; otherwise construct manually.
        var indexByKey = new java.util.LinkedHashMap<String, Integer>();
        var entries = new java.util.ArrayList<StructureExportPreflight.PaletteEntry>();
        indexByKey.put(StructureExportPreflight.keyFor(new BlockPlacementData(BlockPos.ORIGIN, "minecraft:air")), 0);
        entries.add(new StructureExportPreflight.PaletteEntry("minecraft:air", null));
        for (BlockPlacementData placement : placements) {
            String key = StructureExportPreflight.keyFor(placement);
            if (indexByKey.containsKey(key)) {
                continue;
            }
            int next = indexByKey.size();
            indexByKey.put(key, next);
            entries.add(new StructureExportPreflight.PaletteEntry(placement.blockId(), placement.stateData()));
        }
        return new StructureExportPreflight.StructurePalette(List.copyOf(entries), java.util.Map.copyOf(indexByKey));
    }
}
