package com.nodecraft.nodesystem.nodes.math.compare;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Shared comparison for Compare v1 nodes.
 * <p>
 * Numeric ordering is exact (no epsilon). Non-finite operands fail closed to {@code false}.
 * Generic equality accepts ANY types but never coerces across unrelated types.
 */
final class CompareUtils {

    private CompareUtils() {
    }

    static boolean genericEqual(@Nullable Object left, @Nullable Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        switch (left) {
            case Number leftNumber when right instanceof Number rightNumber -> {
                return numericEqual(leftNumber.doubleValue(), rightNumber.doubleValue());
            }
            case String leftString when right instanceof String rightString -> {
                return leftString.equals(rightString);
            }
            case Boolean leftBoolean when right instanceof Boolean rightBoolean -> {
                return leftBoolean.equals(rightBoolean);
            }
            default -> {
            }
        }
        if (left.getClass() == right.getClass()) {
            return Objects.equals(left, right);
        }
        return false;
    }

    static boolean numericEqual(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            return false;
        }
        return a == b;
    }

    static boolean numericLess(@Nullable Object left, @Nullable Object right) {
        Double a = asFiniteDouble(left);
        Double b = asFiniteDouble(right);
        if (a == null || b == null) {
            return false;
        }
        return a < b;
    }

    static boolean numericLessOrEqual(@Nullable Object left, @Nullable Object right) {
        Double a = asFiniteDouble(left);
        Double b = asFiniteDouble(right);
        if (a == null || b == null) {
            return false;
        }
        return a <= b;
    }

    static boolean numericGreater(@Nullable Object left, @Nullable Object right) {
        Double a = asFiniteDouble(left);
        Double b = asFiniteDouble(right);
        if (a == null || b == null) {
            return false;
        }
        return a > b;
    }

    static boolean numericGreaterOrEqual(@Nullable Object left, @Nullable Object right) {
        Double a = asFiniteDouble(left);
        Double b = asFiniteDouble(right);
        if (a == null || b == null) {
            return false;
        }
        return a >= b;
    }

    private static @Nullable Double asFiniteDouble(@Nullable Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double d = number.doubleValue();
        return Double.isFinite(d) ? d : null;
    }
}
