package com.nodecraft.nodesystem.nodes.transform.orientation;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import org.joml.Vector3d;

/**
 * Finite / usable checks for orientation math. Raw Point/Vector conversion belongs in
 * {@link com.nodecraft.nodesystem.util.SpatialValueResolver}.
 */
final class OrientationUtils {
    static final double EPS = 1.0e-12d;

    private OrientationUtils() {
    }

    static boolean isFinite(Vector3d vector) {
        return vector != null
            && Double.isFinite(vector.x)
            && Double.isFinite(vector.y)
            && Double.isFinite(vector.z);
    }

    static boolean isUsableDirection(Vector3d vector) {
        return isFinite(vector) && vector.lengthSquared() > EPS;
    }

    static boolean isUsablePlane(PlaneData plane) {
        return plane != null && isUsableDirection(plane.getNormal());
    }
}
