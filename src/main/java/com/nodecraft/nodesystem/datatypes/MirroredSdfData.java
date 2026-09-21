package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.GeometryMirror;
import org.joml.Vector3d;

/**
 * Reflects an SDF about a plane via domain transform (isometry — no voxel fallback).
 */
public class MirroredSdfData implements SignedDistanceFieldData {
    private final SignedDistanceFieldData source;
    private final PlaneData plane;

    public MirroredSdfData(SignedDistanceFieldData source, PlaneData plane) {
        this.source = source;
        this.plane = plane;
    }

    public SignedDistanceFieldData getSource() {
        return source;
    }

    public PlaneData getPlane() {
        return plane;
    }

    @Override
    public double sampleDistance(Vector3d point) {
        return source.sampleDistance(GeometryMirror.mirrorPoint(point, plane));
    }
}
