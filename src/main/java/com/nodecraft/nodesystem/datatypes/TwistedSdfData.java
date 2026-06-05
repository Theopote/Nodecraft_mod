package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

/**
 * Applies an axial twist domain transform before sampling an input SDF.
 */
public class TwistedSdfData implements SignedDistanceFieldData {
    private static final double EPS = 1.0e-9d;

    public enum ClampMode {
        CLAMP,
        REPEAT,
        UNBOUNDED
    }

    private final SignedDistanceFieldData source;
    private final Vector3d axisOrigin;
    private final Vector3d axisDirection;
    private final double angleRadians;
    private final double twistLength;
    private final ClampMode clampMode;

    public TwistedSdfData(SignedDistanceFieldData source,
                          Vector3d axisOrigin,
                          Vector3d axisDirection,
                          double angleDegrees,
                          double twistLength,
                          ClampMode clampMode) {
        this.source = source;
        this.axisOrigin = new Vector3d(axisOrigin);
        this.axisDirection = new Vector3d(axisDirection);
        if (this.axisDirection.lengthSquared() <= EPS) {
            this.axisDirection.set(0.0d, 1.0d, 0.0d);
        } else {
            this.axisDirection.normalize();
        }
        this.angleRadians = Math.toRadians(angleDegrees);
        this.twistLength = Math.max(EPS, Math.abs(twistLength));
        this.clampMode = clampMode == null ? ClampMode.CLAMP : clampMode;
    }

    public SignedDistanceFieldData getSource() {
        return source;
    }

    public Vector3d getAxisOrigin() {
        return new Vector3d(axisOrigin);
    }

    public Vector3d getAxisDirection() {
        return new Vector3d(axisDirection);
    }

    public double getAngleDegrees() {
        return Math.toDegrees(angleRadians);
    }

    public double getTwistLength() {
        return twistLength;
    }

    public ClampMode getClampMode() {
        return clampMode;
    }

    @Override
    public double sampleDistance(Vector3d point) {
        Vector3d sourcePoint = inverseTwistPoint(point);
        return source.sampleDistance(sourcePoint);
    }

    public Vector3d twistPoint(Vector3d point) {
        return twistPoint(point, 1.0d);
    }

    private Vector3d inverseTwistPoint(Vector3d point) {
        return twistPoint(point, -1.0d);
    }

    private Vector3d twistPoint(Vector3d point, double direction) {
        Vector3d offset = new Vector3d(point).sub(axisOrigin);
        double axialDistance = offset.dot(axisDirection);
        Vector3d axialComponent = new Vector3d(axisDirection).mul(axialDistance);
        Vector3d radialComponent = new Vector3d(offset).sub(axialComponent);

        double factor = applyClampMode(axialDistance / twistLength);
        double angle = angleRadians * factor * direction;
        Vector3d rotatedRadial = rotateAroundAxis(radialComponent, axisDirection, angle);
        return new Vector3d(axisOrigin).add(axialComponent).add(rotatedRadial);
    }

    private double applyClampMode(double normalizedDistance) {
        return switch (clampMode) {
            case CLAMP -> Math.max(0.0d, Math.min(1.0d, normalizedDistance));
            case REPEAT -> normalizedDistance - Math.floor(normalizedDistance);
            case UNBOUNDED -> normalizedDistance;
        };
    }

    private static Vector3d rotateAroundAxis(Vector3d vector, Vector3d axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vector3d term1 = new Vector3d(vector).mul(cos);
        Vector3d term2 = new Vector3d(axis).cross(vector, new Vector3d()).mul(sin);
        Vector3d term3 = new Vector3d(axis).mul(axis.dot(vector) * (1.0d - cos));
        return term1.add(term2).add(term3);
    }
}
