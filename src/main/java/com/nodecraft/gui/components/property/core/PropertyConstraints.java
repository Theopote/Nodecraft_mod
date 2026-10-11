package com.nodecraft.gui.components.property.core;

import com.nodecraft.nodesystem.api.NodeProperty;

/**
 * Numeric bounds for property editors. {@code null} min/max/step means unbounded / auto.
 */
public record PropertyConstraints(Double min, Double max, Double step) {

    public static final PropertyConstraints UNBOUNDED = new PropertyConstraints(null, null, null);

    public PropertyConstraints {
        min = sanitizeBound(min);
        max = sanitizeBound(max);
        if (step != null && (!Double.isFinite(step) || step <= 0.0d)) {
            step = null;
        }
        validateRange(min, max);
    }

    public static PropertyConstraints fromAnnotation(NodeProperty annotation) {
        if (annotation == null) {
            return UNBOUNDED;
        }
        Double min = sanitizeBound(annotation.min() == Double.NEGATIVE_INFINITY ? null : annotation.min());
        Double max = sanitizeBound(annotation.max() == Double.POSITIVE_INFINITY ? null : annotation.max());
        Double step = annotation.step() == 0.0d ? null : annotation.step();
        if (min == null && max == null && step == null) {
            return UNBOUNDED;
        }
        return new PropertyConstraints(min, max, step);
    }

    public PropertyConstraints merge(PropertyConstraints other) {
        if (other == null || other == UNBOUNDED) {
            return this;
        }
        Double mergedMin = mergeMin(min, other.min);
        Double mergedMax = mergeMax(max, other.max);
        Double mergedStep = step != null ? step : other.step;
        if (mergedMin == null && mergedMax == null && mergedStep == null) {
            return UNBOUNDED;
        }
        return new PropertyConstraints(mergedMin, mergedMax, mergedStep);
    }

    public boolean hasMin() {
        return min != null;
    }

    public boolean hasMax() {
        return max != null;
    }

    public boolean hasStep() {
        return step != null;
    }

    public int clampInt(int value) {
        int result = value;
        if (min != null) {
            result = Math.max(result, (int) Math.ceil(min));
        }
        if (max != null) {
            result = Math.min(result, (int) Math.floor(max));
        }
        return result;
    }

    public float clampFloat(float value) {
        float result = value;
        if (min != null) {
            result = Math.max(result, min.floatValue());
        }
        if (max != null) {
            result = Math.min(result, max.floatValue());
        }
        return result;
    }

    public double clampDouble(double value) {
        double result = value;
        if (min != null) {
            result = Math.max(result, min);
        }
        if (max != null) {
            result = Math.min(result, max);
        }
        return result;
    }

    public float dragSpeedForFloat() {
        if (step != null && step > 0.0d) {
            return step.floatValue();
        }
        return 0.01f;
    }

    public float dragSpeedForInt() {
        if (step != null && step > 0.0d) {
            return step.floatValue();
        }
        return 1.0f;
    }

    private static Double sanitizeBound(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        return value;
    }

    private static void validateRange(Double min, Double max) {
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException(
                    "Property constraints are contradictory: min=" + min + " > max=" + max);
        }
    }

    private static Double mergeMin(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }

    private static Double mergeMax(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.min(left, right);
    }
}
