package com.nodecraft.gui.components.customui;

import com.nodecraft.nodesystem.util.BlockPaletteData;
import com.nodecraft.nodesystem.util.BlockPaletteEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomUIZoomLayoutTest {

    @Test
    void curvePlotPreferredHeightIsConstantLogicalSize() {
        assertEquals(96.0f, CurvePlotWidget.preferredHeight(), 0.01f);
        assertEquals(96.0f, CurvePlotWidget.LOGICAL_HEIGHT, 0.01f);
    }

    @Test
    void palettePreferredHeightUsesLogicalFrameMetrics() {
        BlockPaletteData single = new BlockPaletteData(
            List.of(new BlockPaletteEntry("minecraft:stone", 1.0d)));
        float rowLogical = Math.max(
            14.0f + 4.0f,
            CustomUIMetricsSafe.logicalFrameHeight() + 4.0f);
        float expected = rowLogical + 8.0f + CustomUIMetricsSafe.logicalFrameHeight();
        assertEquals(expected, BlockPaletteWidget.preferredHeight(single, 8.0f), 0.01f);
    }

    @Test
    void scaleLogicalTracksCanvasZoomLinearly() {
        assertEquals(36.0f, CustomUIMetricsSafe.scaleLogical(18.0f, 2.0f), 0.01f);
        assertEquals(9.0f, CustomUIMetricsSafe.scaleLogical(18.0f, 0.5f), 0.01f);
    }
}
