package com.nodecraft.nodesystem.datatypes;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a rectangular region defined by two corner BlockPos.
 */
public record RegionData(@Nullable BlockPos corner1, @Nullable BlockPos corner2) {

    /**
     * Checks if both corners are set.
     */
    public boolean isComplete() {
        return corner1 != null && corner2 != null;
    }

    /**
     * Gets the minimum corner of the bounding box containing the two points.
     * Returns null if the region is incomplete.
     */
    @Nullable
    public BlockPos getMinCorner() {
        if (!isComplete()) return null;
        return new BlockPos(Math.min(corner1.getX(), corner2.getX()),
                          Math.min(corner1.getY(), corner2.getY()),
                          Math.min(corner1.getZ(), corner2.getZ()));
    }

    /**
     * Gets the maximum corner of the bounding box containing the two points.
     * Returns null if the region is incomplete.
     */
    @Nullable
    public BlockPos getMaxCorner() {
        if (!isComplete()) return null;
        return new BlockPos(Math.max(corner1.getX(), corner2.getX()),
                          Math.max(corner1.getY(), corner2.getY()),
                          Math.max(corner1.getZ(), corner2.getZ()));
    }

    /**
     * Creates a Minecraft Box representing the region.
     * Returns null if the region is incomplete.
     */
    @Nullable
    public Box toBox() {
        if (!isComplete()) return null;
        // Box constructor includes the max corner, so add 1
        BlockPos min = getMinCorner();
        BlockPos max = getMaxCorner();
        return new Box(min.getX(), min.getY(), min.getZ(), 
                       max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
    }
    
    /**
     * Creates an Iterable for all BlockPos within the region (inclusive).
     * Returns an empty iterable if the region is incomplete.
     */
    public Iterable<BlockPos> getAllBlocks() {
        if (!isComplete()) {
            return java.util.Collections::emptyIterator;
        }
        return BlockPos.iterate(getMinCorner(), getMaxCorner());
    }

    /**
     * Returns whether {@code pos} lies inside this region's inclusive bounds.
     */
    public boolean contains(BlockPos pos) {
        if (!isComplete() || pos == null) {
            return false;
        }
        BlockPos min = getMinCorner();
        BlockPos max = getMaxCorner();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }
} 