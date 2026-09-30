package com.nodecraft.nodesystem.math;

import org.jetbrains.annotations.Nullable;

/**
 * Result of a value-selection operation under the Logic v2 valid/result contract.
 * When {@link #valid()} is false, {@link #value()} is {@code null}.
 */
public record SelectionResult(boolean valid, @Nullable Object value, @Nullable String error) {

    public static SelectionResult ok(@Nullable Object value) {
        return new SelectionResult(true, value, "");
    }

    public static SelectionResult invalid(String error) {
        return new SelectionResult(false, null, error == null ? "" : error);
    }
}
