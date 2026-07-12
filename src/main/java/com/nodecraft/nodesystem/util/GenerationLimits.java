package com.nodecraft.nodesystem.util;

/**
 * Shared hard limits for nodes that materialize collections from user-supplied counts.
 */
public final class GenerationLimits {

    /**
     * Maximum number of elements a single node may allocate into one output list.
     */
    public static final int MAX_LIST_ELEMENTS = 1_048_576;

    /**
     * Maximum repetitions per axis for 2D grid/array nodes before multiplying by source size.
     */
    public static final int MAX_GRID_AXIS = 1024;

    private GenerationLimits() {
    }

    public static int clampNonNegativeCount(int count) {
        if (count <= 0) {
            return 0;
        }
        return Math.min(count, MAX_LIST_ELEMENTS);
    }

    public static int clampPositiveCount(int count) {
        return Math.max(1, Math.min(MAX_LIST_ELEMENTS, count));
    }

    public static int clampGridAxis(int count) {
        return clampNonNegativeCount(Math.min(count, MAX_GRID_AXIS));
    }

    /**
     * Caps grid repetition counts for nodes that iterate {@code 0..count} along each axis.
     * Scales axes down when {@code (x+1)(y+1)(z+1) * itemsPerCell} would exceed {@link #MAX_LIST_ELEMENTS}.
     */
    public static GridAxisCounts clampGridCounts(int xCount, int yCount, int zCount, int itemsPerCell) {
        int x = clampGridAxis(xCount);
        int y = clampGridAxis(yCount);
        int z = clampGridAxis(zCount);
        int perCell = Math.max(1, itemsPerCell);

        while (estimatedInclusiveGridOutputSize(x, y, z, perCell) > MAX_LIST_ELEMENTS) {
            if (z > 0 && z >= x && z >= y) {
                z--;
            } else if (y > 0 && y >= x) {
                y--;
            } else if (x > 0) {
                x--;
            } else {
                break;
            }
        }
        return new GridAxisCounts(x, y, z);
    }

    /**
     * Caps grid counts for nodes that iterate {@code 0..count-1} along each axis.
     */
    public static GridAxisCounts clampExclusiveGridCounts(int xCount, int yCount, int zCount, int itemsPerCell) {
        int x = clampPositiveGridAxis(xCount);
        int y = clampPositiveGridAxis(yCount);
        int z = clampPositiveGridAxis(zCount);
        int perCell = Math.max(1, itemsPerCell);

        while ((long) x * y * z * perCell > MAX_LIST_ELEMENTS) {
            if (z > 1 && z >= x && z >= y) {
                z--;
            } else if (y > 1 && y >= x) {
                y--;
            } else if (x > 1) {
                x--;
            } else {
                break;
            }
        }
        return new GridAxisCounts(x, y, z);
    }

    private static int clampPositiveGridAxis(int count) {
        return Math.max(1, Math.min(MAX_GRID_AXIS, count));
    }

    private static long estimatedInclusiveGridOutputSize(int xCount, int yCount, int zCount, int itemsPerCell) {
        return (long) (xCount + 1) * (yCount + 1) * (zCount + 1) * itemsPerCell;
    }

    public record GridAxisCounts(int xCount, int yCount, int zCount) {
    }

    /**
     * Caps the number of instances produced by fixed-spacing sampling along a span.
     */
    public static int clampSpacingInstanceCount(double span, double spacing) {
        if (!Double.isFinite(span) || !Double.isFinite(spacing) || spacing <= 0.0d) {
            return 1;
        }
        long raw = (long) Math.ceil(span / spacing) + 1L;
        return (int) Math.min(Math.max(1L, raw), MAX_LIST_ELEMENTS);
    }

    /**
     * Caps attempt budgets for rejection-style samplers.
     */
    public static int clampAttemptBudget(int attempts, int targetCount) {
        int perTarget = Math.max(1, targetCount);
        long scaled = (long) perTarget * 100L;
        long capped = Math.min(Math.max(100L, attempts), Math.max(scaled, 100L));
        capped = Math.min(capped, (long) MAX_LIST_ELEMENTS * 100L);
        return (int) capped;
    }

    /**
     * Caps repeat iterations so {@code count * itemsPerRepeat} does not exceed {@link #MAX_LIST_ELEMENTS}.
     */
    public static int clampRepeatCount(int count, int itemsPerRepeat) {
        if (count <= 0) {
            return 0;
        }
        int perRepeat = Math.max(1, itemsPerRepeat);
        long maxCount = MAX_LIST_ELEMENTS / (long) perRepeat;
        if (maxCount <= 0L) {
            return 0;
        }
        return (int) Math.min(count, maxCount);
    }
}
