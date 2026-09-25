package com.nodecraft.nodesystem.math;

import org.jetbrains.annotations.Nullable;

/**
 * Shared parameter resolution and scalar field combination for Field v1.
 * Field math inherits {@link ScalarMathOps} finite semantics point-wise.
 */
public final class FieldMath {

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

    /** Port/property DOUBLE: finite value or {@code fallback}. */
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
