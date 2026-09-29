package com.nodecraft.nodesystem.datatypes;

import org.jetbrains.annotations.Nullable;

/**
 * Directed numeric domain (A→B) for graph-facing interval values.
 * <p>
 * Unlike unordered bounds, {@code start} and {@code end} preserve direction so Remap can
 * express reversed mappings (e.g. 0→1 to 100→0). Clamp and Random sampling use
 * {@link #lower()} / {@link #upper()} instead.
 * <p>
 * Prefer {@link #canonical(double, double)} at construction boundaries: finite Start/End
 * and finite directed span ({@code end - start}).
 */
public record NumericRangeData(double start, double end) {

    private static final double EPS = 1.0e-12d;

    /**
     * Builds a domain when Start, End, and directed span are all finite; otherwise {@code null}.
     */
    public static @Nullable NumericRangeData canonical(double start, double end) {
        if (!Double.isFinite(start) || !Double.isFinite(end)) {
            return null;
        }
        double span = end - start;
        if (!Double.isFinite(span)) {
            return null;
        }
        return new NumericRangeData(start, end);
    }

    /**
     * Directed span: {@code end - start}. Returns {@code NaN} when subtraction overflows.
     */
    public double delta() {
        double span = end - start;
        return Double.isFinite(span) ? span : Double.NaN;
    }

    /** Absolute length of the domain. Non-finite delta → {@code NaN}. */
    public double length() {
        double span = delta();
        return Double.isFinite(span) ? Math.abs(span) : Double.NaN;
    }

    public double lower() {
        return Math.min(start, end);
    }

    public double upper() {
        return Math.max(start, end);
    }

    /** Directed span (alias of {@link #delta()}). */
    public double span() {
        return delta();
    }

    /** @deprecated Use {@link #lower()}. */
    @Deprecated
    public double min() {
        return lower();
    }

    /** @deprecated Use {@link #upper()}. */
    @Deprecated
    public double max() {
        return upper();
    }

    public boolean contains(double value) {
        return value >= lower() - EPS && value <= upper() + EPS;
    }

    /**
     * Normalized parameter along the directed domain. Returns NaN when length is zero
     * or directed span is non-finite.
     */
    public double normalizedParameter(double value) {
        double d = delta();
        if (!Double.isFinite(d) || d == 0.0d) {
            return Double.NaN;
        }
        return (value - start) / d;
    }

    /**
     * Linear interpolation along the directed domain. Non-finite delta → {@code NaN}.
     */
    public double lerp(double t) {
        double d = delta();
        if (!Double.isFinite(d) || !Double.isFinite(t)) {
            return Double.NaN;
        }
        return start + t * d;
    }

    /**
     * Builds a domain from legacy min/max property values (start=min, end=max).
     * Returns {@code null} when endpoints or directed span are not finite.
     */
    public static @Nullable NumericRangeData fromLegacyBounds(double min, double max) {
        return canonical(min, max);
    }
}
