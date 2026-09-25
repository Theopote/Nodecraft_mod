package com.nodecraft.nodesystem.math;

import com.nodecraft.nodesystem.util.GenerationLimits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared sequence generation for Sequence v1 (Number Sequence / Number Series).
 * <p>
 * Exact step (no epsilon). All emitted values are finite. Hard-capped at
 * {@link GenerationLimits#MAX_LIST_ELEMENTS}.
 */
public final class SequenceOps {

    private SequenceOps() {
    }

    /**
     * Value-bounded sequence: Start → End by Step.
     * Does not guarantee End is included unless a generated value lands on End.
     */
    public static List<Double> range(double start, double end, double step) {
        if (!Double.isFinite(start) || !Double.isFinite(end) || !Double.isFinite(step)) {
            return List.of();
        }
        if (step == 0.0d) {
            return List.of();
        }
        if (start == end) {
            return List.of(start);
        }
        if ((start < end && step < 0.0d) || (start > end && step > 0.0d)) {
            return List.of();
        }

        boolean ascending = step > 0.0d;
        List<Double> numbers = new ArrayList<>();
        double previous = Double.NaN;

        for (int i = 0; i < GenerationLimits.MAX_LIST_ELEMENTS; i++) {
            double value = start + (double) i * step;
            if (!Double.isFinite(value)) {
                break;
            }
            if (ascending ? value > end : value < end) {
                break;
            }
            if (i > 0 && value == previous) {
                break;
            }
            numbers.add(value);
            previous = value;
        }

        return Collections.unmodifiableList(numbers);
    }

    /**
     * Count-bounded series: Start + i * Step for i in [0, count).
     * Stops before adding the first non-finite value.
     */
    public static List<Double> series(double start, double step, int count) {
        if (!Double.isFinite(start) || !Double.isFinite(step)) {
            return List.of();
        }
        int n = GenerationLimits.clampNonNegativeCount(count);
        if (n == 0) {
            return List.of();
        }

        List<Double> values = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double value = start + (double) i * step;
            if (!Double.isFinite(value)) {
                break;
            }
            values.add(value);
        }
        return Collections.unmodifiableList(values);
    }
}
