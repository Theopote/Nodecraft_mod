package com.nodecraft.gui.components.property.core;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector4d;

/**
 * Property-panel helpers for editing planes as unit-normal + equation offset D
 * ({@code n·x + D = 0}), matching {@link PlaneData#fromEquation(Vector4d)}.
 */
public final class PlaneEquationEdit {

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
     * Commits UI coefficients to a canonical {@link PlaneData}.
     * Non-unit normals scale D by {@code 1/|n|} via {@link PlaneData#fromEquation}.
     *
     * @return null when the normal is zero / non-finite
     */
    public static @Nullable PlaneData commit(float nx, float ny, float nz, float d) {
        return PlaneData.fromEquation(new Vector4d(nx, ny, nz, d));
    }

    public static @Nullable PlaneData commit(double nx, double ny, double nz, double d) {
        return PlaneData.fromEquation(new Vector4d(nx, ny, nz, d));
    }

    public static String formatReadOnly(PlaneData plane) {
        if (plane == null) {
            return "(null)";
        }
        Vector3d n = plane.getNormal();
        return String.format(
                java.util.Locale.ROOT,
                "N (%.3f, %.3f, %.3f)  D %.3f",
                n.x, n.y, n.z, displayOffset(plane)
        );
    }
}
