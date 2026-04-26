package com.nodecraft.nodesystem.util;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Validates and composes rotation matrices for Platonic solid geometry (local frame → world).
 */
public final class PolyhedronOrientationUtil {

    private static final double EPS = 1.0e-9d;

    private PolyhedronOrientationUtil() {
    }

    /**
     * Orthonormalizes {@code m} via quaternion projection; degenerate input yields identity.
     */
    public static Matrix3d copyValidatedRotation(@Nullable Matrix3d m) {
        if (m == null) {
            return new Matrix3d().identity();
        }
        Quaterniond q = new Quaterniond().setFromUnnormalized(m);
        Matrix3d out = new Matrix3d().set(q);
        if (!Double.isFinite(out.determinant()) || Math.abs(out.determinant()) < EPS) {
            return new Matrix3d().identity();
        }
        return out;
    }

    public static Matrix3d rotationFromEulerDegrees(double rxDeg, double ryDeg, double rzDeg) {
        return new Matrix3d().rotateXYZ(
            Math.toRadians(rxDeg),
            Math.toRadians(ryDeg),
            Math.toRadians(rzDeg)
        );
    }

    /**
     * Uses port value when it is a {@link Matrix3d}; otherwise Euler XYZ in degrees (same convention as {@link GeometryTransform.Spec}).
     */
    public static Matrix3d resolveFromPortOrEuler(@Nullable Object orientationPortValue,
                                                  double eulerXDeg,
                                                  double eulerYDeg,
                                                  double eulerZDeg) {
        if (orientationPortValue instanceof Matrix3d m) {
            return copyValidatedRotation(m);
        }
        return rotationFromEulerDegrees(eulerXDeg, eulerYDeg, eulerZDeg);
    }

    public static int hashMatrix3(Matrix3d m) {
        int h = 1;
        h = 31 * h + Double.hashCode(m.m00);
        h = 31 * h + Double.hashCode(m.m01);
        h = 31 * h + Double.hashCode(m.m02);
        h = 31 * h + Double.hashCode(m.m10);
        h = 31 * h + Double.hashCode(m.m11);
        h = 31 * h + Double.hashCode(m.m12);
        h = 31 * h + Double.hashCode(m.m20);
        h = 31 * h + Double.hashCode(m.m21);
        h = 31 * h + Double.hashCode(m.m22);
        return h;
    }

    public static boolean rotationEquals(Matrix3d a, Matrix3d b) {
        return a != null && b != null && a.equals(b, 1.0e-9d);
    }

    /** Applies {@code r} to each vertex (copies; does not mutate {@code local}). */
    public static Vector3d[] transformLocalVertices(Matrix3d r, Vector3d[] local) {
        Vector3d[] out = new Vector3d[local.length];
        for (int i = 0; i < local.length; i++) {
            Vector3d v = new Vector3d(local[i]);
            r.transform(v);
            out[i] = v;
        }
        return out;
    }
}
