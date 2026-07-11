package com.nodecraft.nodesystem.datatypes;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Materialized scalar field stored on an X/Z lattice at a fixed sample Y.
 * Sampling outside the grid falls back to the nearest edge value.
 */
public final class GridScalarFieldData implements ScalarFieldData {

    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int sampleY;
    private final double[] values;

    public GridScalarFieldData(int minX, int maxX, int minZ, int maxZ, int sampleY, double[] values) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.sampleY = sampleY;
        this.values = values;
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
        return maxX - minX + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    public int cellCount() {
        return values.length;
    }

    public double getAt(int x, int z) {
        return values[index(clampX(x), clampZ(z))];
    }

    public double getAtClamped(int x, int z) {
        return getAt(x, z);
    }

    @Override
    public double sampleScalar(Vector3d point) {
        return getAt((int) Math.round(point.x), (int) Math.round(point.z));
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
        int expected = Math.max(0, maxX - minX + 1) * Math.max(0, maxZ - minZ + 1);
        if (values.length != expected) {
            throw new IllegalArgumentException("Grid value count " + values.length + " does not match bounds " + expected);
        }
        return new GridScalarFieldData(minX, maxX, minZ, maxZ, sampleY, values);
    }

    public static @Nullable GridScalarFieldData asGrid(@Nullable ScalarFieldData field) {
        return field instanceof GridScalarFieldData grid ? grid : null;
    }

    private int index(int x, int z) {
        return (z - minZ) * width() + (x - minX);
    }

    private int clampX(int x) {
        if (x < minX) {
            return minX;
        }
        return Math.min(x, maxX);
    }

    private int clampZ(int z) {
        if (z < minZ) {
            return minZ;
        }
        return Math.min(z, maxZ);
    }
}
