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
     */
    public static Axis resolveAxis(Iterable<BlockPos> positions) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean any = false;

        for (BlockPos pos : positions) {
            any = true;
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        if (!any) {
            return Axis.X;
        }

        int spanX = maxX - minX;
        int spanZ = maxZ - minZ;
        return spanZ > spanX ? Axis.Z : Axis.X;
    }

    /**
     * Running-bond brick index for relative voxel coordinates.
     *
     * @throws IllegalArgumentException if {@code brickLength} or {@code courseHeight} is &lt; 1
     */
    public static int brickIndex(int dx, int dy, int dz, int brickLength, int courseHeight, Axis axis) {
        if (brickLength < 1 || courseHeight < 1) {
            throw new IllegalArgumentException("brickLength and courseHeight must be at least 1");
        }
        int course = Math.floorDiv(dy, courseHeight);
        int stagger = (course & 1) == 0 ? 0 : brickLength / 2;
        int along = axis == Axis.Z ? dz : dx;
        return Math.floorDiv(along + stagger, brickLength);
    }

    /**
     * @deprecated Prefer {@link #brickIndex(int, int, int, int, int, Axis)} with relative coords.
     */
    @Deprecated
    public static int brickIndex(BlockPos pos, int brickLength, int courseHeight, Axis axis) {
        return brickIndex(pos.getX(), pos.getY(), pos.getZ(), brickLength, courseHeight, axis);
    }

    public static boolean isPrimaryBrick(int brickIndex) {
        return (brickIndex & 1) == 0;
    }
}
