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

    /**
     * Angular / collinearity fence on {@code |sin(theta)|} for unit-direction tests.
     * Distinct from {@link #EPS}, which is linear (coordinate / length).
     */
    public static final double ANGULAR_SIN_EPS = 1.0e-6d;
    public static final double ANGULAR_SIN_EPS_SQ = ANGULAR_SIN_EPS * ANGULAR_SIN_EPS;

    private SpatialTolerance() {
    }
}
