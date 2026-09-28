package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;

/**
 * Sector / annular-sector sweep angle language (Graph V91).
 * <p>
 * Valid: {@code 0 < |endDegrees - startDegrees| < 360}.
 * Full disks/rings must use Circle / Annulus, not Sector.
 */
public final class ProfileSweepAngleUtils {

    private ProfileSweepAngleUtils() {
    }

    /**
     * @return null when valid; otherwise an actionable error message
     */
    public static @Nullable String validateOpenSweepDegrees(double startDegrees, double endDegrees) {
        if (!Double.isFinite(startDegrees) || !Double.isFinite(endDegrees)) {
            return "Start and end angles must be finite";
        }
        double sweep = endDegrees - startDegrees;
        double absSweep = Math.abs(sweep);
        if (absSweep <= 1.0e-9d) {
            return "Start and end angles must differ";
        }
        if (absSweep >= 360.0d - 1.0e-9d) {
            return "Sweep angle must be strictly less than 360 degrees (use Circle or Annulus for a full ring)";
        }
        return null;
    }
}
