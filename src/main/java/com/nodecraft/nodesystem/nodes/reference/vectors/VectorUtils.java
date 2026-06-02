package com.nodecraft.nodesystem.nodes.reference.vectors;

import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

final class VectorUtils {
    static final double EPS = 1.0e-12d;

    private VectorUtils() {
    }

    static Vector3d toVector(Object value) {
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vector) {
            return new Vector3d(vector.x, vector.y, vector.z);
        }
        return null;
    }

    static boolean isFinite(Vector3d vector) {
        return vector != null
            && Double.isFinite(vector.x)
            && Double.isFinite(vector.y)
            && Double.isFinite(vector.z);
    }

    static boolean isFinite(double value) {
        return Double.isFinite(value);
    }
}
