package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PolygonProfileData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Connection-aware profile input resolution (Graph V73).
 */
public final class ProfileInputUtils {

    private ProfileInputUtils() {
    }

    public static @Nullable PlaneData resolveOptionalPlane(
            BaseNode node,
            String portId,
            @Nullable PlaneData propertyFallback
    ) {
        return OptionalPortDrive.resolveOptionalPlane(node, portId, propertyFallback);
    }

    public static @Nullable Vector3d resolveOptionalCenter(
            BaseNode node,
            String portId,
            @Nullable Vector3d planeOriginFallback
    ) {
        return OptionalPortDrive.resolveOptionalPoint(node, portId, planeOriginFallback);
    }

    /**
     * Optional in-plane axis for profile orientation. Connected invalid axis fails closed.
     */
    public static @Nullable Vector3d resolveOptionalInPlaneAxis(
            BaseNode node,
            String portId,
            PlaneData plane
    ) {
        if (!OptionalPortDrive.isConnected(node, portId)) {
            return null;
        }
        Vector3d axis = OptionalPortDrive.resolveOptionalVector(node, portId, null);
        if (axis == null || axis.lengthSquared() <= 1.0e-24d) {
            return null;
        }
        Vector3d normal = plane.getNormal();
        Vector3d projected = new Vector3d(axis).sub(new Vector3d(normal).mul(axis.dot(normal)));
        if (projected.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        return projected;
    }

    public static @Nullable Integer resolveOptionalBoundedExactInteger(
            BaseNode node,
            String portId,
            int propertyFallback,
            int minInclusive,
            int maxInclusive
    ) {
        return CurveInputUtils.resolveOptionalBoundedExactInteger(
            node, portId, propertyFallback, minInclusive, maxInclusive);
    }

    public static @Nullable Integer resolveOptionalExactPositiveInteger(
            BaseNode node,
            String portId,
            int propertyFallback
    ) {
        return CurveInputUtils.resolveOptionalExactPositiveInteger(node, portId, propertyFallback);
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

    public static @Nullable PolygonProfileData resolveStrictProfile(
            BaseNode node,
            String portId
    ) {
        Object value = node.getInput(portId);
        if (!(value instanceof PolygonProfileData profile)) {
            return null;
        }
        if (PolygonProfileValidator.validate(profile) != null) {
            return null;
        }
        return profile;
    }

    public static boolean isConnected(BaseNode node, String portId) {
        return OptionalPortDrive.isConnected(node, portId);
    }
}
