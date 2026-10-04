package com.nodecraft.nodesystem.nodes.geometry.solids;

import java.util.List;

/**
 * Shared Sweep property / connected-list preflight: scale &gt; 0, rotation finite.
 */
final class SweepFieldSupport {

    private SweepFieldSupport() {
    }

    static boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0d;
    }

    static boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    static boolean allPositiveFinite(List<Double> values) {
        if (values == null) {
            return false;
        }
        for (Double value : values) {
            if (value == null || !isPositiveFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
