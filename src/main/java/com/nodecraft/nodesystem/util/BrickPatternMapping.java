package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;

/**
 * Shared brick running-bond indexing for {@code BrickPatternMapNode}.
 * Callers must validate {@code brickLength >= 1} and {@code courseHeight >= 1}.
 */
public final class BrickPatternMapping {

    public enum Axis {
        X,
        Z
    }

    private BrickPatternMapping() {
    }

    /**
     * Picks the horizontal axis with greater voxel span so north-south walls use Z
     * and east-west walls use X. Ties fall back to X.
     * <p>
     * Spans are computed in {@code long} so extreme world extents do not overflow.
     */
    public static Axis resolveAxis(Iterable<BlockPos> positions) {
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        long minZ = Long.MAX_VALUE;
        long maxZ = Long.MIN_VALUE;
        boolean any = false;

        for (BlockPos pos : positions) {
            any = true;
            long x = pos.getX();
            long z = pos.getZ();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        if (!any) {
            return Axis.X;
        }

        return resolveAxisFromSpans(minX, maxX, minZ, maxZ);
    }

    /**
     * Long-safe Auto axis from relative X/Z extents (no temporary BlockPos packing).
     */
    public static Axis resolveAxisFromSpans(long minX, long maxX, long minZ, long maxZ) {
        long spanX = maxX - minX;
        long spanZ = maxZ - minZ;
        return spanZ > spanX ? Axis.Z : Axis.X;
    }

    /**
     * Running-bond brick index for relative voxel coordinates (long-safe).
     *
     * @throws IllegalArgumentException if {@code brickLength} or {@code courseHeight} is &lt; 1
     */
    public static int brickIndex(long dx, long dy, long dz, int brickLength, int courseHeight, Axis axis) {
        if (brickLength < 1 || courseHeight < 1) {
            throw new IllegalArgumentException("brickLength and courseHeight must be at least 1");
        }
        long course = Math.floorDiv(dy, courseHeight);
        long stagger = (course & 1L) == 0L ? 0L : brickLength / 2L;
        long along = axis == Axis.Z ? dz : dx;
        return (int) Math.floorDiv(along + stagger, brickLength);
    }

    /**
     * @deprecated Prefer {@link #brickIndex(long, long, long, int, int, Axis)} with relative coords.
     */
    @Deprecated
    public static int brickIndex(BlockPos pos, int brickLength, int courseHeight, Axis axis) {
        return brickIndex(pos.getX(), pos.getY(), pos.getZ(), brickLength, courseHeight, axis);
    }

    public static boolean isPrimaryBrick(int brickIndex) {
        return (brickIndex & 1) == 0;
    }
}
