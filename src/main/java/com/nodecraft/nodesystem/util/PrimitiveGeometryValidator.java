package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Shared construction/consumption invariants for continuous primitive geometry (Graph V74).
 */
public final class PrimitiveGeometryValidator {

    public static final double AXIS_EPS = 1.0e-9d;

    private PrimitiveGeometryValidator() {
    }

    /**
     * @return null when valid; otherwise an actionable error message
     */
    public static @Nullable String validateSphere(@Nullable Vector3d center, double radius) {
        if (!FrameUtils.isFinite(center)) {
            return "Sphere requires a finite center";
        }
        if (!Double.isFinite(radius) || radius <= 0.0d) {
            return "Sphere radius must be finite and > 0";
        }
        return null;
    }

    public static @Nullable String validateSphere(@Nullable SphereData sphere) {
        if (sphere == null) {
            return "Sphere is missing";
        }
        return validateSphere(sphere.center(), sphere.radius());
    }

    public static @Nullable String validateCylinder(
            @Nullable Vector3d start,
            @Nullable Vector3d end,
            double radius
    ) {
        if (!FrameUtils.isFinite(start) || !FrameUtils.isFinite(end)) {
            return "Cylinder requires finite axis endpoints";
        }
        if (new Vector3d(end).sub(start).length() <= AXIS_EPS) {
            return "Cylinder axis length must be > 0";
        }
        if (!Double.isFinite(radius) || radius <= 0.0d) {
            return "Cylinder radius must be finite and > 0";
        }
        return null;
    }

    public static @Nullable String validateCylinder(@Nullable CylinderGeometryData cylinder) {
        if (cylinder == null) {
            return "Cylinder is missing";
        }
        return validateCylinder(cylinder.getStart(), cylinder.getEnd(), cylinder.getRadius());
    }

    public static @Nullable String validateCone(
            @Nullable Vector3d baseCenter,
            @Nullable Vector3d apex,
            double radius
    ) {
        if (!FrameUtils.isFinite(baseCenter) || !FrameUtils.isFinite(apex)) {
            return "Cone requires finite base center and apex";
        }
        if (new Vector3d(apex).sub(baseCenter).length() <= AXIS_EPS) {
            return "Cone height must be > 0";
        }
        if (!Double.isFinite(radius) || radius <= 0.0d) {
            return "Cone radius must be finite and > 0";
        }
        return null;
    }

    public static @Nullable String validateCone(@Nullable ConeGeometryData cone) {
        if (cone == null) {
            return "Cone is missing";
        }
        return validateCone(cone.getBaseCenter(), cone.getApex(), cone.getBaseRadius());
    }

    public static @Nullable String validateFrustum(
            @Nullable Vector3d baseCenter,
            @Nullable Vector3d topCenter,
            double baseRadius,
            double topRadius
    ) {
        if (!FrameUtils.isFinite(baseCenter) || !FrameUtils.isFinite(topCenter)) {
            return "Frustum requires finite face centers";
        }
        if (new Vector3d(topCenter).sub(baseCenter).length() <= AXIS_EPS) {
            return "Frustum height must be > 0";
        }
        if (!Double.isFinite(baseRadius) || !Double.isFinite(topRadius)
                || baseRadius < 0.0d || topRadius < 0.0d) {
            return "Frustum radii must be finite and >= 0";
        }
        if (Math.max(baseRadius, topRadius) <= AXIS_EPS) {
            return "Frustum requires at least one positive radius";
        }
        return null;
    }

    public static @Nullable String validateFrustum(@Nullable FrustumConeGeometryData frustum) {
        if (frustum == null) {
            return "Frustum is missing";
        }
        return validateFrustum(
            frustum.getBaseCenter(), frustum.getTopCenter(), frustum.getBaseRadius(), frustum.getTopRadius());
    }

    /**
     * Ordinary ring torus only: {@code 0 < minorRadius < majorRadius}.
     */
    public static @Nullable String validateRingTorus(
            @Nullable Vector3d center,
            @Nullable Vector3d axis,
            double majorRadius,
            double minorRadius
    ) {
        if (!FrameUtils.isFinite(center)) {
            return "Torus requires a finite center";
        }
        if (!FrameUtils.isUsableAxis(axis)) {
            return "Torus requires a usable symmetry axis";
        }
        if (!Double.isFinite(majorRadius) || majorRadius <= 0.0d) {
            return "Torus major radius must be finite and > 0";
        }
        if (!Double.isFinite(minorRadius) || minorRadius <= 0.0d) {
            return "Torus minor radius must be finite and > 0";
        }
        if (!(minorRadius < majorRadius)) {
            return "Torus requires 0 < minor radius < major radius (ring torus)";
        }
        return null;
    }

    public static @Nullable String validateRingTorus(@Nullable TorusGeometryData torus) {
        if (torus == null) {
            return "Torus is missing";
        }
        return validateRingTorus(torus.center(), torus.axis(), torus.majorRadius(), torus.minorRadius());
    }

    public static @Nullable String validateCapsule(
            @Nullable Vector3d start,
            @Nullable Vector3d end,
            double radius
    ) {
        if (!FrameUtils.isFinite(start) || !FrameUtils.isFinite(end)) {
            return "Capsule requires finite axis endpoints";
        }
        if (new Vector3d(end).sub(start).length() <= AXIS_EPS) {
            return "Capsule axis length must be > 0";
        }
        if (!Double.isFinite(radius) || radius <= 0.0d) {
            return "Capsule radius must be finite and > 0";
        }
        return null;
    }
}
