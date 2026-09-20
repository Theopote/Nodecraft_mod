package com.nodecraft.gui.components.property.core;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.Locale;

public final class PropertyValueFormatters {

    /** Display format for scalar floats/doubles in the property panel. */
    public static final String DECIMAL_DISPLAY_FORMAT = "%.2f";
    private static final double DECIMAL_DISPLAY_EPSILON = 5e-3;

    private PropertyValueFormatters() {
    }

    public static String formatDecimal(double value) {
        return String.format(Locale.ROOT, DECIMAL_DISPLAY_FORMAT, value);
    }

    public static String formatDecimal(float value) {
        return String.format(Locale.ROOT, DECIMAL_DISPLAY_FORMAT, value);
    }

    /** True when a displayed 2-decimal text no longer matches the live value. */
    public static boolean decimalDisplayDrifted(double displayed, double live) {
        return Math.abs(displayed - live) > DECIMAL_DISPLAY_EPSILON;
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
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", vec.x, vec.y, vec.z);
    }

    public static String formatVec3d(Vec3d vec) {
        if (vec == null) {
            return "(null)";
        }
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", vec.x, vec.y, vec.z);
    }
}
