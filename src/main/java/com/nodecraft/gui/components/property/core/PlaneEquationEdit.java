package com.nodecraft.gui.components.property.core;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Property-panel helpers for editing planes as unit normal + offset D ({@code n·x + D = 0})
 * while preserving {@link PlaneData#getPoint()} construction origin semantics.
 */
public final class PlaneEquationEdit {

    private static final double NORMAL_EPS = 1.0e-6;

    private PlaneEquationEdit() {
    }

    /** Signed offset D from a canonical plane ({@code plane.w}). */
    public static double displayOffset(PlaneData plane) {
        if (plane == null) {
            return Double.NaN;
        }
        return plane.getPlane().w;
    }

    /**
     * Commits UI coefficients against the previous plane, preserving construction origin when possible.
     * <ul>
     *   <li>Normal direction change → {@code canonical(oldOrigin, newUnitNormal)} (D recomputed)</li>
     *   <li>Offset D change → move origin along the unit normal</li>
     *   <li>Both → project old origin onto the new equation plane along the new normal</li>
     * </ul>
     * D is always interpreted relative to a <em>unit</em> normal (normal length is ignored for D).
     *
     * @return null when the normal is zero / non-finite
     */
    public static @Nullable PlaneData commit(
            @Nullable PlaneData previous,
            float nx,
            float ny,
            float nz,
            float d
    ) {
        return commit(previous, (double) nx, ny, nz, d);
    }

    public static @Nullable PlaneData commit(
            @Nullable PlaneData previous,
            double nx,
            double ny,
            double nz,
            double d
    ) {
        if (!Double.isFinite(nx) || !Double.isFinite(ny) || !Double.isFinite(nz) || !Double.isFinite(d)) {
            return null;
        }
        Vector3d unit = VectorUtils.safeNormalize(new Vector3d(nx, ny, nz));
        if (unit == null) {
            return null;
        }

        if (previous == null) {
            // No prior origin: place construction point at -D * n (equation sample point).
            Vector3d origin = VectorUtils.safeScale(unit, -d);
            if (origin == null) {
                return null;
            }
            return PlaneData.canonical(origin, unit);
        }

        Vector3d oldOrigin = previous.getPoint();
        Vector3d oldNormal = previous.getNormal();
        double oldD = displayOffset(previous);
        boolean normalChanged = !normalsEqual(oldNormal, unit);
        boolean dChanged = Math.abs(oldD - d) > NORMAL_EPS;

        if (normalChanged && !dChanged) {
            return PlaneData.canonical(oldOrigin, unit);
        }
        if (!normalChanged && dChanged) {
            // n·O + oldD = 0; want n·O' + d = 0 → O' = O + (oldD - d) * n
            Vector3d delta = VectorUtils.safeScale(unit, oldD - d);
            Vector3d newOrigin = VectorUtils.safeAdd(oldOrigin, delta);
            if (newOrigin == null) {
                return null;
            }
            return PlaneData.canonical(newOrigin, unit);
        }
        if (normalChanged) {
            // Both changed: keep origin as close as possible on the new plane along n'.
            Vector3d newOrigin = projectOriginOntoEquation(oldOrigin, unit, d);
            if (newOrigin == null) {
                return null;
            }
            return PlaneData.canonical(newOrigin, unit);
        }

        // No meaningful change — return previous (same geometry + origin).
        return previous;
    }

    /**
     * @deprecated Prefer {@link #commit(PlaneData, float, float, float, float)} so construction origin is preserved.
     */
    @Deprecated
    public static @Nullable PlaneData commit(float nx, float ny, float nz, float d) {
        return commit(null, nx, ny, nz, d);
    }

    public static String formatReadOnly(PlaneData plane) {
        if (plane == null) {
            return "(null)";
        }
        Vector3d n = plane.getNormal();
        Vector3d o = plane.getPoint();
        return String.format(
                java.util.Locale.ROOT,
                "N (%.3f, %.3f, %.3f)  D %.3f  O (%.2f, %.2f, %.2f)",
                n.x, n.y, n.z, displayOffset(plane), o.x, o.y, o.z
        );
    }

    private static boolean normalsEqual(Vector3d a, Vector3d b) {
        return Math.abs(a.x - b.x) <= NORMAL_EPS
                && Math.abs(a.y - b.y) <= NORMAL_EPS
                && Math.abs(a.z - b.z) <= NORMAL_EPS;
    }

    /** O' = O - (n·O + D) * n so that n·O' + D = 0. */
    private static @Nullable Vector3d projectOriginOntoEquation(Vector3d origin, Vector3d unitNormal, double d) {
        double alignment = VectorUtils.safeDot(unitNormal, origin);
        if (!Double.isFinite(alignment)) {
            return null;
        }
        Vector3d delta = VectorUtils.safeScale(unitNormal, alignment + d);
        return VectorUtils.safeSubtract(origin, delta);
    }
}
