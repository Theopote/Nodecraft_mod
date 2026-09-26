package com.nodecraft.nodesystem.nodes.transform.deformations;

import org.joml.Vector3d;

/**
 * Deformation-specific math helpers. Shared resolvers live in PointUtils / VectorUtils /
 * OptionalPortDrive / StrictIntegerUtils.
 */
final class DeformationUtils {

    private DeformationUtils() {
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
