package com.nodecraft.nodesystem.util;

/**
 * Overflow-aware scalar helpers for primitive analytical outputs.
 */
public final class PrimitiveNumericUtils {

    private PrimitiveNumericUtils() {
    }

    public static boolean allFinite(double... values) {
        if (values == null) {
            return false;
        }
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    public static double safeSquare(double value) {
        if (!Double.isFinite(value)) {
            return Double.NaN;
        }
        double squared = value * value;
        return Double.isFinite(squared) ? squared : Double.NaN;
    }

    public static double safeCube(double value) {
        double squared = safeSquare(value);
        if (!Double.isFinite(squared)) {
            return Double.NaN;
        }
        double cubed = squared * value;
        return Double.isFinite(cubed) ? cubed : Double.NaN;
    }

    public static double safeHypot(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return Double.NaN;
        }
        double hypot = Math.hypot(a, b);
        return Double.isFinite(hypot) ? hypot : Double.NaN;
    }

    public static double safeAdd(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return Double.NaN;
        }
        double sum = a + b;
        return Double.isFinite(sum) ? sum : Double.NaN;
    }

    public static double safeMul(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return Double.NaN;
        }
        double product = a * b;
        return Double.isFinite(product) ? product : Double.NaN;
    }

    public static boolean isFiniteMatrix(org.joml.Matrix3d matrix) {
        if (matrix == null) {
            return false;
        }
        return allFinite(
            matrix.m00, matrix.m01, matrix.m02,
            matrix.m10, matrix.m11, matrix.m12,
            matrix.m20, matrix.m21, matrix.m22
        );
    }
}
