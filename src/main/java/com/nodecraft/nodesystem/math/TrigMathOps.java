package com.nodecraft.nodesystem.math;

/**
 * Shared trigonometry for Trigonometry v1 nodes and Expression.
 * <p>
 * Graph-facing angles are always in degrees. Successful results are finite;
 * failures return {@link ScalarResult#invalid()}.
 * <p>
 * Angles are period-reduced before radian conversion ({@code mod 360} for sin/cos,
 * {@code mod 180} for tan) to improve numerical stability for large finite inputs.
 * This cannot recover phase detail already lost in the input {@code double}.
 */
public final class TrigMathOps {

    private TrigMathOps() {
    }

    public static ScalarResult sin(double angleDeg) {
        if (!Double.isFinite(angleDeg)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.sin(Math.toRadians(normalizeDegrees360(angleDeg))));
    }

    public static ScalarResult cos(double angleDeg) {
        if (!Double.isFinite(angleDeg)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.cos(Math.toRadians(normalizeDegrees360(angleDeg))));
    }

    public static ScalarResult tan(double angleDeg) {
        if (!Double.isFinite(angleDeg)) {
            return ScalarResult.invalid();
        }
        if (isTanSingularityDegrees(angleDeg)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.tan(Math.toRadians(normalizeDegrees180(angleDeg))));
    }

    public static ScalarResult asin(double value) {
        if (!Double.isFinite(value) || value < -1.0d || value > 1.0d) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.toDegrees(Math.asin(value)));
    }

    public static ScalarResult acos(double value) {
        if (!Double.isFinite(value) || value < -1.0d || value > 1.0d) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.toDegrees(Math.acos(value)));
    }

    public static ScalarResult atan(double value) {
        if (!Double.isFinite(value)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.toDegrees(Math.atan(value)));
    }

    public static ScalarResult atan2(double y, double x) {
        if (!Double.isFinite(y) || !Double.isFinite(x)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.toDegrees(Math.atan2(y, x)));
    }

    public static ScalarResult sinh(double value) {
        if (!Double.isFinite(value)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.sinh(value));
    }

    public static ScalarResult cosh(double value) {
        if (!Double.isFinite(value)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.cosh(value));
    }

    public static ScalarResult tanh(double value) {
        if (!Double.isFinite(value)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.tanh(value));
    }

    /** Exact singularity: angle congruent to 90° mod 180° (no epsilon). */
    static boolean isTanSingularityDegrees(double angleDeg) {
        double r = angleDeg % 180.0d;
        return r == 90.0d || r == -90.0d;
    }

    static double normalizeDegrees360(double angleDeg) {
        return angleDeg % 360.0d;
    }

    static double normalizeDegrees180(double angleDeg) {
        return angleDeg % 180.0d;
    }
}
