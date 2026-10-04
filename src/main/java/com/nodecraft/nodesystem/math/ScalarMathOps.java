package com.nodecraft.nodesystem.math;

import com.nodecraft.nodesystem.datatypes.NumericRangeData;

/**
 * Shared continuous scalar math for Scalar Math v1 nodes, Expression, and Graph Mapper.
 * <p>
 * Graph-facing zero / degeneracy uses exact equality (no magic epsilon).
 * Successful results are always finite; failures return {@link ScalarResult#invalid()}.
 */
public final class ScalarMathOps {

    private ScalarMathOps() {
    }

    public static ScalarResult add(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(a + b);
    }

    public static ScalarResult sub(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(a - b);
    }

    public static ScalarResult mul(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(a * b);
    }

    /** Division: invalid only when {@code b == 0.0d}, or inputs/result non-finite. */
    public static ScalarResult div(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b) || b == 0.0d) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(a / b);
    }

    /** Modulus: invalid only when {@code b == 0.0d}, or inputs/result non-finite. */
    public static ScalarResult mod(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b) || b == 0.0d) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(a % b);
    }

    public static ScalarResult pow(double base, double exponent) {
        if (!Double.isFinite(base) || !Double.isFinite(exponent)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.pow(base, exponent));
    }

    public static ScalarResult abs(double value) {
        if (!Double.isFinite(value)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.abs(value));
    }

    public static ScalarResult sqrt(double value) {
        if (!Double.isFinite(value) || value < 0.0d) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.sqrt(value));
    }

    public static ScalarResult log(double number, double base) {
        if (!Double.isFinite(number) || !Double.isFinite(base)
            || number <= 0.0d || base <= 0.0d || base == 1.0d) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.log(number) / Math.log(base));
    }

    public static ScalarResult min(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.min(a, b));
    }

    public static ScalarResult max(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.max(a, b));
    }

    public static ScalarResult sign(double value) {
        if (!Double.isFinite(value)) {
            return ScalarResult.invalid();
        }
        double sign = value > 0.0d ? 1.0d : (value < 0.0d ? -1.0d : 0.0d);
        return ScalarResult.ok(sign);
    }

    /** Nearest integer-valued double, ties-to-even ({@link Math#rint(double)}). */
    public static ScalarResult round(double value) {
        if (!Double.isFinite(value)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.rint(value));
    }

    public static ScalarResult floor(double value) {
        if (!Double.isFinite(value)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.floor(value));
    }

    public static ScalarResult ceil(double value) {
        if (!Double.isFinite(value)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(Math.ceil(value));
    }

    /** {@code frac(x) = x - floor(x)}. */
    public static ScalarResult frac(double value) {
        if (!Double.isFinite(value)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(value - Math.floor(value));
    }

    /** Linear interpolate; T outside [0,1] extrapolates. Uses FMA to avoid {@code b - a} overflow. */
    public static ScalarResult lerp(double a, double b, double t) {
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(t)) {
            return ScalarResult.invalid();
        }
        double result = Math.fma(t, b, Math.fma(-t, a, a));
        if (!Double.isFinite(result)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(result);
    }

    /**
     * Hermite smoothstep between edge0 and edge1 (may be reversed).
     * Invalid when edges coincide ({@code edge0 == edge1}).
     */
    public static ScalarResult smoothstep(double value, double edge0, double edge1) {
        ScalarResult normalized = normalizeInterval(value, edge0, edge1);
        if (!normalized.valid()) {
            return normalized;
        }
        double clamped = clamp01(normalized.value());
        return ScalarResult.ok(clamped * clamped * (3.0d - 2.0d * clamped));
    }

    /**
     * Normalized and clamped parameter for smoothstep (same validity as {@link #smoothstep}).
     */
    public static ScalarResult smoothstepT(double value, double edge0, double edge1) {
        ScalarResult normalized = normalizeInterval(value, edge0, edge1);
        if (!normalized.valid()) {
            return normalized;
        }
        return ScalarResult.ok(clamp01(normalized.value()));
    }

    /**
     * Directed normalization {@code (value - edge0) / (edge1 - edge0)} with overflow-safe fallback
     * when {@code edge1 - edge0} is non-finite.
     */
    static ScalarResult normalizeInterval(double value, double edge0, double edge1) {
        if (!Double.isFinite(value) || !Double.isFinite(edge0) || !Double.isFinite(edge1)
            || edge0 == edge1) {
            return ScalarResult.invalid();
        }

        double span = edge1 - edge0;
        if (Double.isFinite(span) && span != 0.0d) {
            double t = (value - edge0) / span;
            if (!Double.isFinite(t)) {
                return ScalarResult.invalid();
            }
            return ScalarResult.ok(t);
        }

        boolean ascending = edge0 < edge1;
        if (ascending) {
            if (value <= edge0) {
                return ScalarResult.ok(0.0d);
            }
            if (value >= edge1) {
                return ScalarResult.ok(1.0d);
            }
        } else {
            if (value >= edge0) {
                return ScalarResult.ok(0.0d);
            }
            if (value <= edge1) {
                return ScalarResult.ok(1.0d);
            }
        }

        double left = value - edge0;
        double right = edge1 - value;
        if (!Double.isFinite(left) || !Double.isFinite(right)) {
            return ScalarResult.invalid();
        }
        if (left == 0.0d) {
            return ScalarResult.ok(0.0d);
        }
        if (right == 0.0d) {
            return ScalarResult.ok(1.0d);
        }
        if (left > 0.0d && right > 0.0d && left == right) {
            return ScalarResult.ok(0.5d);
        }
        double sum = left + right;
        if (Double.isFinite(sum) && sum != 0.0d) {
            double t = left / sum;
            if (!Double.isFinite(t)) {
                return ScalarResult.invalid();
            }
            return ScalarResult.ok(t);
        }
        double ratio = right / left;
        if (!Double.isFinite(ratio)) {
            return ScalarResult.ok(Math.abs(left) > Math.abs(right) ? 0.0d : 1.0d);
        }
        double t = 1.0d / (1.0d + ratio);
        if (!Double.isFinite(t)) {
            return ScalarResult.invalid();
        }
        return ScalarResult.ok(t);
    }

    public static ScalarResult clamp(double value, double a, double b) {
        if (!Double.isFinite(value) || !Double.isFinite(a) || !Double.isFinite(b)) {
            return ScalarResult.invalid();
        }
        double lower = Math.min(a, b);
        double upper = Math.max(a, b);
        return ScalarResult.ok(Math.max(lower, Math.min(upper, value)));
    }

    public static ScalarResult clamp(double value, NumericRangeData domain) {
        if (domain == null) {
            return ScalarResult.invalid();
        }
        return clamp(value, domain.lower(), domain.upper());
    }

    /**
     * Remap value from source domain to target domain using {@link #normalizeInterval}
     * then FMA lerp on the target (same overflow-safe interval as Smoothstep).
     * Invalid when source edges coincide or any endpoint / result is non-finite.
     */
    public static ScalarResult remap(double value, NumericRangeData source, NumericRangeData target, boolean clampToTarget) {
        if (source == null || target == null) {
            return ScalarResult.invalid();
        }
        if (!Double.isFinite(target.start()) || !Double.isFinite(target.end())) {
            return ScalarResult.invalid();
        }
        ScalarResult normalized = normalizeInterval(value, source.start(), source.end());
        if (!normalized.valid()) {
            return normalized;
        }
        ScalarResult mapped = lerp(target.start(), target.end(), normalized.value());
        if (!mapped.valid()) {
            return mapped;
        }
        double result = mapped.value();
        if (clampToTarget) {
            result = Math.max(target.lower(), Math.min(target.upper(), result));
        }
        return ScalarResult.ok(result);
    }

    public static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    public static boolean isDegenerateDomain(NumericRangeData domain) {
        return domain == null || domain.delta() == 0.0d;
    }
}
