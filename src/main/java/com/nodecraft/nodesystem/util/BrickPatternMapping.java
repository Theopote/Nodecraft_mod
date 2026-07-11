package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.BlockPos;

/**
 * Shared brick running-bond indexing for {@code BrickPatternMapNode}.
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

    public static int brickIndex(BlockPos pos, int brickLength, int courseHeight, Axis axis) {
        int length = Math.max(1, brickLength);
        int height = Math.max(1, courseHeight);
        int course = Math.floorDiv(pos.getY(), height);
        int stagger = (course & 1) == 0 ? 0 : length / 2;
        int along = axis == Axis.Z ? pos.getZ() : pos.getX();
        return Math.floorDiv(along + stagger, length);
    }

    public static boolean isPrimaryBrick(int brickIndex) {
        return (brickIndex & 1) == 0;
    }
}
