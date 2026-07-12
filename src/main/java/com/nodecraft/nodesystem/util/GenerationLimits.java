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
