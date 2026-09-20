package com.nodecraft.gui.editor.interaction;

/**
 * Marquee / box-selection hit rules.
 * <p>
 * Left-to-right ({@code endX >= startX}): window select — node must be fully inside.
 * Right-to-left ({@code endX < startX}): crossing select — any overlap counts.
 */
public final class BoxSelectionRules {

    private BoxSelectionRules() {
    }

    /** Crossing (right-to-left) when the drag end is left of the drag start. */
    public static boolean isCrossingSelect(float startX, float endX) {
        return endX < startX;
    }

    public static boolean nodeHitsSelection(
            float selectionMinX,
            float selectionMaxX,
            float selectionMinY,
            float selectionMaxY,
            float nodeMinX,
            float nodeMaxX,
            float nodeMinY,
            float nodeMaxY,
            boolean crossingSelect
    ) {
        if (crossingSelect) {
            return nodeMaxX >= selectionMinX && nodeMinX <= selectionMaxX
                    && nodeMaxY >= selectionMinY && nodeMinY <= selectionMaxY;
        }
        return nodeMinX >= selectionMinX && nodeMaxX <= selectionMaxX
                && nodeMinY >= selectionMinY && nodeMaxY <= selectionMaxY;
    }
}
