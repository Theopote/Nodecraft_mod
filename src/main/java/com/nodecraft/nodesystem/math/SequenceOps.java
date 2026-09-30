package com.nodecraft.nodesystem.math;

import com.nodecraft.nodesystem.util.GenerationLimits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared sequence generation for Sequence nodes (Number Sequence / Number Series).
 * <p>
 * Exact step (no epsilon). All emitted values are finite. Hard-capped at
 * {@link GenerationLimits#MAX_LIST_ELEMENTS}.
 */
public final class SequenceOps {

    public static final String ERROR_NON_FINITE_VALUE = "non_finite_value";
    public static final String ERROR_FLOAT_PRECISION_STALL = "float_precision_stall";
    public static final String ERROR_MAX_ELEMENTS_EXCEEDED = "max_elements_exceeded";

    private SequenceOps() {
    }

    /**
     * Value-bounded sequence: Start → End by Step.
     * Does not guarantee End is included unless a generated value lands on End.
     */
    public static SequenceResult range(double start, double end, double step) {
        return range(start, end, step, GenerationLimits.MAX_LIST_ELEMENTS);
    }

    /** Package-private for contract tests with a smaller element budget. */
    static SequenceResult range(double start, double end, double step, int maxElements) {
        if (!Double.isFinite(start) || !Double.isFinite(end) || !Double.isFinite(step)) {
            return SequenceResult.ok(List.of());
        }
        if (step == 0.0d) {
            return SequenceResult.ok(List.of());
        }
        if (start == end) {
            return SequenceResult.ok(List.of(start));
        }
        if ((start < end && step < 0.0d) || (start > end && step > 0.0d)) {
            return SequenceResult.ok(List.of());
        }
        if (maxElements <= 0) {
            return SequenceResult.invalid(ERROR_MAX_ELEMENTS_EXCEEDED);
        }

        boolean ascending = step > 0.0d;
        List<Double> numbers = new ArrayList<>();
        double previous = Double.NaN;

        for (int i = 0; i < maxElements; i++) {
            double value = start + (double) i * step;
            if (!Double.isFinite(value)) {
                return SequenceResult.invalid(ERROR_NON_FINITE_VALUE);
            }
            if (ascending ? value > end : value < end) {
                return SequenceResult.ok(Collections.unmodifiableList(numbers));
            }
            if (i > 0 && value == previous) {
                return SequenceResult.invalid(ERROR_FLOAT_PRECISION_STALL);
            }
            numbers.add(value);
            previous = value;
        }

        // Budget full: exceed only when another in-range finite value would still be required.
        double next = start + (double) maxElements * step;
        if (!Double.isFinite(next) || (ascending ? next > end : next < end)) {
            return SequenceResult.ok(Collections.unmodifiableList(numbers));
        }
        return SequenceResult.invalid(ERROR_MAX_ELEMENTS_EXCEEDED);
    }

    /**
     * Count-bounded series: Start + i * Step for i in [0, count).
     * Fails closed when a non-finite value appears before completing Count.
     */
    public static SequenceResult series(double start, double step, int count) {
        if (!Double.isFinite(start) || !Double.isFinite(step)) {
            return SequenceResult.ok(List.of());
        }
        int n = GenerationLimits.clampNonNegativeCount(count);
        if (n == 0) {
            return SequenceResult.ok(List.of());
        }

        List<Double> values = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double value = start + (double) i * step;
            if (!Double.isFinite(value)) {
                return SequenceResult.invalid(ERROR_NON_FINITE_VALUE);
            }
            values.add(value);
        }
        return SequenceResult.ok(Collections.unmodifiableList(values));
    }
}
