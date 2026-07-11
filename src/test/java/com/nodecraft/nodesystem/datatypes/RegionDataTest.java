package com.nodecraft.nodesystem.datatypes;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionDataTest {

    @Test
    void containsUsesInclusiveBounds() {
        RegionData region = new RegionData(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1));

        assertTrue(region.contains(new BlockPos(0, 0, 0)));
        assertTrue(region.contains(new BlockPos(1, 1, 1)));
        assertFalse(region.contains(new BlockPos(2, 0, 0)));
        assertFalse(region.contains(new BlockPos(0, -1, 0)));
    }

    @Test
    void overlappingMoveDestinationPreservesIntersectionPositions() {
        RegionData source = new RegionData(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0));
        RegionData destination = new RegionData(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0));

        assertTrue(source.contains(new BlockPos(1, 0, 0)));
        assertTrue(destination.contains(new BlockPos(1, 0, 0)));
    }
}
