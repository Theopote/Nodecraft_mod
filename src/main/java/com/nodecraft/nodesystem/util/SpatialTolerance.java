package com.nodecraft.nodesystem.util;

/**
 * Shared spatial tolerance constants for point/plane/frame/vector helpers.
 * <p>
 * Use {@link #EPS} for linear comparisons (|scalar|, coordinate deltas).
 * Use {@link #EPS_SQ} for squared-length comparisons (|v|²).
 */
public final class SpatialTolerance {

    public static final double EPS = 1.0e-12d;
    public static final double EPS_SQ = EPS * EPS;

    private SpatialTolerance() {
    }
}
