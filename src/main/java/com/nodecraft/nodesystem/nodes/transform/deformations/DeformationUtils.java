package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Deformation-specific math helpers. Shared resolvers live in PointUtils / VectorUtils /
 * OptionalPortDrive / StrictIntegerUtils.
 */
final class DeformationUtils {

    private DeformationUtils() {
    }

    /**
     * Orthonormal bend frame: axis along bend distribution, normal in the bend plane
     * (perpendicular to axis), binormal = axis × normal.
     */
    record BendFrame(Vector3d axis, Vector3d normal, Vector3d binormal) {
    }

    /**
     * Projects {@code bendNormal} onto the plane perpendicular to {@code axisDirection}.
     * Returns null when axis/normal are unusable or parallel (no silent fallback).
     */
    static @Nullable BendFrame resolveBendFrame(
            @Nullable Vector3d axisDirection,
            @Nullable Vector3d bendNormal
    ) {
        if (!VectorUtils.isNonZero(axisDirection) || !VectorUtils.isNonZero(bendNormal)) {
            return null;
        }
        Vector3d axis = new Vector3d(axisDirection).normalize();
        Vector3d normal = new Vector3d(bendNormal);
        normal.sub(new Vector3d(axis).mul(normal.dot(axis)));
        if (!VectorUtils.isNonZero(normal)) {
            return null;
        }
        normal.normalize();
        Vector3d binormal = new Vector3d(axis).cross(normal);
        if (!VectorUtils.isNonZero(binormal)) {
            return null;
        }
        binormal.normalize();
        return new BendFrame(axis, normal, binormal);
    }

    static Vector3d rotateAroundAxis(Vector3d vector, Vector3d axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vector3d term1 = new Vector3d(vector).mul(cos);
        Vector3d term2 = new Vector3d(axis).cross(vector, new Vector3d()).mul(sin);
        Vector3d term3 = new Vector3d(axis).mul(axis.dot(vector) * (1.0d - cos));
        return term1.add(term2).add(term3);
    }
}
