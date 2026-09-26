package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Terrain X/Z lattice domain: each index (x,z) stores the sample at cell center
 * {@code (x+0.5, sampleY+0.5, z+0.5)}.
 */
record TerrainGridDomain(int minBlockX, int maxBlockX, int minBlockZ, int maxBlockZ, int sampleYBlock) {

    static TerrainGridDomain localDefault() {
        return new TerrainGridDomain(
            TerrainNodeUtils.DEFAULT_MIN_X,
            TerrainNodeUtils.DEFAULT_MAX_X,
            TerrainNodeUtils.DEFAULT_MIN_Z,
            TerrainNodeUtils.DEFAULT_MAX_Z,
            TerrainNodeUtils.DEFAULT_BASE_Y
        );
    }

    static TerrainGridDomain of(int minX, int maxX, int minZ, int maxZ, int sampleY) {
        return new TerrainGridDomain(minX, maxX, minZ, maxZ, sampleY);
    }

    long widthLong() {
        return (long) maxBlockX - minBlockX + 1L;
    }

    long depthLong() {
        return (long) maxBlockZ - minBlockZ + 1L;
    }

    /**
     * @return cell count, or {@code -1} when overflow / non-positive
     */
    long cellCountLong() {
        long width = widthLong();
        long depth = depthLong();
        if (width <= 0L || depth <= 0L) {
            return -1L;
        }
        try {
            return Math.multiplyExact(width, depth);
        } catch (ArithmeticException e) {
            return -1L;
        }
    }

    boolean exceedsGridCap() {
        long cells = cellCountLong();
        return cells < 0L || cells > GenerationLimits.MAX_TERRAIN_GRID_CELLS;
    }

    boolean canAllocateArray() {
        long cells = cellCountLong();
        return cells > 0L
            && cells <= GenerationLimits.MAX_TERRAIN_GRID_CELLS
            && cells <= Integer.MAX_VALUE;
    }

    int widthExact() {
        long width = widthLong();
        if (width <= 0L || width > Integer.MAX_VALUE) {
            throw new IllegalStateException("Terrain grid width overflow: " + width);
        }
        return (int) width;
    }

    int depthExact() {
        long depth = depthLong();
        if (depth <= 0L || depth > Integer.MAX_VALUE) {
            throw new IllegalStateException("Terrain grid depth overflow: " + depth);
        }
        return (int) depth;
    }

    boolean containsColumn(int x, int z) {
        return x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ;
    }

    void cellCenterSamplePoint(int blockX, int blockZ, Vector3d dest) {
        dest.set(
            blockX + BlockSpace.CELL_CENTER_OFFSET,
            sampleYBlock + BlockSpace.CELL_CENTER_OFFSET,
            blockZ + BlockSpace.CELL_CENTER_OFFSET
        );
    }

    Vector3d cellCenterSamplePoint(int blockX, int blockZ) {
        Vector3d dest = new Vector3d();
        cellCenterSamplePoint(blockX, blockZ, dest);
        return dest;
    }

    /**
     * Safe next X/Z coordinate for inclusive loops with positive step.
     * Returns {@code null} when the next value would overflow int.
     */
    static @Nullable Integer safeNextAxis(int current, int step, int maxInclusive) {
        if (step <= 0) {
            return null;
        }
        long next = (long) current + (long) step;
        if (next > maxInclusive || next > Integer.MAX_VALUE) {
            return null;
        }
        return (int) next;
    }
}
