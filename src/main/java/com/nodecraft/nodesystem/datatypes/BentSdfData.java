package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.VectorUtils;
import org.joml.Vector3d;

/**
 * Applies an axial bend domain transform before sampling an input SDF.
 * <p>
 * Constructors are strict (Graph V101): no silent axis / normal / length repair.
 * Callers must supply a usable axis, a bend normal that is not parallel to the axis,
 * and a positive bend length.
 */
public class BentSdfData implements SignedDistanceFieldData {
    private static final double EPS = 1.0e-9d;

    public enum ClampMode {
        CLAMP,
        REPEAT,
        UNBOUNDED
    }

    private final SignedDistanceFieldData source;
    private final Vector3d axisOrigin;
    private final Vector3d axisDirection;
    private final Vector3d bendNormal;
    private final Vector3d binormal;
    private final double angleRadians;
    private final double bendLength;
    private final ClampMode clampMode;

    public BentSdfData(SignedDistanceFieldData source,
                       Vector3d axisOrigin,
                       Vector3d axisDirection,
                       Vector3d bendNormal,
                       double bendDegrees,
                       double bendLength,
                       ClampMode clampMode) {
        if (source == null) {
            throw new IllegalArgumentException("Bent SDF requires a source field");
        }
        if (axisOrigin == null || !VectorUtils.isFinite(axisOrigin)) {
            throw new IllegalArgumentException("Bent SDF requires a finite axis origin");
        }
        Vector3d axis = VectorUtils.safeNormalize(axisDirection);
        Vector3d incomingNormal = VectorUtils.safeNormalize(bendNormal);
        if (axis == null) {
            throw new IllegalArgumentException("Bent SDF requires a non-zero finite axis direction");
        }
        if (incomingNormal == null) {
            throw new IllegalArgumentException("Bent SDF requires a non-zero finite bend normal");
        }
        if (!Double.isFinite(bendDegrees)) {
            throw new IllegalArgumentException("Bent SDF requires a finite bend angle");
        }
        if (!Double.isFinite(bendLength) || bendLength <= 0.0d) {
            throw new IllegalArgumentException("Bent SDF requires a positive bend length");
        }

        double nDotA = VectorUtils.safeDot(incomingNormal, axis);
        Vector3d projected = VectorUtils.safeSubtract(incomingNormal, VectorUtils.safeScale(axis, nDotA));
        projected = VectorUtils.safeNormalize(projected);
        if (projected == null) {
            throw new IllegalArgumentException("Bend normal must not be parallel to axis direction");
        }
        Vector3d binormal = VectorUtils.safeNormalize(VectorUtils.safeCross(axis, projected));
        if (binormal == null) {
            throw new IllegalArgumentException("Degenerate bend frame");
        }

        this.source = source;
        this.axisOrigin = new Vector3d(axisOrigin);
        this.axisDirection = axis;
        this.bendNormal = projected;
        this.binormal = binormal;
        this.angleRadians = Math.toRadians(bendDegrees);
        this.bendLength = bendLength;
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

    public Vector3d getBendNormal() {
        return new Vector3d(bendNormal);
    }

    public double getBendDegrees() {
        return Math.toDegrees(angleRadians);
    }

    public double getBendLength() {
        return bendLength;
    }

    public ClampMode getClampMode() {
        return clampMode;
    }

    @Override
    public double sampleDistance(Vector3d point) {
        return source.sampleDistance(unbendPoint(point));
    }

    public Vector3d bendPoint(Vector3d point) {
        return remapPoint(point, 1.0d);
    }

    public Vector3d unbendPoint(Vector3d point) {
        return remapPoint(point, -1.0d);
    }

    private Vector3d remapPoint(Vector3d point, double direction) {
        Vector3d offset = new Vector3d(point).sub(axisOrigin);
        double axialDistance = offset.dot(axisDirection);
        Vector3d axialComponent = new Vector3d(axisDirection).mul(axialDistance);
        Vector3d radialComponent = new Vector3d(offset).sub(axialComponent);

        double factor = applyClampMode(axialDistance / bendLength);
        double theta = angleRadians * factor * direction;
        if (Math.abs(angleRadians) <= EPS) {
            return new Vector3d(point);
        }

        double curvature = angleRadians / bendLength;
        double radius = 1.0d / curvature;
        Vector3d centerline = new Vector3d(axisOrigin)
            .add(new Vector3d(bendNormal).mul(radius * (1.0d - Math.cos(theta))))
            .add(new Vector3d(axisDirection).mul(radius * Math.sin(theta)));
        Vector3d rotatedRadial = rotateAroundAxis(radialComponent, binormal, theta);
        return centerline.add(rotatedRadial);
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
