package com.nodecraft.nodesystem.nodes.world.read;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldReadUtilsTest {

    @Test
    void resolveMaxStringLengthUsesDefaultWhenZero() {
        assertEquals(WorldReadUtils.DEFAULT_MAX_NBT_STRING_LENGTH, WorldReadUtils.resolveMaxStringLength(0));
        assertEquals(WorldReadUtils.DEFAULT_MAX_NBT_STRING_LENGTH, WorldReadUtils.resolveMaxStringLength(null));
    }

    @Test
    void resolveMaxStringLengthCapsHugeValues() {
        assertEquals(WorldReadUtils.MAX_NBT_STRING_LENGTH, WorldReadUtils.resolveMaxStringLength(Integer.MAX_VALUE));
    }

    @Test
    void truncateUsesDefaultCapWhenLimitIsZero() {
        String value = "x".repeat(WorldReadUtils.DEFAULT_MAX_NBT_STRING_LENGTH + 10);
        String truncated = WorldReadUtils.truncate(value, 0);

        assertEquals(WorldReadUtils.DEFAULT_MAX_NBT_STRING_LENGTH + 3, truncated.length());
        assertTrue(truncated.endsWith("..."));
    }
}
