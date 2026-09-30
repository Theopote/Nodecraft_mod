package com.nodecraft.nodesystem.math;

/**
 * Result of a compare operation under the Compare v2 valid/result contract.
 * When {@link #valid()} is false, {@link #result()} is always {@code false}.
 */
public record ComparisonResult(boolean valid, boolean result) {

    public static ComparisonResult ok(boolean result) {
        return new ComparisonResult(true, result);
    }

    public static ComparisonResult invalid() {
        return new ComparisonResult(false, false);
    }
}
