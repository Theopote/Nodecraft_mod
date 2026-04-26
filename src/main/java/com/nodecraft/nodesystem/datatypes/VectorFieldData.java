package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

/**
 * Vector field defined over 3D space: F(p) -> vector.
 */
@FunctionalInterface
public interface VectorFieldData {
    void sampleVector(Vector3d point, Vector3d dest);
}
