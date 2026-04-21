package com.nodecraft.nodesystem.datatypes;

/**
 * Immutable numeric range value object for input nodes.
 */
public record NumericRangeData(double min, double max) {

    public double span() {
        return max - min;
    }

    public boolean contains(double value) {
        return value >= min && value <= max;
    }
}

