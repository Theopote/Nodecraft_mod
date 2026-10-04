package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.SdfFieldValidator;
import org.joml.Vector3d;

/**
 * Wraps an SDF with explicit sampling bounds so it can flow through GeometryData pipelines.
 */
public record SdfGeometryData(SignedDistanceFieldData sdf, Vector3d min, Vector3d max,
                              double isoValue) implements GeometryData {
    public SdfGeometryData(SignedDistanceFieldData sdf, Vector3d min, Vector3d max, double isoValue) {
        SdfFieldValidator.requireValid(SdfFieldValidator.validateSdfGeometry(sdf, min, max, isoValue));
        this.sdf = sdf;
        this.min = new Vector3d(min);
        this.max = new Vector3d(max);
        this.isoValue = isoValue;
    }

    @Override
    public Vector3d min() {
        return new Vector3d(min);
    }

    @Override
    public Vector3d max() {
        return new Vector3d(max);
    }
}
