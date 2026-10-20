package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.LineData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

    /**
     * Drive-aware primitive input resolution.
     * <p>
     * POINT ports never accept {@link LineData}. Driven-invalid inputs fail closed
     * (return null) instead of washing out to property defaults. Driven =
     * wired or explicitly injected via {@code setInput}/{@code compute}.
     */
    public final class PrimitiveInputUtils {
    
        private PrimitiveInputUtils() {
        }
    
        public static boolean isConnected(BaseNode node, String portId) {
            return OptionalPortDrive.isConnected(node, portId);
        }
    
        public static boolean isDriven(BaseNode node, String portId) {
            return OptionalPortDrive.isDriven(node, portId);
        }
    
        /**
         * Optional POINT. Rejects {@link LineData} even if present on the wire.
         */
        public static @Nullable Vector3d resolveOptionalPoint(
                BaseNode node,
                String portId,
                @Nullable Vector3d propertyFallback
        ) {
            if (isDriven(node, portId)) {
                Object value = node.getInput(portId);
                if (value instanceof LineData) {
                    return null;
                }
                Vector3d resolved = SpatialValueResolver.resolvePoint(value);
                return FrameUtils.isFinite(resolved) ? resolved : null;
            }
            return propertyFallback == null ? null : new Vector3d(propertyFallback);
        }

    public static @Nullable Vector3d resolveOptionalVector(
            BaseNode node,
            String portId,
            @Nullable Vector3d propertyFallback
    ) {
        return OptionalPortDrive.resolveOptionalVector(node, portId, propertyFallback);
    }

    public static @Nullable Vector3d resolveOptionalUsableAxis(
            BaseNode node,
            String portId,
            @Nullable Vector3d propertyFallback
    ) {
        Vector3d axis = resolveOptionalVector(node, portId, propertyFallback);
        if (axis == null || !FrameUtils.isUsableAxis(axis)) {
            return null;
        }
        return axis;
    }

    public static @Nullable PlaneData resolveOptionalPlane(
            BaseNode node,
            String portId,
            @Nullable PlaneData propertyFallback
    ) {
        return OptionalPortDrive.resolveOptionalPlane(node, portId, propertyFallback);
    }

    public static @Nullable Double resolveOptionalFiniteDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        return CurveInputUtils.resolveOptionalFiniteDouble(node, portId, propertyFallback);
    }

    public static @Nullable Double resolveOptionalPositiveFiniteDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        return CurveInputUtils.resolveOptionalPositiveFiniteDouble(node, portId, propertyFallback);
    }

    public static @Nullable Double resolveOptionalNonNegativeFiniteDouble(
            BaseNode node,
            String portId,
            double propertyFallback
    ) {
        return CurveInputUtils.resolveOptionalNonNegativeFiniteDouble(node, portId, propertyFallback);
    }

    /**
     * Optional MATRIX3. Undriven → null (caller may apply Euler fallback).
     * Driven non-{@link Matrix3d} or non-finite → null (fail closed).
     */
    public static @Nullable Matrix3d resolveOptionalMatrix3(BaseNode node, String portId) {
        if (!isDriven(node, portId)) {
            return null;
        }
        Object value = node.getInput(portId);
        if (!(value instanceof Matrix3d matrix)) {
            return null;
        }
        if (!isFiniteMatrix(matrix)) {
            return null;
        }
        return new Matrix3d(matrix);
    }

    /**
     * Orientation: undriven → Euler degrees; driven invalid MATRIX3 → null.
     */
    public static @Nullable Matrix3d resolveOrientationOrEuler(
            BaseNode node,
            String orientationPortId,
            double eulerXDeg,
            double eulerYDeg,
            double eulerZDeg
    ) {
        if (!isDriven(node, orientationPortId)) {
            if (!Double.isFinite(eulerXDeg) || !Double.isFinite(eulerYDeg) || !Double.isFinite(eulerZDeg)) {
                return null;
            }
            return PolyhedronOrientationUtil.rotationFromEulerDegrees(eulerXDeg, eulerYDeg, eulerZDeg);
        }
        Matrix3d connected = resolveOptionalMatrix3(node, orientationPortId);
        if (connected == null) {
            return null;
        }
        Matrix3d validated = PolyhedronOrientationUtil.copyValidatedRotation(connected);
        if (!isFiniteMatrix(validated) || Math.abs(validated.determinant()) < 1.0e-9d) {
            return null;
        }
        return validated;
    }

    private static boolean isFiniteMatrix(Matrix3d m) {
        return Double.isFinite(m.m00) && Double.isFinite(m.m01) && Double.isFinite(m.m02)
            && Double.isFinite(m.m10) && Double.isFinite(m.m11) && Double.isFinite(m.m12)
            && Double.isFinite(m.m20) && Double.isFinite(m.m21) && Double.isFinite(m.m22);
    }
}
