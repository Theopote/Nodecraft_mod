package com.nodecraft.nodesystem.util;

import net.minecraft.block.BlockState;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStateResolverRoundTripTest {

    @Test
    void toStateDataRoundTripsOrientedStairs() {
        Assumptions.assumeTrue(BlockStateResolver.isRegistryReady(), "Live BLOCK registry required");

        BlockStateData overrides = new BlockStateData()
                .withProperty("facing", "east")
                .withProperty("half", "top")
                .withProperty("shape", "straight");
        BlockState resolved = BlockStateResolver.resolve("minecraft:oak_stairs", overrides);
        assertNotNull(resolved);

        BlockStateData extracted = BlockStateResolver.toStateData(resolved);
        assertEquals("east", extracted.get("facing"));
        assertEquals("top", extracted.get("half"));
        assertEquals("straight", extracted.get("shape"));

        BlockState again = BlockStateResolver.resolve("minecraft:oak_stairs", extracted);
        assertEquals(resolved, again);
    }

    @Test
    void toStateDataEmptyForNull() {
        assertTrue(BlockStateResolver.toStateData(null).isEmpty());
    }
}
