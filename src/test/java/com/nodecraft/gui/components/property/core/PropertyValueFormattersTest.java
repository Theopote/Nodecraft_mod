package com.nodecraft.gui.components.property.core;

import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void formatEditNumberPreservesPrecision() {
        assertEquals("1.234567", PropertyValueFormatters.formatEditNumber(1.234567d));
    }

    @Test
    void formatPolylineSummaryIsCompact() {
        PolylineData open = new PolylineData(List.of(
                new Vec3d(0, 0, 0),
                new Vec3d(3, 0, 0),
                new Vec3d(3, 4, 0)
        ));
        assertEquals("3 points · 2 segments · L 7.00", PropertyValueFormatters.formatPolylineSummary(open));

        PolylineData closed = new PolylineData(List.of(
                new Vec3d(0, 0, 0),
                new Vec3d(1, 0, 0),
                new Vec3d(0, 0, 0)
        ));
        assertEquals("3 points · 2 segments · L 2.00 · closed", PropertyValueFormatters.formatPolylineSummary(closed));
    }

    @Test
    void formatRegionSummaryIsCompact() {
        RegionData complete = new RegionData(new BlockPos(10, 64, 20), new BlockPos(30, 80, 45));
        assertEquals("From (10, 64, 20) to (30, 80, 45)", PropertyValueFormatters.formatRegionSummary(complete));

        RegionData incomplete = new RegionData(new BlockPos(1, 2, 3), null);
        assertEquals("Incomplete · Corner1 (1, 2, 3)", PropertyValueFormatters.formatRegionSummary(incomplete));
    }

    @Test
    void formatCurveSummaryDoesNotNeedSampling() {
        Curve curve = new Curve(Curve.CurveType.BEZIER, 16)
                .addControlPoint(new Vec3d(0, 0, 0))
                .addControlPoint(new Vec3d(1, 0, 0))
                .addControlPoint(new Vec3d(1, 1, 0))
                .addControlPoint(new Vec3d(0, 1, 0));
        assertEquals("BEZIER · 4 control points", PropertyValueFormatters.formatCurveSummary(curve));
    }

    @Test
    void formatColorSummariesIncludeHexAndRgba() {
        assertEquals("#55AACC", PropertyValueFormatters.formatColorHex(0x55 / 255f, 0xAA / 255f, 0xCC / 255f, 1f));
        assertEquals("RGBA(0.20, 0.40, 0.60, 0.80)", PropertyValueFormatters.formatColorRgba(0.2f, 0.4f, 0.6f, 0.8f));
        ColorData color = new ColorData(0.2f, 0.4f, 0.6f, 0.8f);
        assertTrue(PropertyValueFormatters.formatColorDataSummary(color).startsWith("#"));
        assertEquals("128 elements", PropertyValueFormatters.formatListSummary(128));
        assertEquals("1 position", PropertyValueFormatters.formatBlockPosListSummary(1));
    }
}
