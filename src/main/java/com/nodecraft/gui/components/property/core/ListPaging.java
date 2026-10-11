package com.nodecraft.gui.components.property.core;

/**
 * Page-aligned start-index clamping for property-panel list browsers.
 */
public final class ListPaging {

    private ListPaging() {
    }

    /**
     * Highest valid page-aligned start index for a list of {@code size} with {@code pageSize}.
     */
    public static int maxStart(int size, int pageSize) {
        if (size <= 0 || pageSize <= 0) {
            return 0;
        }
        return ((size - 1) / pageSize) * pageSize;
    }

    /**
     * Clamps {@code start} into {@code [0, maxStart]} and aligns down to a page boundary.
     */
    public static int clampStart(int size, int pageSize, int start) {
        if (size <= 0 || pageSize <= 0) {
            return 0;
        }
        int max = maxStart(size, pageSize);
        int clamped = Math.max(0, Math.min(start, max));
        return (clamped / pageSize) * pageSize;
    }
}
