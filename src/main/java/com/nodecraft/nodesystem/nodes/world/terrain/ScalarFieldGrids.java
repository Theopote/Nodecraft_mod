package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.datatypes.GridScalarFieldData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Shared helpers for rasterizing scalar fields onto terrain simulation grids.
 */
final class ScalarFieldGrids {

    /** Hard cap for materialized X/Z lattice cells (matches 512x512). */
    static final long MAX_GRID_CELLS = 262_144L;

    private static final Vector3d SAMPLE_POINT = new Vector3d();

    private ScalarFieldGrids() {
    }

    static FieldGridBounds resolveBounds(@Nullable RegionData region, @Nullable ScalarFieldData field) {
        GridScalarFieldData grid = GridScalarFieldData.asGrid(field);
        if (grid != null) {
            return new FieldGridBounds(grid.getMinX(), grid.getMaxX(), grid.getMinZ(), grid.getMaxZ(), grid.getSampleY());
        }
        return resolveBounds(region);
    }

    static FieldGridBounds resolveBounds(@Nullable RegionData region) {
        if (region == null || !region.isComplete()) {
            return FieldGridBounds.defaults();
        }

        BlockPos min = region.getMinCorner();
        BlockPos max = region.getMaxCorner();
        if (min == null || max == null) {
            return FieldGridBounds.defaults();
        }

        return new FieldGridBounds(min.getX(), max.getX(), min.getZ(), max.getZ(), TerrainNodeUtils.DEFAULT_BASE_Y);
    }

    static @Nullable GridScalarFieldData materialize(@Nullable ScalarFieldData field, FieldGridBounds bounds) {
        if (field == null) {
            return null;
        }

        GridScalarFieldData existing = GridScalarFieldData.asGrid(field);
        if (existing != null
            && existing.getMinX() == bounds.minX()
            && existing.getMaxX() == bounds.maxX()
            && existing.getMinZ() == bounds.minZ()
            && existing.getMaxZ() == bounds.maxZ()
            && existing.getSampleY() == bounds.sampleY()) {
            return existing;
        }

        long cellCount = bounds.cellCount();
        if (cellCount <= 0L || cellCount > MAX_GRID_CELLS) {
            NodeCraft.LOGGER.warn(
                "Scalar field materialization skipped: lattice size {} exceeds limit {}.",
                cellCount,
                MAX_GRID_CELLS
            );
            return null;
        }

        int width = bounds.width();
        int depth = bounds.depth();
        double[] values = new double[(int) cellCount];
        int index = 0;
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                SAMPLE_POINT.set(x, bounds.sampleY(), z);
                values[index++] = TerrainNodeUtils.sanitizeFinite(field.sampleScalar(SAMPLE_POINT), 0.0d);
            }
        }

        return GridScalarFieldData.fromValues(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ(), bounds.sampleY(), values);
    }

    static GridScalarFieldData buildGrid(FieldGridBounds bounds, double[] values) {
        return GridScalarFieldData.fromValues(bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ(), bounds.sampleY(), values);
    }

    static double sampleSlopeFromGrid(GridScalarFieldData heightGrid, int x, int z, double step) {
        double center = heightGrid.getAt(x, z);
        double left = heightGrid.getAtClamped((int) Math.round(x - step), z);
        double right = heightGrid.getAtClamped((int) Math.round(x + step), z);
        double down = heightGrid.getAtClamped(x, (int) Math.round(z - step));
        double up = heightGrid.getAtClamped(x, (int) Math.round(z + step));

        double gradX = (right - left) / (2.0d * step);
        double gradZ = (up - down) / (2.0d * step);
        return Math.sqrt(gradX * gradX + gradZ * gradZ);
    }

    record FieldGridBounds(int minX, int maxX, int minZ, int maxZ, int sampleY) {

        static FieldGridBounds defaults() {
            return new FieldGridBounds(
                TerrainNodeUtils.DEFAULT_MIN_X,
                TerrainNodeUtils.DEFAULT_MAX_X,
                TerrainNodeUtils.DEFAULT_MIN_Z,
                TerrainNodeUtils.DEFAULT_MAX_Z,
                TerrainNodeUtils.DEFAULT_BASE_Y
            );
        }

        int width() {
            return maxX - minX + 1;
        }

        int depth() {
            return maxZ - minZ + 1;
        }

        long cellCount() {
            return (long) width() * depth();
        }
    }
}
