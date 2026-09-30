package com.nodecraft.nodesystem.util;

/**
 * Exact numeric equality without unchecked {@code doubleValue()} precision loss.
 */
public final class NumericComparison {

    private NumericComparison() {
    }

    /**
     * Returns whether two {@link Number} values are mathematically equal under Compare v2 rules.
     * Non-finite operands fail closed to {@code false}.
     */
    public static boolean numbersEqual(Number left, Number right) {
        if (isIntegral(left) && isIntegral(right)) {
            return integralEqual(left, right);
        }
        if (left instanceof Double leftDouble && right instanceof Double rightDouble) {
            return finiteDoubleEqual(leftDouble, rightDouble);
        }
        if (left instanceof Float leftFloat && right instanceof Float rightFloat) {
            return finiteDoubleEqual(leftFloat.doubleValue(), rightFloat.doubleValue());
        }
        return crossNumericEqual(left, right);
    }

    private static boolean integralEqual(Number left, Number right) {
        long leftValue = toLongExact(left);
        long rightValue = toLongExact(right);
        return leftValue == rightValue;
    }

    private static boolean crossNumericEqual(Number left, Number right) {
        if (isIntegral(left)) {
            return integralMatchesFloating(left, right);
        }
        if (isIntegral(right)) {
            return integralMatchesFloating(right, left);
        }
        double leftValue = left.doubleValue();
        double rightValue = right.doubleValue();
        if (!Double.isFinite(leftValue) || !Double.isFinite(rightValue)) {
            return false;
        }
        return leftValue == rightValue;
    }

    private static boolean integralMatchesFloating(Number integral, Number floating) {
        long integralValue = toLongExact(integral);
        double floatingValue = floating.doubleValue();
        if (!Double.isFinite(floatingValue)) {
            return false;
        }
        if ((double) integralValue != floatingValue) {
            return false;
        }
        return floatingValue == integralValue;
    }

    private static boolean finiteDoubleEqual(double left, double right) {
        if (!Double.isFinite(left) || !Double.isFinite(right)) {
            return false;
        }
        return left == right;
    }

    private static boolean isIntegral(Number value) {
        return value instanceof Integer
                || value instanceof Long
                || value instanceof Short
                || value instanceof Byte;
    }

    private static long toLongExact(Number value) {
        if (value instanceof Integer integer) {
            return integer.longValue();
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Short shortValue) {
            return shortValue.longValue();
        }
        if (value instanceof Byte byteValue) {
            return byteValue.longValue();
        }
        return value.longValue();
    }
}
