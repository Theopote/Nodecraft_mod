package com.nodecraft.nodesystem.nodes.world.terrain;

import com.nodecraft.core.NodeCraft;
import com.nodecraft.nodesystem.datatypes.GridScalarFieldData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.ScalarFieldData;
import com.nodecraft.nodesystem.util.GenerationLimits;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Shared helpers for rasterizing scalar fields onto terrain simulation grids (cell-center lattice).
 */
final class ScalarFieldGrids {

    private ScalarFieldGrids() {
    }

    static TerrainGridDomain resolveDomain(@Nullable RegionData region, @Nullable ScalarFieldData field) {
        GridScalarFieldData grid = GridScalarFieldData.asGrid(field);
        if (grid != null) {
            return TerrainGridDomain.of(
                grid.getMinX(),
                grid.getMaxX(),
                grid.getMinZ(),
                grid.getMaxZ(),
                grid.getSampleY()
            );
        }
        return TerrainNodeUtils.localDomainFromRegion(region);
    }

    static TerrainGridDomain resolveDomain(@Nullable RegionData region) {
        return TerrainNodeUtils.localDomainFromRegion(region);
    }

    /** @deprecated use {@link #resolveDomain(RegionData, ScalarFieldData)} */
    @Deprecated
    static FieldGridBounds resolveBounds(@Nullable RegionData region, @Nullable ScalarFieldData field) {
        TerrainGridDomain domain = resolveDomain(region, field);
        return FieldGridBounds.from(domain);
    }

    /** @deprecated use {@link #resolveDomain(RegionData)} */
    @Deprecated
    static FieldGridBounds resolveBounds(@Nullable RegionData region) {
        return FieldGridBounds.from(resolveDomain(region));
    }

    /**
     * Materializes {@code field} onto {@code domain} at cell centers.
     * Returns {@code null} when size exceeds hard cap or any sample is non-finite.
     */
    static @Nullable GridScalarFieldData materialize(@Nullable ScalarFieldData field, TerrainGridDomain domain) {
        if (field == null || domain == null) {
            return null;
        }

        GridScalarFieldData existing = GridScalarFieldData.asGrid(field);
        if (existing != null
            && existing.getMinX() == domain.minBlockX()
            && existing.getMaxX() == domain.maxBlockX()
            && existing.getMinZ() == domain.minBlockZ()
            && existing.getMaxZ() == domain.maxBlockZ()
            && existing.getSampleY() == domain.sampleYBlock()) {
            if (!allFinite(existing)) {
                return null;
            }
            return existing;
        }

        if (!domain.canAllocateArray()) {
            NodeCraft.LOGGER.warn(
                "Scalar field materialization skipped: lattice size {} exceeds hard cap {}.",
                domain.cellCountLong(),
                GenerationLimits.MAX_TERRAIN_GRID_CELLS
            );
            return null;
        }

        long cellCount = domain.cellCountLong();
        double[] values = new double[(int) cellCount];
        Vector3d samplePoint = new Vector3d();
        int index = 0;
        for (int z = domain.minBlockZ(); z <= domain.maxBlockZ(); z++) {
            for (int x = domain.minBlockX(); x <= domain.maxBlockX(); x++) {
                domain.cellCenterSamplePoint(x, z, samplePoint);
                double sample = field.sampleScalar(samplePoint);
                if (!Double.isFinite(sample)) {
                    NodeCraft.LOGGER.warn(
                        "Scalar field materialization failed: non-finite sample at cell ({}, {}).",
                        x,
                        z
                    );
                    return null;
                }
                values[index++] = sample;
            }
        }

        return GridScalarFieldData.fromValues(
            domain.minBlockX(),
            domain.maxBlockX(),
            domain.minBlockZ(),
            domain.maxBlockZ(),
            domain.sampleYBlock(),
            values
        );
    }

    /** @deprecated prefer {@link #materialize(ScalarFieldData, TerrainGridDomain)} */
    @Deprecated
    static @Nullable GridScalarFieldData materialize(@Nullable ScalarFieldData field, FieldGridBounds bounds) {
        if (bounds == null) {
            return null;
        }
        return materialize(field, bounds.toDomain());
    }

    static GridScalarFieldData buildGrid(TerrainGridDomain domain, double[] values) {
        return GridScalarFieldData.fromValues(
            domain.minBlockX(),
            domain.maxBlockX(),
            domain.minBlockZ(),
            domain.maxBlockZ(),
            domain.sampleYBlock(),
            values
        );
    }

    static GridScalarFieldData buildGrid(FieldGridBounds bounds, double[] values) {
        return buildGrid(bounds.toDomain(), values);
    }

    static double sampleSlopeFromGrid(GridScalarFieldData heightGrid, int x, int z, double step) {
        double center = heightGrid.getAtClamped(x, z);
        double left = heightGrid.getAtClamped((int) Math.round(x - step), z);
        double right = heightGrid.getAtClamped((int) Math.round(x + step), z);
        double down = heightGrid.getAtClamped(x, (int) Math.round(z - step));
        double up = heightGrid.getAtClamped(x, (int) Math.round(z + step));

        double gradX = (right - left) / (2.0d * step);
        double gradZ = (up - down) / (2.0d * step);
        return Math.sqrt(gradX * gradX + gradZ * gradZ);
    }

    private static boolean allFinite(GridScalarFieldData grid) {
        for (int z = grid.getMinZ(); z <= grid.getMaxZ(); z++) {
            for (int x = grid.getMinX(); x <= grid.getMaxX(); x++) {
                if (!Double.isFinite(grid.getAt(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Legacy adapter kept for callers not yet migrated to {@link TerrainGridDomain}.
     */
    record FieldGridBounds(int minX, int maxX, int minZ, int maxZ, int sampleY) {

        static FieldGridBounds defaults() {
            return from(TerrainGridDomain.localDefault());
        }

        static FieldGridBounds from(TerrainGridDomain domain) {
            return new FieldGridBounds(
                domain.minBlockX(),
                domain.maxBlockX(),
                domain.minBlockZ(),
                domain.maxBlockZ(),
                domain.sampleYBlock()
            );
        }

        TerrainGridDomain toDomain() {
            return TerrainGridDomain.of(minX, maxX, minZ, maxZ, sampleY);
        }

        int width() {
            long width = (long) maxX - minX + 1L;
            if (width <= 0L || width > Integer.MAX_VALUE) {
                throw new IllegalStateException("Terrain grid width overflow: " + width);
            }
            return (int) width;
        }

        int depth() {
            long depth = (long) maxZ - minZ + 1L;
            if (depth <= 0L || depth > Integer.MAX_VALUE) {
                throw new IllegalStateException("Terrain grid depth overflow: " + depth);
            }
            return (int) depth;
        }

        long cellCount() {
            return toDomain().cellCountLong();
        }
    }
}
