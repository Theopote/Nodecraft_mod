package com.nodecraft.gui.components.property.layout;

/**
 * Responsive layout rules for the property panel parameter list.
 */
public final class PropertyPanelMetrics {

    /** Below this width, property name and control stack vertically. */
    public static final float STACKED_LAYOUT_THRESHOLD_PX = 280f;
    public static final float MIN_LABEL_WIDTH_PX = 96f;
    public static final float MIN_VALUE_WIDTH_PX = 120f;
    public static final float LABEL_WIDTH_RATIO = 0.42f;

    private PropertyPanelMetrics() {
    }

    public static boolean useStackedLayout(float availableWidth) {
        return availableWidth > 0f && availableWidth < STACKED_LAYOUT_THRESHOLD_PX;
    }

    public static float labelColumnWidth(float availableWidth) {
        if (availableWidth <= 0f) {
            return MIN_LABEL_WIDTH_PX;
        }
        if (useStackedLayout(availableWidth)) {
            return availableWidth;
        }
        float preferred = availableWidth * LABEL_WIDTH_RATIO;
        float maxLabel = Math.max(MIN_LABEL_WIDTH_PX, availableWidth - MIN_VALUE_WIDTH_PX);
        return Math.min(Math.max(preferred, MIN_LABEL_WIDTH_PX), maxLabel);
    }
}
