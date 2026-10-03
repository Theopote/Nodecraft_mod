package com.nodecraft.nodesystem.nodes.world.read;

import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.VectorData;
import com.nodecraft.nodesystem.world.WorldQueryAccess;
import com.nodecraft.nodesystem.world.WorldScanStatus;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void scanStatusCompleteRequiresValidNoLimitsNoUnloaded() {
        WorldScanStatus status = new WorldScanStatus();
        assertTrue(status.valid());
        assertTrue(status.complete());
        assertEquals("completed", status.stoppedReason());

        status.markUnloaded();
        assertTrue(status.valid());
        assertFalse(status.complete());
        assertEquals("unloaded_chunk", status.stoppedReason());

        status.markHitLimit("max_blocks");
        assertEquals("max_blocks", status.stoppedReason());

        WorldScanStatus failed = new WorldScanStatus();
        failed.fail("World read failed");
        assertFalse(failed.valid());
        assertFalse(failed.complete());
        assertEquals("invalid", failed.stoppedReason());

        WorldScanStatus budget = new WorldScanStatus();
        budget.accept(WorldQueryAccess.Status.BUDGET);
        assertEquals("work_budget", budget.stoppedReason());
        assertFalse(budget.complete());
    }
}
