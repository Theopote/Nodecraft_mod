package com.nodecraft.gui.components.property.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyPanelMetricsTest {

    @Test
    void stackedLayoutUsedOnNarrowPanels() {
        assertTrue(PropertyPanelMetrics.useStackedLayout(240f));
        assertFalse(PropertyPanelMetrics.useStackedLayout(320f));
    }

    @Test
    void labelWidthRespectsMinimumValueColumn() {
        assertEquals(200f, PropertyPanelMetrics.labelColumnWidth(200f), 0.01f);
        assertEquals(168f, PropertyPanelMetrics.labelColumnWidth(400f), 0.01f);
    }

    @Test
    void labelWidthNeverConsumesEntireRowOnWidePanels() {
        float labelWidth = PropertyPanelMetrics.labelColumnWidth(500f);
        assertTrue(labelWidth >= PropertyPanelMetrics.MIN_LABEL_WIDTH_PX);
        assertTrue(500f - labelWidth >= PropertyPanelMetrics.MIN_VALUE_WIDTH_PX);
    }
}
