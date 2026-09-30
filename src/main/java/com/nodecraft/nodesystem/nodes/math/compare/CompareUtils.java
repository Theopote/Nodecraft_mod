package com.nodecraft.nodesystem.nodes.math.compare;

import com.nodecraft.nodesystem.math.ComparisonResult;
import com.nodecraft.nodesystem.util.NumericComparison;
import com.nodecraft.nodesystem.util.StrictDoubleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Shared comparison for Compare v2 nodes.
 * <p>
 * Numeric ordering is exact (no epsilon). Non-finite operands fail closed.
 * Generic equality accepts ANY types but never coerces across unrelated types.
 */
final class CompareUtils {

    private CompareUtils() {
    }

    static ComparisonResult compareEqual(
            @Nullable Object left,
            @Nullable Object right,
            boolean drivenLeft,
            boolean drivenRight
    ) {
        if (!drivenLeft && !drivenRight) {
            return ComparisonResult.invalid();
        }
        if (left == null || right == null) {
            return ComparisonResult.ok(left == right);
        }
        switch (left) {
            case Number leftNumber when right instanceof Number rightNumber -> {
                return ComparisonResult.ok(NumericComparison.numbersEqual(leftNumber, rightNumber));
            }
            case String leftString when right instanceof String rightString -> {
                return ComparisonResult.ok(leftString.equals(rightString));
            }
            case Boolean leftBoolean when right instanceof Boolean rightBoolean -> {
                return ComparisonResult.ok(leftBoolean.equals(rightBoolean));
            }
            default -> {
            }
        }
        if (left.getClass() == right.getClass()) {
            return ComparisonResult.ok(Objects.equals(left, right));
        }
        return ComparisonResult.ok(false);
    }

    static ComparisonResult compareNotEqual(
            @Nullable Object left,
            @Nullable Object right,
            boolean drivenLeft,
            boolean drivenRight
    ) {
        if (!drivenLeft && !drivenRight) {
            return ComparisonResult.invalid();
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            boolean leftFinite = Double.isFinite(leftNumber.doubleValue());
            boolean rightFinite = Double.isFinite(rightNumber.doubleValue());
            if (!leftFinite && !rightFinite) {
                return ComparisonResult.invalid();
            }
        }
        ComparisonResult equal = compareEqual(left, right, drivenLeft, drivenRight);
        if (!equal.valid()) {
            return ComparisonResult.invalid();
        }
        return ComparisonResult.ok(!equal.result());
    }

    static ComparisonResult compareLess(
            @Nullable Object left,
            @Nullable Object right,
            boolean drivenLeft,
            boolean drivenRight
    ) {
        return compareOrdering(left, right, drivenLeft, drivenRight, (a, b) -> a < b);
    }

    static ComparisonResult compareLessOrEqual(
            @Nullable Object left,
            @Nullable Object right,
            boolean drivenLeft,
            boolean drivenRight
    ) {
        return compareOrdering(left, right, drivenLeft, drivenRight, (a, b) -> a <= b);
    }

    static ComparisonResult compareGreater(
            @Nullable Object left,
            @Nullable Object right,
            boolean drivenLeft,
            boolean drivenRight
    ) {
        return compareOrdering(left, right, drivenLeft, drivenRight, (a, b) -> a > b);
    }

    static ComparisonResult compareGreaterOrEqual(
            @Nullable Object left,
            @Nullable Object right,
            boolean drivenLeft,
            boolean drivenRight
    ) {
        return compareOrdering(left, right, drivenLeft, drivenRight, (a, b) -> a >= b);
    }

    private static ComparisonResult compareOrdering(
            @Nullable Object left,
            @Nullable Object right,
            boolean drivenLeft,
            boolean drivenRight,
            OrderingPredicate predicate
    ) {
        if (!drivenLeft || !drivenRight) {
            return ComparisonResult.invalid();
        }
        Double a = asStrictFiniteDouble(left);
        Double b = asStrictFiniteDouble(right);
        if (a == null || b == null) {
            return ComparisonResult.invalid();
        }
        return ComparisonResult.ok(predicate.test(a, b));
    }

    private static @Nullable Double asStrictFiniteDouble(@Nullable Object value) {
        return StrictDoubleUtils.requireExactFiniteDouble(value);
    }

    @FunctionalInterface
    private interface OrderingPredicate {
        boolean test(double left, double right);
    }
}
