package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

/**
 * Signed Distance Field abstraction.
 * Negative distance means inside, positive means outside, zero is the surface.
 */
public interface SignedDistanceFieldData {
    double sampleDistance(Vector3d point);
}
