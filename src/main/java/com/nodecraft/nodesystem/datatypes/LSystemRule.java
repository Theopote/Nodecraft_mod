package com.nodecraft.nodesystem.datatypes;

import org.jspecify.annotations.NonNull;

/**
 * L-system production rule: rewrite {@code symbol} to {@code production} with relative {@code weight}.
 */
public record LSystemRule(String symbol, String production, double weight) {

    public LSystemRule(String symbol, String production) {
        this(symbol, production, 1.0d);
    }

    public LSystemRule {
        symbol = symbol != null ? symbol : "";
        production = production != null ? production : "";
    }

    @Override
    public @NonNull String toString() {
        if (weight != 1.0d) {
            return symbol + " -> " + production + " (w=" + weight + ")";
        }
        return symbol + " -> " + production;
    }
}
