package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/**
 * Canonical constructors for SDF value types: reject invalid, never silent-clamp.
 */
public final class SdfFieldValidator {

    private static final double ROTATION_EPS = 1.0e-3d;

    private SdfFieldValidator() {
    }

    public static void requireValid(@Nullable String error) {
        PrimitiveGeometryValidator.requireValid(error);
    }

    public static @Nullable String requireSource(@Nullable SignedDistanceFieldData source) {
        return source == null ? "SDF requires a source field" : null;
    }

    public static @Nullable String validateTorusSdf(@Nullable Vector3d center, double majorRadius, double minorRadius) {
        return PrimitiveGeometryValidator.validateRingTorus(
            center, new Vector3d(0.0d, 1.0d, 0.0d), majorRadius, minorRadius);
    }

    public static @Nullable String validateBoolean(
            @Nullable SignedDistanceFieldData left,
            @Nullable SignedDistanceFieldData right,
            @Nullable Object operation,
            double smoothK
    ) {
        if (left == null || right == null) {
            return "Boolean SDF requires both operands";
        }
        if (operation == null) {
            return "Boolean SDF requires an operation";
        }
        if (!Double.isFinite(smoothK) || smoothK < 0.0d) {
            return "Boolean smooth K must be finite and >= 0";
        }
        return null;
    }

    public static @Nullable String validateDisplacement(
            @Nullable SignedDistanceFieldData source,
            double amplitude,
            double frequency,
            @Nullable Vector3d offset
    ) {
        String sourceError = requireSource(source);
        if (sourceError != null) {
            return sourceError;
        }
        if (!Double.isFinite(amplitude) || amplitude < 0.0d) {
            return "SDF displacement amplitude must be finite and >= 0";
        }
        if (!Double.isFinite(frequency) || frequency <= 0.0d) {
            return "SDF displacement frequency must be finite and > 0";
        }
        if (!VectorUtils.isFinite(offset)) {
            return "SDF displacement offset must be finite";
        }
        return null;
    }

    public static @Nullable String validateTransformed(
            @Nullable SignedDistanceFieldData source,
            @Nullable Vector3d translation,
            @Nullable Matrix3d rotation,
            double scale,
            double rotationXDeg,
            double rotationYDeg,
            double rotationZDeg
    ) {
        String sourceError = requireSource(source);
        if (sourceError != null) {
            return sourceError;
        }
        if (!VectorUtils.isFinite(translation)) {
            return "Transformed SDF requires a finite translation";
        }
        if (!Double.isFinite(rotationXDeg) || !Double.isFinite(rotationYDeg) || !Double.isFinite(rotationZDeg)) {
            return "Transformed SDF requires finite Euler angles";
        }
        if (!Double.isFinite(scale) || scale <= VectorUtils.EPS) {
            return "Scale must be greater than zero";
        }
        Matrix3d resolved = rotation == null ? new Matrix3d().identity() : rotation;
        if (!isFiniteOrthonormalRotation(resolved)) {
            return "Transformed SDF rotation must be a finite orthonormal matrix";
        }
        return null;
    }

    public static @Nullable String validateSdfGeometry(
            @Nullable SignedDistanceFieldData sdf,
            @Nullable Vector3d min,
            @Nullable Vector3d max,
            double isoValue
    ) {
        String sourceError = requireSource(sdf);
        if (sourceError != null) {
            return sourceError;
        }
        if (!VectorUtils.isFinite(min) || !VectorUtils.isFinite(max)) {
            return "SDF geometry bounds must be finite";
        }
        if (min.x > max.x || min.y > max.y || min.z > max.z) {
            return "SDF geometry min must be <= max";
        }
        if (!Double.isFinite(isoValue)) {
            return "SDF iso value must be finite";
        }
        return null;
    }

    public static @Nullable String validateMirrored(
            @Nullable SignedDistanceFieldData source,
            @Nullable PlaneData plane
    ) {
        String sourceError = requireSource(source);
        if (sourceError != null) {
            return sourceError;
        }
        if (plane == null) {
            return "Mirrored SDF requires a plane";
        }
        return null;
    }

    /**
     * True when {@code m} is a finite right-handed orthonormal rotation (transpose == inverse).
     */
    public static boolean isFiniteOrthonormalRotation(@Nullable Matrix3d m) {
        if (m == null) {
            return false;
        }
        Vector3d c0 = m.getColumn(0, new Vector3d());
        Vector3d c1 = m.getColumn(1, new Vector3d());
        Vector3d c2 = m.getColumn(2, new Vector3d());
        if (!VectorUtils.isFinite(c0) || !VectorUtils.isFinite(c1) || !VectorUtils.isFinite(c2)) {
            return false;
        }
        double l0 = VectorUtils.safeLength(c0);
        double l1 = VectorUtils.safeLength(c1);
        double l2 = VectorUtils.safeLength(c2);
        if (!nearOne(l0) || !nearOne(l1) || !nearOne(l2)) {
            return false;
        }
        if (!nearZero(VectorUtils.safeDot(c0, c1))
            || !nearZero(VectorUtils.safeDot(c0, c2))
            || !nearZero(VectorUtils.safeDot(c1, c2))) {
            return false;
        }
        double det = m.determinant();
        return Double.isFinite(det) && Math.abs(det - 1.0d) <= ROTATION_EPS;
    }

    private static boolean nearOne(double value) {
        return Double.isFinite(value) && Math.abs(value - 1.0d) <= ROTATION_EPS;
    }

    private static boolean nearZero(double value) {
        return Double.isFinite(value) && Math.abs(value) <= ROTATION_EPS;
    }
}
