package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.BlockSpace;
import com.nodecraft.nodesystem.util.GenerationLimits;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Materialized scalar field on an X/Z lattice at a fixed sample Y.
 * <p>
 * Index {@code (x,z)} stores the sample at cell center
 * {@code (x+0.5, sampleY+0.5, z+0.5)}. Continuous sampling maps via
 * {@link BlockSpace#nearestCellIndex}. Outside the grid domain returns {@link Double#NaN}
 * (no silent CLAMP_TO_EDGE).
 */
public final class GridScalarFieldData implements ScalarFieldData {

    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int sampleY;
    private final double[] values;
    private final int width;
    private final int depth;

    public GridScalarFieldData(int minX, int maxX, int minZ, int maxZ, int sampleY, double[] values) {
        long widthLong = (long) maxX - minX + 1L;
        long depthLong = (long) maxZ - minZ + 1L;
        if (widthLong <= 0L || depthLong <= 0L) {
            throw new IllegalArgumentException("Grid bounds produce non-positive size");
        }
        long cellCount;
        try {
            cellCount = Math.multiplyExact(widthLong, depthLong);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Grid cell count overflows", e);
        }
        if (cellCount > GenerationLimits.MAX_TERRAIN_GRID_CELLS || cellCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                "Grid cell count " + cellCount + " exceeds hard cap " + GenerationLimits.MAX_TERRAIN_GRID_CELLS
            );
        }
        if (values == null || values.length != (int) cellCount) {
            throw new IllegalArgumentException(
                "Grid value count " + (values == null ? 0 : values.length) + " does not match bounds " + cellCount
            );
        }
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.sampleY = sampleY;
        this.values = values;
        this.width = (int) widthLong;
        this.depth = (int) depthLong;
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public int getSampleY() {
        return sampleY;
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public int cellCount() {
        return values.length;
    }

    public boolean containsColumn(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * Value at column index. Outside domain → {@link Double#NaN}.
     */
    public double getAt(int x, int z) {
        if (!containsColumn(x, z)) {
            return Double.NaN;
        }
        return values[index(x, z)];
    }

    /**
     * Neighbor probe that clamps to the edge (for finite-difference kernels inside the grid).
     * Prefer {@link #getAt} for domain-faithful sampling.
     */
    public double getAtClamped(int x, int z) {
        int cx = Math.max(minX, Math.min(x, maxX));
        int cz = Math.max(minZ, Math.min(z, maxZ));
        return values[index(cx, cz)];
    }

    @Override
    public double sampleScalar(Vector3d point) {
        if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.z)) {
            return Double.NaN;
        }
        int x = BlockSpace.nearestCellIndex(point.x);
        int z = BlockSpace.nearestCellIndex(point.z);
        return getAt(x, z);
    }

    public static GridScalarFieldData copyOf(GridScalarFieldData source) {
        double[] copy = new double[source.values.length];
        System.arraycopy(source.values, 0, copy, 0, source.values.length);
        return new GridScalarFieldData(
            source.minX,
            source.maxX,
            source.minZ,
            source.maxZ,
            source.sampleY,
            copy
        );
    }

    public static GridScalarFieldData fromValues(int minX,
                                                 int maxX,
                                                 int minZ,
                                                 int maxZ,
                                                 int sampleY,
                                                 double[] values) {
        return new GridScalarFieldData(minX, maxX, minZ, maxZ, sampleY, values);
    }

    public static @Nullable GridScalarFieldData asGrid(@Nullable ScalarFieldData field) {
        return field instanceof GridScalarFieldData grid ? grid : null;
    }

    private int index(int x, int z) {
        return (z - minZ) * width + (x - minX);
    }
}
