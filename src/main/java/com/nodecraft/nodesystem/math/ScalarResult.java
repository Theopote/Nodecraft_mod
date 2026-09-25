package com.nodecraft.nodesystem.math;

/**
 * Result of a continuous scalar operation under the Scalar Math v1 finite-result contract.
 * When {@link #valid()} is false, {@link #value()} is {@link Double#NaN}.
 */
public record ScalarResult(boolean valid, double value) {

    public static ScalarResult ok(double value) {
        if (!Double.isFinite(value)) {
            return invalid();
        }
        return new ScalarResult(true, value);
    }

    public static ScalarResult invalid() {
        return new ScalarResult(false, Double.NaN);
    }
}
