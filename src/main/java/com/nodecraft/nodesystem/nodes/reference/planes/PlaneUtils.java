package com.nodecraft.nodesystem.nodes.reference.planes;

import com.nodecraft.nodesystem.datatypes.PointData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

final class PlaneUtils {
    static final double EPS = 1.0e-12d;

    private PlaneUtils() {
    }

    static Vector3d resolvePoint(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        if (value instanceof Vector3d vector) {
            return new Vector3d(vector);
        }
        if (value instanceof Vec3d vector) {
            return new Vector3d(vector.x, vector.y, vector.z);
        }
        if (value instanceof BlockPos blockPos) {
            return new Vector3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        }
        return null;
    }

    static boolean isFinite(Vector3d vector) {
        return vector != null
            && Double.isFinite(vector.x)
            && Double.isFinite(vector.y)
            && Double.isFinite(vector.z);
    }

    static boolean isUsableNormal(Vector3d normal) {
        return isFinite(normal) && normal.lengthSquared() > EPS;
    }

    static BlockPos toBlockPos(Vector3d vector, BlockPos fallback) {
        if (!isFinite(vector)) {
            return fallback;
        }
        return BlockPos.ofFloored(vector.x, vector.y, vector.z);
    }
}
