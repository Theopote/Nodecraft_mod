package com.nodecraft.nodesystem.math;

import org.jetbrains.annotations.Nullable;

/**
 * Shared parameter resolution and scalar field combination for Field v1.
 * Field math inherits {@link ScalarMathOps} finite semantics point-wise.
 */
public final class FieldMath {

    /** Minimum falloff radius for attractor fields (linear divisor floor). */
    public static final double MIN_ATTRACTOR_FALLOFF_RADIUS = 1.0e-9d;

    /** Minimum falloff exponent for attractor fields (INVERSE / LINEAR modes). */
    public static final double MIN_ATTRACTOR_FALLOFF_EXPONENT = 0.001d;

    public enum ScalarCombineOp {
        ADD,
        SUB,
        MUL,
        DIV,
        MIN,
        MAX,
        POW
    }

    private FieldMath() {
    }

    /**
     * Finite value or {@code fallback}. Not for graph ports — field nodes use
     * {@code FieldSampleUtils.resolveOptional*} (OptionalPortDrive + range).
     */
    public static double resolveFinite(@Nullable Object value, double fallback) {
        if (value instanceof Number number) {
            double d = number.doubleValue();
            return Double.isFinite(d) ? d : fallback;
        }
        return fallback;
    }

    /** Finite and strictly positive, else {@code fallback}. */
    public static double resolvePositive(@Nullable Object value, double fallback) {
        if (value instanceof Number number) {
            double d = number.doubleValue();
            return Double.isFinite(d) && d > 0.0d ? d : fallback;
        }
        return fallback;
    }

    /** Attractor falloff radius: finite and {@code >=} {@link #MIN_ATTRACTOR_FALLOFF_RADIUS}. */
    public static double resolveAttractorRadius(@Nullable Object value, double fallback) {
        return resolveMinInclusive(value, fallback, MIN_ATTRACTOR_FALLOFF_RADIUS);
    }

    /** Attractor falloff exponent: finite and {@code >=} {@link #MIN_ATTRACTOR_FALLOFF_EXPONENT}. */
    public static double resolveAttractorExponent(@Nullable Object value, double fallback) {
        return resolveMinInclusive(value, fallback, MIN_ATTRACTOR_FALLOFF_EXPONENT);
    }

    private static double resolveMinInclusive(@Nullable Object value, double fallback, double minInclusive) {
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (Double.isFinite(d) && d >= minInclusive) {
                return d;
            }
            return validMinFallback(fallback, minInclusive);
        }
        return validMinFallback(fallback, minInclusive);
    }

    private static double validMinFallback(double fallback, double minInclusive) {
        return Double.isFinite(fallback) && fallback >= minInclusive ? fallback : minInclusive;
    }

    /** Finite and non-negative, else {@code fallback}. */
    public static double resolveNonNegative(@Nullable Object value, double fallback) {
        if (value instanceof Number number) {
            double d = number.doubleValue();
            return Double.isFinite(d) && d >= 0.0d ? d : fallback;
        }
        return fallback;
    }

    /**
     * Combines two scalar samples using Scalar Math v1 rules.
     * Invalid operations produce {@link Double#NaN}.
     */
    public static double combineScalars(double a, double b, ScalarCombineOp op) {
        ScalarCombineOp safeOp = op == null ? ScalarCombineOp.ADD : op;
        ScalarResult result = switch (safeOp) {
            case ADD -> ScalarMathOps.add(a, b);
            case SUB -> ScalarMathOps.sub(a, b);
            case MUL -> ScalarMathOps.mul(a, b);
            case DIV -> ScalarMathOps.div(a, b);
            case MIN -> ScalarMathOps.min(a, b);
            case MAX -> ScalarMathOps.max(a, b);
            case POW -> ScalarMathOps.pow(a, b);
        };
        return result.valid() ? result.value() : Double.NaN;
    }
}
