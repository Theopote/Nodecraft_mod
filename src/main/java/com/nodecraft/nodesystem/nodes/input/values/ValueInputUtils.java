package com.nodecraft.nodesystem.nodes.input.values;

import com.nodecraft.nodesystem.datatypes.ColorData;
import com.nodecraft.nodesystem.math.NumericInputUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Shared helpers for {@code input.values.*} source nodes.
 */
public final class ValueInputUtils {

    private ValueInputUtils() {
    }

    public static double finiteOrFallback(double candidate, double fallback) {
        return NumericInputUtils.finiteOrFallback(candidate, fallback);
    }

    public static boolean isFiniteColorChannels(double r, double g, double b, double a) {
        return Double.isFinite(r) && Double.isFinite(g) && Double.isFinite(b) && Double.isFinite(a);
    }

    public static boolean isFiniteColor(@Nullable ColorData color) {
        if (color == null) {
            return false;
        }
        return isFiniteColorChannels(color.r(), color.g(), color.b(), color.a());
    }

    public static ColorData toColorData(float r, float g, float b, float a) {
        return new ColorData(r, g, b, a);
    }
}
