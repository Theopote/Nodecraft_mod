package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.GeometryMirror;
import com.nodecraft.nodesystem.util.SdfFieldValidator;
import org.joml.Vector3d;

/**
 * Reflects an SDF about a plane via domain transform (isometry — no voxel fallback).
 */
public record MirroredSdfData(SignedDistanceFieldData source, PlaneData plane) implements SignedDistanceFieldData {
    public MirroredSdfData {
        SdfFieldValidator.requireValid(SdfFieldValidator.validateMirrored(source, plane));
    }

    @Override
    public double sampleDistance(Vector3d point) {
        return source.sampleDistance(GeometryMirror.mirrorPoint(point, plane));
    }
}
