package com.nodecraft.gui.components.property.core;

import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.util.Color;
import com.nodecraft.nodesystem.util.Curve;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.Locale;

public final class PropertyValueFormatters {

    /** Display format for scalar floats/doubles in the property panel. */
    public static final String DECIMAL_DISPLAY_FORMAT = "%.2f";
    private static final double DECIMAL_DISPLAY_EPSILON = 5e-3;
    /** Relative tolerance for recognizing mathematical constants (π, e). */
    private static final double SPECIAL_CONSTANT_REL_EPSILON = 1e-12;
    private static final double[] SPECIAL_CONSTANTS = {Math.PI, Math.E};

    private PropertyValueFormatters() {
    }

    public static String formatDecimal(double value) {
        return String.format(Locale.ROOT, DECIMAL_DISPLAY_FORMAT, value);
    }

    public static String formatDecimal(float value) {
        return String.format(Locale.ROOT, DECIMAL_DISPLAY_FORMAT, value);
    }

    /**
     * Text for editable numeric fields: two decimal places by default.
     * Keeps full precision for special constants such as π and e (and their negatives).
     */
    public static String formatEditNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.toString(value);
        }
        if (isSpecialMathematicalConstant(value)) {
            return Double.toString(value);
        }
        return formatDecimal(value);
    }

    /** True when {@code value} is (approximately) π, e, or their negatives. */
    public static boolean isSpecialMathematicalConstant(double value) {
        if (!Double.isFinite(value)) {
            return false;
        }
        double abs = Math.abs(value);
        for (double constant : SPECIAL_CONSTANTS) {
            double scale = Math.max(1.0d, constant);
            if (Math.abs(abs - constant) <= SPECIAL_CONSTANT_REL_EPSILON * scale) {
                return true;
            }
        }
        return false;
    }

    /** True when a displayed 2-decimal text no longer matches the live value. */
    public static boolean decimalDisplayDrifted(double displayed, double live) {
        return Math.abs(displayed - live) > DECIMAL_DISPLAY_EPSILON;
    }

    /** True when two stored double values differ (full precision, for commit decisions). */
    public static boolean doubleValueChanged(double oldValue, double newValue) {
        return Double.compare(oldValue, newValue) != 0;
    }

    public static String formatBlockPos(BlockPos pos) {
        if (pos == null) {
            return "(null)";
        }
        return String.format(Locale.ROOT, "(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }

    public static String formatVector3d(Vector3d vec) {
        if (vec == null) {
            return "(null)";
        }
        return String.format(
                Locale.ROOT,
                "(%s, %s, %s)",
                formatEditNumber(vec.x),
                formatEditNumber(vec.y),
                formatEditNumber(vec.z));
    }

    public static String formatVec3d(Vec3d vec) {
        if (vec == null) {
            return "(null)";
        }
        return String.format(
                Locale.ROOT,
                "(%s, %s, %s)",
                formatEditNumber(vec.x),
                formatEditNumber(vec.y),
                formatEditNumber(vec.z));
    }

    /** Compact triad for Point / Vector port previews. */
    public static String formatTriad(String label, double x, double y, double z) {
        return label
                + "["
                + formatEditNumber(x)
                + ", "
                + formatEditNumber(y)
                + ", "
                + formatEditNumber(z)
                + "]";
    }

    /** Format a boxed numeric port/property value for display. */
    public static String formatNumber(Number value) {
        if (value == null) {
            return "(null)";
        }
        if (value instanceof Float || value instanceof Double) {
            return formatEditNumber(value.doubleValue());
        }
        return Long.toString(value.longValue());
    }

    /** One-line summary for polyline properties in the panel. */
    public static String formatPolylineSummary(PolylineData polyline) {
        if (polyline == null) {
            return "(null)";
        }
        String summary = String.format(
                Locale.ROOT,
                "%d points · %d segments · L %.2f",
                polyline.getPointCount(),
                polyline.getSegmentCount(),
                polyline.getLength()
        );
        if (polyline.isClosed()) {
            return summary + " · closed";
        }
        return summary;
    }

    /** One-line summary for curve properties (no sampling). */
    public static String formatCurveSummary(Curve curve) {
        if (curve == null) {
            return "(null)";
        }
        return String.format(
                Locale.ROOT,
                "%s · %d control points",
                curve.getCurveType(),
                curve.size()
        );
    }

    public static String formatColorRgba(float r, float g, float b, float a) {
        return String.format(Locale.ROOT, "RGBA(%.2f, %.2f, %.2f, %.2f)", r, g, b, a);
    }

    public static String formatColorHex(float r, float g, float b, float a) {
        int ri = Math.round(clamp01(r) * 255.0f);
        int gi = Math.round(clamp01(g) * 255.0f);
        int bi = Math.round(clamp01(b) * 255.0f);
        int ai = Math.round(clamp01(a) * 255.0f);
        if (ai >= 255) {
            return String.format(Locale.ROOT, "#%02X%02X%02X", ri, gi, bi);
        }
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X", ri, gi, bi, ai);
    }

    public static String formatColorDataSummary(ColorData color) {
        if (color == null) {
            return "(null)";
        }
        return formatColorHex(color.r(), color.g(), color.b(), color.a())
                + "  "
                + formatColorRgba(color.r(), color.g(), color.b(), color.a());
    }

    public static String formatNodeColorSummary(Color color) {
        if (color == null) {
            return "(null)";
        }
        return formatColorHex(color.red(), color.green(), color.blue(), color.alpha())
                + "  "
                + formatColorRgba(color.red(), color.green(), color.blue(), color.alpha());
    }

    public static String formatListSummary(int size) {
        return size == 1 ? "1 element" : size + " elements";
    }

    public static String formatBlockPosListSummary(int size) {
        return size == 1 ? "1 position" : size + " positions";
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /** One-line summary for region properties in the panel. */
    public static String formatRegionSummary(RegionData region) {
        if (region == null) {
            return "(null)";
        }
        if (region.isComplete()) {
            return String.format(
                    Locale.ROOT,
                    "From %s to %s",
                    formatBlockPos(region.corner1()),
                    formatBlockPos(region.corner2())
            );
        }
        if (region.corner1() == null && region.corner2() == null) {
            return "Incomplete";
        }
        if (region.corner1() == null) {
            return "Incomplete · Corner2 " + formatBlockPos(region.corner2());
        }
        return "Incomplete · Corner1 " + formatBlockPos(region.corner1());
    }
}
