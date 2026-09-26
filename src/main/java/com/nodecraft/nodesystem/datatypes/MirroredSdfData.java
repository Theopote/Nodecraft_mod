package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.GeometryMirror;
import org.joml.Vector3d;

/**
 * Reflects an SDF about a plane via domain transform (isometry — no voxel fallback).
 */
public record MirroredSdfData(SignedDistanceFieldData source, PlaneData plane) implements SignedDistanceFieldData {

    @Override
    public double sampleDistance(Vector3d point) {
        return source.sampleDistance(GeometryMirror.mirrorPoint(point, plane));
    }
}
