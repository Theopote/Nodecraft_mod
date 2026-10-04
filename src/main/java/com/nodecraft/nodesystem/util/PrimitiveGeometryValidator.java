package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BoxGeometryData;
import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import com.nodecraft.nodesystem.datatypes.SquarePyramidGeometryData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Shared construction/consumption invariants for continuous primitive geometry.
 */
public final class PrimitiveGeometryValidator {

    public static final double AXIS_EPS = 1.0e-9d;

    private PrimitiveGeometryValidator() {
    }

    public static boolean requireFinitePoint(@Nullable Vector3d point) {
        return FrameUtils.isFinite(point);
    }

    /**
     * Overflow-safe midpoint {@code a*0.5 + b*0.5}. Null when inputs or result are non-finite.
     */
    public static @Nullable Vector3d overflowSafeMidpoint(@Nullable Vector3d a, @Nullable Vector3d b) {
        Vector3d halfA = VectorUtils.safeScale(a, 0.5d);
        Vector3d halfB = VectorUtils.safeScale(b, 0.5d);
        return VectorUtils.safeAdd(halfA, halfB);
    }

    /**
     * {@code b - a} with overflow-safe length. Null when subtraction overflows, length is
     * non-finite, or length is at most {@link #AXIS_EPS}.
     */
    public static @Nullable Vector3d requirePositiveAxis(@Nullable Vector3d a, @Nullable Vector3d b) {
        Vector3d axis = VectorUtils.safeSubtract(b, a);
        double length = VectorUtils.safeLength(axis);
        if (axis == null || !Double.isFinite(length) || length <= AXIS_EPS) {
            return null;
        }
        return axis;
    }

    public static double requirePositiveAxisLength(@Nullable Vector3d a, @Nullable Vector3d b) {
        Vector3d axis = requirePositiveAxis(a, b);
        return axis == null ? Double.NaN : VectorUtils.safeLength(axis);
    }

    public static void requireValid(@Nullable String error) {
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
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
        if (!requireFinitePoint(start) || !requireFinitePoint(end)) {
            return "Cylinder requires finite axis endpoints";
        }
        if (requirePositiveAxis(start, end) == null) {
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
        if (!requireFinitePoint(baseCenter) || !requireFinitePoint(apex)) {
            return "Cone requires finite base center and apex";
        }
        if (requirePositiveAxis(baseCenter, apex) == null) {
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
        if (!requireFinitePoint(baseCenter) || !requireFinitePoint(topCenter)) {
            return "Frustum requires finite face centers";
        }
        if (requirePositiveAxis(baseCenter, topCenter) == null) {
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
        if (!requireFinitePoint(start) || !requireFinitePoint(end)) {
            return "Capsule requires finite axis endpoints";
        }
        if (requirePositiveAxis(start, end) == null) {
            return "Capsule axis length must be > 0";
        }
        if (!Double.isFinite(radius) || radius <= 0.0d) {
            return "Capsule radius must be finite and > 0";
        }
        return null;
    }

    public static @Nullable String validateHemisphere(
            @Nullable Vector3d center,
            @Nullable Vector3d axis,
            double radius
    ) {
        if (!requireFinitePoint(center)) {
            return "Hemisphere requires a finite center";
        }
        if (!FrameUtils.isUsableAxis(axis)) {
            return "Hemisphere requires a usable axis";
        }
        if (!Double.isFinite(radius) || radius <= 0.0d) {
            return "Hemisphere radius must be finite and > 0";
        }
        return null;
    }

    public static @Nullable String validateHemisphere(@Nullable HemisphereGeometryData hemisphere) {
        if (hemisphere == null) {
            return "Hemisphere is missing";
        }
        return validateHemisphere(hemisphere.center(), hemisphere.axis(), hemisphere.radius());
    }

    public static @Nullable String validateBox(@Nullable Vector3d center, @Nullable Vector3d halfExtents) {
        if (!requireFinitePoint(center)) {
            return "Box requires a finite center";
        }
        if (halfExtents == null || !VectorUtils.isFinite(halfExtents)
                || halfExtents.x <= 0.0d || halfExtents.y <= 0.0d || halfExtents.z <= 0.0d) {
            return "Box half-extents must be finite and > 0";
        }
        return null;
    }

    public static @Nullable String validateBox(@Nullable BoxGeometryData box) {
        if (box == null) {
            return "Box is missing";
        }
        return validateBox(box.getCenter(), box.getHalfExtents());
    }

    public static @Nullable String validateEllipsoid(@Nullable Vector3d center, @Nullable Vector3d radii) {
        if (!requireFinitePoint(center)) {
            return "Ellipsoid requires a finite center";
        }
        if (radii == null || !VectorUtils.isFinite(radii)
                || radii.x <= 0.0d || radii.y <= 0.0d || radii.z <= 0.0d) {
            return "Ellipsoid radii must be finite and > 0";
        }
        return null;
    }

    public static @Nullable String validateEllipsoid(@Nullable EllipsoidGeometryData ellipsoid) {
        if (ellipsoid == null) {
            return "Ellipsoid is missing";
        }
        return validateEllipsoid(ellipsoid.getCenter(), ellipsoid.getRadii());
    }

    public static @Nullable String validateSquarePyramid(
            @Nullable Vector3d baseCenter,
            @Nullable Vector3d xAxis,
            @Nullable Vector3d yAxis,
            @Nullable Vector3d normal,
            double baseSize,
            double height
    ) {
        if (!requireFinitePoint(baseCenter)) {
            return "Square pyramid requires a finite base center";
        }
        FrameData frame = FrameData.orthonormal(baseCenter, xAxis, yAxis, normal);
        if (frame == null || !frame.isCanonical()) {
            return "Square pyramid requires an orthonormal right-handed basis";
        }
        if (!Double.isFinite(baseSize) || baseSize <= 0.0d || !Double.isFinite(height) || height <= 0.0d) {
            return "Square pyramid base size and height must be finite and > 0";
        }
        return null;
    }

    public static @Nullable String validateSquarePyramid(@Nullable SquarePyramidGeometryData pyramid) {
        if (pyramid == null) {
            return "Square pyramid is missing";
        }
        return validateSquarePyramid(
            pyramid.getBaseCenter(),
            pyramid.getXAxis(),
            pyramid.getYAxis(),
            pyramid.getNormal(),
            pyramid.getBaseSize(),
            pyramid.getHeight()
        );
    }

    public static @Nullable String validatePolyhedron(@Nullable Vector3d center, double size, String sizeName) {
        if (!requireFinitePoint(center)) {
            return "Polyhedron requires a finite center";
        }
        String label = sizeName == null || sizeName.isBlank() ? "size" : sizeName;
        if (!Double.isFinite(size) || size <= 0.0d) {
            return "Polyhedron " + label + " must be finite and > 0";
        }
        return null;
    }
}
