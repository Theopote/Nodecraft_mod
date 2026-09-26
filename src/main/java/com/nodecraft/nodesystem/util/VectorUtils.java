package com.nodecraft.nodesystem.util;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Shared vector validation helpers used across reference vector nodes.
 */
public final class VectorUtils {

    public static final double EPS = 1.0e-12d;

    private VectorUtils() {
    }

    /** Strict VECTOR port: accepts {@link Vector3d} and legacy {@link Vec3d}. */
    public static @Nullable Vector3d toVector(@Nullable Object value) {
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vec3d) {
            return new Vector3d(vec3d.x, vec3d.y, vec3d.z);
        }
        return null;
    }

    public static boolean isFinite(@Nullable Vector3d vector) {
        return FrameUtils.isFinite(vector);
    }

    public static boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    public static boolean isNonZero(@Nullable Vector3d vector) {
        return vector != null && vector.lengthSquared() > EPS;
    }
}
