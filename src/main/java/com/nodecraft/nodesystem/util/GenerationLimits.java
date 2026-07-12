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

    /**
     * Maximum iterations for flow-control loop nodes.
     */
    public static final int MAX_LOOP_ITERATIONS = 100_000;

    /**
     * Maximum segment/resolution count for most geometry-generation nodes.
     */
    public static final int MAX_SEGMENTS = 131_072;

    /**
     * Looser segment cap for shapes that need higher resolution to stay smooth.
     */
    public static final int MAX_HEART_SEGMENTS = 524_288;

    /**
     * Maximum per-axis samples for cubic bounds-estimation grids.
     */
    public static final int MAX_BOUNDS_SAMPLES = 128;

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

    /**
     * Caps loop iteration budgets to at least one and at most {@link #MAX_LOOP_ITERATIONS}.
     */
    public static int clampLoopIterations(int count) {
        return Math.max(1, Math.min(MAX_LOOP_ITERATIONS, count));
    }

    /**
     * Clamps curve/profile segment counts to {@code [min, MAX_SEGMENTS]}.
     */
    public static int clampSegments(int min, int requested) {
        return clampSegments(min, MAX_SEGMENTS, requested);
    }

    /**
     * Clamps segment counts to {@code [min, max]}.
     */
    public static int clampSegments(int min, int max, int requested) {
        return Math.max(min, Math.min(max, requested));
    }

    /**
     * Clamps heart-profile segment counts with a looser upper bound than {@link #clampSegments(int, int)}.
     */
    public static int clampHeartSegments(int requested) {
        return Math.max(24, Math.min(MAX_HEART_SEGMENTS, requested));
    }

    /**
     * Clamps per-unit resolution so {@code unitCount * resolution} does not exceed {@link #MAX_LIST_ELEMENTS}.
     */
    public static int clampResolutionPerUnit(int min, int requested, int unitCount) {
        int clamped = clampSegments(min, requested);
        int units = Math.max(1, unitCount);
        long maxPerUnit = MAX_LIST_ELEMENTS / (long) units;
        if (maxPerUnit < min) {
            return min;
        }
        return (int) Math.min(clamped, maxPerUnit);
    }

    /**
     * Clamps section counts so {@code profilePointCount * sectionCount} does not exceed {@link #MAX_LIST_ELEMENTS}.
     */
    public static int clampSectionCountForProfile(int min, int requested, int profilePointCount) {
        int clamped = clampSegments(min, requested);
        int profileSize = Math.max(1, profilePointCount);
        long maxSections = MAX_LIST_ELEMENTS / (long) profileSize;
        if (maxSections < min) {
            return min;
        }
        return (int) Math.min(clamped, maxSections);
    }

    /**
     * Clamps helix segments-per-turn so {@code turns * segmentsPerTurn} stays within {@link #MAX_LIST_ELEMENTS}.
     */
    public static int clampSegmentsPerTurn(int min, int requested, double turns) {
        int clamped = clampSegments(min, requested);
        if (!Double.isFinite(turns) || turns <= 0.0d) {
            return clamped;
        }
        long totalSegments = (long) Math.ceil(turns * clamped);
        if (totalSegments <= MAX_LIST_ELEMENTS) {
            return clamped;
        }
        long maxPerTurn = (long) Math.floor((double) MAX_LIST_ELEMENTS / Math.ceil(turns));
        return (int) Math.max(min, Math.min(clamped, maxPerTurn));
    }

    /**
     * Clamps per-axis bounds sampling for cubic grid estimators.
     */
    public static int clampBoundsSamples(int requested) {
        return Math.max(2, Math.min(MAX_BOUNDS_SAMPLES, requested));
    }
}
