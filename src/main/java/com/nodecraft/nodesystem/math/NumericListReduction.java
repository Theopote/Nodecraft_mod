package com.nodecraft.nodesystem.math;

import com.nodecraft.nodesystem.util.StrictDoubleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared finite DOUBLE_LIST parsing and reduction for list numeric nodes.
 * All reductions return {@link ScalarResult}; overflow and non-finite results fail closed.
 * List elements must be exact finite {@link Double} (same rule as Number Sequence / Map Numbers).
 */
public final class NumericListReduction {

    public record ParseResult(List<Double> values, boolean valid) {
        public static ParseResult invalid() {
            return new ParseResult(List.of(), false);
        }

        public static ParseResult ok(List<Double> values) {
            return new ParseResult(List.copyOf(values), true);
        }
    }

    private NumericListReduction() {
    }

    public static ParseResult parseFiniteNumbers(@Nullable Object listObj) {
        if (!(listObj instanceof List<?> list) || list.isEmpty()) {
            return ParseResult.invalid();
        }
        List<Double> values = new ArrayList<>(list.size());
        for (Object item : list) {
            Double value = StrictDoubleUtils.requireExactFiniteDouble(item);
            if (value == null) {
                return ParseResult.invalid();
            }
            values.add(value);
        }
        return ParseResult.ok(values);
    }

    public static ScalarResult sum(List<Double> values) {
        if (values.isEmpty()) {
            return ScalarResult.invalid();
        }
        double sum = values.getFirst();
        for (int i = 1; i < values.size(); i++) {
            ScalarResult step = ScalarMathOps.add(sum, values.get(i));
            if (!step.valid()) {
                return ScalarResult.invalid();
            }
            sum = step.value();
        }
        return ScalarResult.ok(sum);
    }

    public static ScalarResult product(List<Double> values) {
        if (values.isEmpty()) {
            return ScalarResult.invalid();
        }
        double product = values.getFirst();
        for (int i = 1; i < values.size(); i++) {
            ScalarResult step = ScalarMathOps.mul(product, values.get(i));
            if (!step.valid()) {
                return ScalarResult.invalid();
            }
            product = step.value();
        }
        return ScalarResult.ok(product);
    }

    /** Stable online mean; avoids sum overflow when the mean itself is representable. */
    public static ScalarResult average(List<Double> values) {
        if (values.isEmpty()) {
            return ScalarResult.invalid();
        }
        double mean = values.getFirst();
        for (int i = 1; i < values.size(); i++) {
            ScalarResult delta = ScalarMathOps.sub(values.get(i), mean);
            if (!delta.valid()) {
                return ScalarResult.invalid();
            }
            ScalarResult scaled = ScalarMathOps.div(delta.value(), i + 1);
            if (!scaled.valid()) {
                return ScalarResult.invalid();
            }
            ScalarResult next = ScalarMathOps.add(mean, scaled.value());
            if (!next.valid()) {
                return ScalarResult.invalid();
            }
            mean = next.value();
        }
        return ScalarResult.ok(mean);
    }

    /** {@code sorted} must be sorted ascending. */
    public static ScalarResult medianSorted(List<Double> sorted) {
        if (sorted.isEmpty()) {
            return ScalarResult.invalid();
        }
        int size = sorted.size();
        int mid = size / 2;
        if ((size & 1) == 1) {
            return ScalarResult.ok(sorted.get(mid));
        }
        double low = sorted.get(mid - 1);
        double high = sorted.get(mid);
        if (low == high) {
            return ScalarResult.ok(low);
        }
        return ScalarMathOps.lerp(low, high, 0.5d);
    }
}
