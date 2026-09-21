package com.nodecraft.nodesystem.nodes.reference.frames;

import com.nodecraft.nodesystem.util.SpatialValueResolver;
import org.joml.Vector3d;

/**
 * Finite / usable checks for frame construction. Point/vector conversion uses
 * {@link SpatialValueResolver}.
 */
final class FrameUtils {
    static final double EPS = 1.0e-12d;

    private FrameUtils() {
    }

    static Vector3d resolvePoint(Object value) {
        return SpatialValueResolver.resolvePoint(value);
    }

    static Vector3d resolveVector(Object value) {
        return SpatialValueResolver.resolveVector(value);
    }

    static boolean isFinite(Vector3d vector) {
        return vector != null
            && Double.isFinite(vector.x)
            && Double.isFinite(vector.y)
            && Double.isFinite(vector.z);
    }

    static boolean isUsableAxis(Vector3d axis) {
        return isFinite(axis) && axis.lengthSquared() > EPS;
    }

    static Vector3d normalizedDirection(Vector3d from, Vector3d to) {
        if (!isFinite(from) || !isFinite(to)) {
            return null;
        }
        Vector3d axis = new Vector3d(to).sub(from);
        if (!isUsableAxis(axis)) {
            return null;
        }
        return axis.normalize();
    }
}
