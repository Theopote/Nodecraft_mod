package com.nodecraft.nodesystem.datatypes;

/**
 * Directed numeric domain (A→B) for graph-facing interval values.
 * <p>
 * Unlike unordered bounds, {@code start} and {@code end} preserve direction so Remap can
 * express reversed mappings (e.g. 0→1 to 100→0). Clamp and Random sampling use
 * {@link #lower()} / {@link #upper()} instead.
 */
public record NumericRangeData(double start, double end) {

    private static final double EPS = 1.0e-12d;

    /** Directed span: {@code end - start}. */
    public double delta() {
        return end - start;
    }

    /** Absolute length of the domain. */
    public double length() {
        return Math.abs(delta());
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
     * Normalized parameter along the directed domain. Returns NaN when length is zero.
     */
    public double normalizedParameter(double value) {
        if (delta() == 0.0d) {
            return Double.NaN;
        }
        return (value - start) / delta();
    }

    /** Linear interpolation along the directed domain. */
    public double lerp(double t) {
        return start + t * delta();
    }

    /**
     * Builds a domain from legacy min/max property values (start=min, end=max).
     */
    public static NumericRangeData fromLegacyBounds(double min, double max) {
        return new NumericRangeData(min, max);
    }
}
