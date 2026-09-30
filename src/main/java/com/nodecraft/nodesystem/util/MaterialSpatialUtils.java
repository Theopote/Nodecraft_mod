package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;

/**
 * Long-safe block-relative coordinates for material spatial sampling.
 */
public final class MaterialSpatialUtils {

    public record Relative(long dx, long dy, long dz) {
    }

    private static final BlockPos WORLD_ORIGIN = new BlockPos(0, 0, 0);

    private MaterialSpatialUtils() {
    }

    public static Relative relative(BlockPos pos, BlockPos origin) {
        BlockPos o = origin != null ? origin : WORLD_ORIGIN;
        return new Relative(
            (long) pos.getX() - o.getX(),
            (long) pos.getY() - o.getY(),
            (long) pos.getZ() - o.getZ()
        );
    }
}
