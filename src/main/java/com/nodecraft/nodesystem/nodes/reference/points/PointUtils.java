package com.nodecraft.nodesystem.nodes.reference.points;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.util.BlockSpace;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

final class PointUtils {

    static final double EPS = 1.0e-12d;

    private PointUtils() {
    }

    /** Strict POINT port: accepts {@link PointData} only. */
    static Vector3d toPointPosition(Object value) {
        if (value instanceof PointData pointData) {
            return pointData.getPosition();
        }
        return null;
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
            return BlockSpace.cellCenter(blockPos);
        }
        return null;
    }

    static boolean isFinite(Vector3d point) {
        return point != null
            && Double.isFinite(point.x)
            && Double.isFinite(point.y)
            && Double.isFinite(point.z);
    }

    static boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    static BlockPos toBlockPos(Object value) {
        if (value instanceof BlockPos blockPos) {
            return blockPos;
        }
        Vector3d point = resolvePoint(value);
        if (!isFinite(point)) {
            return null;
        }
        return BlockPos.ofFloored(point.x, point.y, point.z);
    }

    static double distanceSquared(Vector3d a, Vector3d b) {
        return a.distanceSquared(b);
    }
}
