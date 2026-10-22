package com.nodecraft.gui.components.port;

import com.nodecraft.nodesystem.datatypes.PointData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyValueFormatterTest {

    @Test
    void previewRoundsDoublesAndPoints() {
        assertEquals("605.75", PropertyValueFormatter.formatValuePreview(605.7496414983359d));
        assertEquals(
                "Point[605.75, 114.52, 201.13]",
                PropertyValueFormatter.formatValuePreview(
                        new PointData(605.7496414983359d, 114.52240436581235d, 201.12679254255286d)));
    }

    @Test
    void detailsKeepRoundedDisplay() {
        String details = PropertyValueFormatter.formatValueDetails(Math.PI, "Output Data");
        assertTrue(details.contains(Double.toString(Math.PI)));
        assertTrue(details.contains("Type: Double"));
    }
}
