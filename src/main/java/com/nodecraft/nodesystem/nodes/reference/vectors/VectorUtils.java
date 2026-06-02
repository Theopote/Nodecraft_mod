package com.nodecraft.nodesystem.nodes.reference.vectors;

import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

final class VectorUtils {

    static final double EPS = 1.0e-12d;

    private VectorUtils() {}

    // ==================== 类型转换 ====================

    static Vector3d toVector(Object value) {
        if (value instanceof Vector3d v) {
            return new Vector3d(v);
        }
        if (value instanceof Vec3d v) {
            return new Vector3d(v.x, v.y, v.z);
        }
        return null;
    }

    // ==================== 合法性判断 ====================

    static boolean isFinite(Vector3d v) {
        return v != null
                && Double.isFinite(v.x)
                && Double.isFinite(v.y)
                && Double.isFinite(v.z);
    }

    static boolean isFinite(double v) {
        return Double.isFinite(v);
    }

    static boolean isNonZero(Vector3d v) {
        return v != null && v.lengthSquared() > EPS;
    }

    static boolean isValidInput(Vector3d v) {
        return isFinite(v) && isNonZero(v);
    }

    // ==================== 安全数学操作 ====================

    /**
     * 安全归一化：零向量时返回 (0, 0, 1)
     */
    static Vector3d safeNormalize(Vector3d v) {
        if (!isFinite(v) || v.lengthSquared() < EPS) {
            return new Vector3d(0, 0, 1);
        }
        return new Vector3d(v).normalize();
    }

    /**
     * 安全长度：零向量返回 0
     */
    static double safeLength(Vector3d v) {
        return isFinite(v) ? v.length() : 0.0;
    }

    static double safeLengthSquared(Vector3d v) {
        return isFinite(v) ? v.lengthSquared() : 0.0;
    }

    // ==================== 常用向量操作 ====================

    static Vector3d zero() {
        return new Vector3d();
    }

    static Vector3d unitY() {
        return new Vector3d(0, 1, 0);
    }

    /**
     * 安全的点积（处理 null）
     */
    static double safeDot(Vector3d a, Vector3d b) {
        if (!isFinite(a) || !isFinite(b)) return 0.0;
        return a.dot(b);
    }
}