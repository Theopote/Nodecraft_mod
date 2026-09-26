package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.VectorData;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Shared vector validation helpers used across reference vector nodes.
 */
public final class VectorUtils {

    public static final double EPS = SpatialTolerance.EPS;
    public static final double EPS_SQ = SpatialTolerance.EPS_SQ;

    private VectorUtils() {
    }

    /** Strict VECTOR port: accepts {@link VectorData}, {@link Vector3d}, and legacy {@link Vec3d}. */
    public static @Nullable Vector3d toVector(@Nullable Object value) {
        if (value instanceof VectorData vectorData) {
            return vectorData.components();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vec3d) {
            return new Vector3d(vec3d.x, vec3d.y, vec3d.z);
        }
        return null;
    }

    /** Canonical typed output for VECTOR ports. */
    public static @Nullable VectorData toVectorPort(@Nullable Vector3d vector) {
        return VectorData.canonical(vector);
    }

    public static boolean isFinite(@Nullable Vector3d vector) {
        return FrameUtils.isFinite(vector);
    }

    public static boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    public static boolean isNonZero(@Nullable Vector3d vector) {
        return vector != null && vector.lengthSquared() > EPS_SQ;
    }

    /**
     * Strict VECTOR_LIST resolution: null/empty/non-Collection → null;
     * any non-VECTOR or non-finite entry → null; otherwise full list (no filtering).
     */
    public static @Nullable List<Vector3d> resolveStrictVectorList(@Nullable Object value) {
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            return null;
        }
        List<Vector3d> vectors = new ArrayList<>(collection.size());
        for (Object entry : collection) {
            Vector3d vector = toVector(entry);
            if (!isFinite(vector)) {
                return null;
            }
            vectors.add(vector);
        }
        return List.copyOf(vectors);
    }
}
