package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.util.BlockSpace;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

final class DeformationUtils {
    static final double EPS = 1.0e-9d;

    private DeformationUtils() {
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

    static boolean isFinite(Vector3d vector) {
        return vector != null
            && Double.isFinite(vector.x)
            && Double.isFinite(vector.y)
            && Double.isFinite(vector.z);
    }

    static boolean isUsableDirection(Vector3d vector) {
        return isFinite(vector) && vector.lengthSquared() > EPS;
    }

    static double resolveFiniteDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            double candidate = number.doubleValue();
            if (Double.isFinite(candidate)) {
                return candidate;
            }
        }
        return fallback;
    }

    static int resolveInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    static double finiteOrCurrent(Object value, double current) {
        return resolveFiniteDouble(value, current);
    }

    static int intOrCurrent(Object value, int current) {
        return value instanceof Number number ? number.intValue() : current;
    }

    static Vector3d rotateAroundAxis(Vector3d vector, Vector3d axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vector3d term1 = new Vector3d(vector).mul(cos);
        Vector3d term2 = new Vector3d(axis).cross(vector, new Vector3d()).mul(sin);
        Vector3d term3 = new Vector3d(axis).mul(axis.dot(vector) * (1.0d - cos));
        return term1.add(term2).add(term3);
    }
}
