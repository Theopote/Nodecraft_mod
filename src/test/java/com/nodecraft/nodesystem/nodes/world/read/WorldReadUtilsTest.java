package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldReadUtilsTest {

    @Test
    void requireBlockPosRejectsVectorFloorCoercion() {
        assertNull(WorldReadUtils.requireBlockPos(new VectorData(1.2, 3.4, 5.6)));
        assertNull(WorldReadUtils.requireBlockPos(null));
        assertEquals(new BlockPos(1, 2, 3), WorldReadUtils.requireBlockPos(new BlockPos(1, 2, 3)));
    }

    @Test
    void volumeUsesLongArithmeticAndOverflowSentinel() {
        RegionData small = new RegionData(new BlockPos(0, 0, 0), new BlockPos(1, 1, 1));
        assertEquals(8L, WorldReadUtils.volume(small));

        RegionData overflow = new RegionData(
                new BlockPos(0, 0, 0),
                new BlockPos(Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2)
        );
        assertEquals(WorldReadUtils.OVERFLOW, WorldReadUtils.volume(overflow));
    }

    @Test
    void truncateUsesDefaultCapWhenLimitIsZero() {
        String value = "x".repeat(WorldReadUtils.DEFAULT_MAX_NBT_STRING_LENGTH + 10);
        String truncated = WorldReadUtils.truncate(value, 0);

        assertEquals(WorldReadUtils.DEFAULT_MAX_NBT_STRING_LENGTH + 3, truncated.length());
        assertTrue(truncated.endsWith("..."));
    }

    @Test
    void nextAxisCoordinateStopsPastMax() {
        assertEquals(5, WorldReadUtils.nextAxisCoordinate(2, 3, 10));
        assertNull(WorldReadUtils.nextAxisCoordinate(8, 3, 10));
        assertNull(WorldReadUtils.nextAxisCoordinate(Integer.MAX_VALUE - 1, 2, Integer.MAX_VALUE));
    }
}
