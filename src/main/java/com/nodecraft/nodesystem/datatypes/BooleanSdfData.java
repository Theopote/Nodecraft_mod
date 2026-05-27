package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

/**
 * Boolean and smooth-boolean composition of two SDF inputs.
 */
public class BooleanSdfData implements SignedDistanceFieldData {
    public enum Operation {
        UNION,
        INTERSECTION,
        DIFFERENCE
    }

    private final SignedDistanceFieldData left;
    private final SignedDistanceFieldData right;
    private final Operation operation;
    private final double smoothK;

    public BooleanSdfData(SignedDistanceFieldData left, SignedDistanceFieldData right, Operation operation, double smoothK) {
        this.left = left;
        this.right = right;
        this.operation = operation == null ? Operation.UNION : operation;
        this.smoothK = Math.max(0.0d, smoothK);
    }

    public SignedDistanceFieldData getLeft() {
        return left;
    }

    public SignedDistanceFieldData getRight() {
        return right;
    }

    public Operation getOperation() {
        return operation;
    }

    public double getSmoothK() {
        return smoothK;
    }

    @Override
    public double sampleDistance(Vector3d point) {
        double a = left.sampleDistance(point);
        double b = right.sampleDistance(point);
        return switch (operation) {
            case UNION -> smoothK > 0.0d ? smoothMin(a, b, smoothK) : Math.min(a, b);
            case INTERSECTION -> smoothK > 0.0d ? smoothMax(a, b, smoothK) : Math.max(a, b);
            case DIFFERENCE -> smoothK > 0.0d ? smoothMax(a, -b, smoothK) : Math.max(a, -b);
        };
    }

    // Polynomial smooth minimum.
    private static double smoothMin(double a, double b, double k) {
        double h = clamp01(0.5d + 0.5d * (b - a) / k);
        return lerp(b, a, h) - k * h * (1.0d - h);
    }

    // Smooth maximum via duality.
    private static double smoothMax(double a, double b, double k) {
        return -smoothMin(-a, -b, k);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp01(double v) {
        return Math.max(0.0d, Math.min(1.0d, v));
    }
}
