package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.DataTreeData;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementPreflightTest {

    @Test
    void rejectsNonPlacementEntryInList() {
        PlacementPreflight.Result result = PlacementPreflight.preflightSources(
                List.of("not-a-placement"), null);
        assertFalse(result.valid());
        assertEquals(PlacementPreflight.ERROR_INVALID_ENTRY, result.error());
    }

    @Test
    void rejectsMissingBlockId() {
        BlockPlacementData missingId = new BlockPlacementData(new BlockPos(0, 0, 0), "  ");
        PlacementPreflight.Result result = PlacementPreflight.preflightSources(List.of(missingId), null);
        assertFalse(result.valid());
        assertEquals(PlacementPreflight.ERROR_INVALID_ENTRY, result.error());
    }

    @Test
    void acceptsParsedPlacementsBeforeBlockStateResolution() {
        BlockPlacementData good = new BlockPlacementData(new BlockPos(1, 2, 3), "minecraft:stone");
        var parsed = PlacementPreflight.parsePlacementList(List.of(good));
        assertTrue(parsed.valid());
        assertEquals(1, parsed.value().size());
        assertEquals(new BlockPos(1, 2, 3), parsed.value().getFirst().pos());
    }

    @Test
    void rejectsInvalidTreeEntry() {
        DataTreeData tree = new DataTreeData(List.of(
                new DataTreeData.Branch(List.of(0), List.of(42))
        ));
        PlacementPreflight.Result result = PlacementPreflight.preflightSources(null, tree);
        assertFalse(result.valid());
        assertEquals(PlacementPreflight.ERROR_INVALID_ENTRY, result.error());
    }
}
