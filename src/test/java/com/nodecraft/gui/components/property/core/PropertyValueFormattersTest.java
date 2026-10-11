package com.nodecraft.gui.components.property.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyValueFormattersTest {

    @Test
    void displayDriftDetectsRoundedTextMismatch() {
        assertTrue(PropertyValueFormatters.decimalDisplayDrifted(1.23d, 1.24d));
        assertFalse(PropertyValueFormatters.decimalDisplayDrifted(1.23d, 1.229d));
    }

    @Test
    void commitUsesFullDoublePrecision() {
        assertTrue(PropertyValueFormatters.doubleValueChanged(1.001d, 1.002d));
        assertFalse(PropertyValueFormatters.doubleValueChanged(1.001d, 1.001d));
        assertTrue(PropertyValueFormatters.doubleValueChanged(1.234567d, 1.244567d));
    }

    @Test
    void smallButDistinctChangesAreNotDroppedByCommitHelper() {
        double oldValue = 1.001d;
        double newValue = 1.002d;
        assertFalse(PropertyValueFormatters.decimalDisplayDrifted(newValue, oldValue));
        assertTrue(PropertyValueFormatters.doubleValueChanged(oldValue, newValue));
    }

    @Test
    void formatDecimalIsDisplayOnly() {
        assertEquals("1.23", PropertyValueFormatters.formatDecimal(1.234567d));
        assertEquals("-2.50", PropertyValueFormatters.formatDecimal(-2.5d));
    }
}
