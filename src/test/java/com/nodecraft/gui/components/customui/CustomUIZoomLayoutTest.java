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
            CustomUIMetricsSafe.layoutFrameHeight() + 4.0f);
        float expected = rowLogical + 8.0f + 4.0f + CustomUIMetricsSafe.layoutFrameHeight();
        assertEquals(expected, BlockPaletteWidget.preferredHeight(single, 8.0f), 0.01f);
    }

    @Test
    void palettePreferredHeightCapsAtMaxVisibleRows() {
        List<BlockPaletteEntry> many = new java.util.ArrayList<>();
        for (int i = 0; i < BlockPaletteWidget.MAX_VISIBLE_ROWS + 4; i++) {
            many.add(new BlockPaletteEntry("minecraft:stone", 1.0d));
        }
        BlockPaletteData large = new BlockPaletteData(many);
        BlockPaletteData capped = new BlockPaletteData(
            many.subList(0, BlockPaletteWidget.MAX_VISIBLE_ROWS));
        assertEquals(
            BlockPaletteWidget.preferredHeight(capped, 8.0f),
            BlockPaletteWidget.preferredHeight(large, 8.0f),
            0.01f);
    }

    @Test
    void heightPalettePreferredHeightIncludesBinsLabel() {
        float strip = HeightPaletteStripWidget.preferredStripHeight(96.0f);
        float expected = strip + 4.0f + CustomUIMetricsSafe.layoutTextLineHeight();
        assertEquals(expected, HeightPaletteStripWidget.preferredHeight(96.0f), 0.01f);
    }

    @Test
    void scaleLogicalTracksCanvasZoomLinearly() {
        assertEquals(36.0f, CustomUIMetricsSafe.scaleLogical(18.0f, 2.0f), 0.01f);
        assertEquals(9.0f, CustomUIMetricsSafe.scaleLogical(18.0f, 0.5f), 0.01f);
    }
}
