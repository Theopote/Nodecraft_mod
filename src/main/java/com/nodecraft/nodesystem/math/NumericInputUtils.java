package com.nodecraft.nodesystem.math;

/**
 * Shared finite-value and clamp helpers for Input Numeric v1 nodes.
 * Precision / decimal-place properties are UI-only and must not quantize graph outputs.
 */
public final class NumericInputUtils {

    private NumericInputUtils() {
    }

    /** Accept {@code candidate} when finite; otherwise keep {@code previous}. */
    public static double acceptFiniteOrKeep(double candidate, double previous) {
        return Double.isFinite(candidate) ? candidate : previous;
    }

    /** Optional bounds: {@code ±Infinity} allowed; {@code NaN} rejected (returns {@code fallback}). */
    public static double sanitizeOptionalBound(double bound, double fallback) {
        return Double.isNaN(bound) ? fallback : bound;
    }

    /** Slider bounds must be finite; otherwise returns {@code fallback}. */
    public static double sanitizeFiniteBound(double bound, double fallback) {
        return Double.isFinite(bound) ? bound : fallback;
    }

    /**
     * Clamp between optional min/max. Non-finite min/max are treated as unbounded
     * ({@code Double.NEGATIVE_INFINITY} / {@code Double.POSITIVE_INFINITY}).
     */
    public static double clampOptionalBounds(double value, double min, double max) {
        double result = value;
        if (Double.isFinite(min)) {
            result = Math.max(min, result);
        }
        if (Double.isFinite(max)) {
            result = Math.min(max, result);
        }
        return result;
    }

    /** Clamp between finite min/max (inclusive). */
    public static double clampFiniteRange(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Normalized parameter in {@code [0, 1]}; zero-width range returns {@code 0}. */
    public static double normalizedInRange(double value, double min, double max) {
        double range = max - min;
        if (range == 0.0d) {
            return 0.0d;
        }
        return clamp01((value - min) / range);
    }

    /** Map {@code t ∈ [0, 1]} back to {@code [min, max]}. */
    public static double lerpFromNormalized(double t, double min, double max) {
        return min + clamp01(t) * (max - min);
    }

    /** Wrap degrees to {@code [0, 360)}. Non-finite input yields {@code NaN}. */
    public static double wrapDegrees360(double angle) {
        if (!Double.isFinite(angle)) {
            return Double.NaN;
        }
        double normalized = angle % 360.0d;
        if (normalized < 0.0d) {
            normalized += 360.0d;
        }
        return normalized;
    }

    /**
     * Directed span {@code end - start}. Returns {@code NaN} when either endpoint is
     * non-finite or subtraction overflows to non-finite.
     */
    public static double safeDirectedSpan(double start, double end) {
        if (!Double.isFinite(start) || !Double.isFinite(end)) {
            return Double.NaN;
        }
        double span = end - start;
        return Double.isFinite(span) ? span : Double.NaN;
    }

    /** Snap to step within {@code [min, max]}; invalid step returns clamped value only. */
    public static double snapToStep(double value, double min, double max, double step) {
        double clamped = clampFiniteRange(value, min, max);
        if (!Double.isFinite(step) || step <= 0.0d) {
            return clamped;
        }
        clamped = min + Math.round((clamped - min) / step) * step;
        return clampFiniteRange(clamped, min, max);
    }

    public static String formatString(int decimalPlaces, int maxPlaces) {
        int safe = Math.max(0, Math.min(maxPlaces, decimalPlaces));
        return "%." + safe + "f";
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
