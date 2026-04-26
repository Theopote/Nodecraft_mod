package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

/**
 * Scalar field defined over 3D space: f(p) -> scalar.
 */
@FunctionalInterface
public interface ScalarFieldData {
    double sampleScalar(Vector3d point);
}
