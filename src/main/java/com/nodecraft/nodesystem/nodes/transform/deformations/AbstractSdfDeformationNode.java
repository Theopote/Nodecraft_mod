package com.nodecraft.nodesystem.nodes.transform.deformations;

import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.util.OptionalPortDrive;
import com.nodecraft.nodesystem.util.PointUtils;
import com.nodecraft.nodesystem.util.SdfBoundsEstimator;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Shared SDF source resolution and bounds pairing for Twist/Bend SDF nodes.
 */
abstract class AbstractSdfDeformationNode extends AbstractDeformationNode {

    protected static final String INPUT_SDF_ID = "input_sdf";
    protected static final String INPUT_BOUNDS_MIN_ID = "input_bounds_min";
    protected static final String INPUT_BOUNDS_MAX_ID = "input_bounds_max";
    protected static final String OUTPUT_SDF_ID = "output_sdf";
    protected static final String OUTPUT_BOUNDS_MIN_ID = "output_bounds_min";
    protected static final String OUTPUT_BOUNDS_MAX_ID = "output_bounds_max";

    protected AbstractSdfDeformationNode(String typeName) {
        super(typeName);
    }

    protected final void failSdfOutputs(String error) {
        markInvalid(error);
        putNullOutputs(OUTPUT_SDF_ID, OUTPUT_BOUNDS_MIN_ID, OUTPUT_BOUNDS_MAX_ID);
    }

    /**
     * Resolves required SDF input and optional explicit bounds (paired Min/Max).
     */
    protected @Nullable SdfSource resolveSdfSource(double boundsPadding) {
        boolean sdfConnected = OptionalPortDrive.isConnected(this, INPUT_SDF_ID);
        Object sdfObj = inputValues.get(INPUT_SDF_ID);
        if (sdfConnected) {
            if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
                return null;
            }
            return buildSdfSource(sdf, boundsPadding);
        }
        if (sdfObj == null) {
            return null;
        }
        if (!(sdfObj instanceof SignedDistanceFieldData sdf)) {
            return null;
        }
        return buildSdfSource(sdf, boundsPadding);
    }

    private @Nullable SdfSource buildSdfSource(SignedDistanceFieldData sdf, double boundsPadding) {
        BoundsResolution boundsResolution = resolveInputBounds();
        if (boundsResolution.status == BoundsStatus.INVALID) {
            return null;
        }
        AxisAlignedBounds bounds = boundsResolution.bounds;
        if (boundsResolution.status == BoundsStatus.ABSENT) {
            SdfBoundsEstimator.AxisAlignedBounds estimated = SdfBoundsEstimator.estimate(sdf);
            if (estimated == null || !estimated.isValid()) {
                return null;
            }
            bounds = AxisAlignedBounds.from(estimated.min(), estimated.max()).expanded(boundsPadding);
            if (bounds == null || !bounds.isValid()) {
                return null;
            }
        }
        return new SdfSource(sdf, bounds.min, bounds.max);
    }

    /**
     * Bounds Min/Max are a paired optional input:
     * both unconnected → ABSENT (auto-estimate allowed);
     * both connected and valid → VALID;
     * half-connected or invalid → INVALID (fail closed, never estimate).
     */
    private BoundsResolution resolveInputBounds() {
        boolean minConnected = OptionalPortDrive.isConnected(this, INPUT_BOUNDS_MIN_ID);
        boolean maxConnected = OptionalPortDrive.isConnected(this, INPUT_BOUNDS_MAX_ID);
        if (!minConnected && !maxConnected) {
            return BoundsResolution.absent();
        }
        if (minConnected != maxConnected) {
            return BoundsResolution.invalid();
        }
        Vector3d min = OptionalPortDrive.resolveOptionalPoint(this, INPUT_BOUNDS_MIN_ID, null);
        Vector3d max = OptionalPortDrive.resolveOptionalPoint(this, INPUT_BOUNDS_MAX_ID, null);
        if (!PointUtils.isFinite(min) || !PointUtils.isFinite(max)
                || min.x > max.x || min.y > max.y || min.z > max.z) {
            return BoundsResolution.invalid();
        }
        AxisAlignedBounds bounds = AxisAlignedBounds.from(min, max);
        if (!bounds.isValid()) {
            return BoundsResolution.invalid();
        }
        return BoundsResolution.valid(bounds);
    }

    protected enum BoundsStatus {
        ABSENT,
        VALID,
        INVALID
    }

    record SdfSource(SignedDistanceFieldData sdf, Vector3d min, Vector3d max) {
        SdfSource {
            min = new Vector3d(min);
            max = new Vector3d(max);
        }
    }

    record AxisAlignedBounds(Vector3d min, Vector3d max) {
        static AxisAlignedBounds from(Vector3d min, Vector3d max) {
            return new AxisAlignedBounds(min, max);
        }

        AxisAlignedBounds {
            min = new Vector3d(min);
            max = new Vector3d(max);
        }

        boolean isValid() {
            return VectorUtils.isFinite(min)
                && VectorUtils.isFinite(max)
                && min.x <= max.x
                && min.y <= max.y
                && min.z <= max.z;
        }

        AxisAlignedBounds include(Vector3d point) {
            return new AxisAlignedBounds(
                new Vector3d(Math.min(min.x, point.x), Math.min(min.y, point.y), Math.min(min.z, point.z)),
                new Vector3d(Math.max(max.x, point.x), Math.max(max.y, point.y), Math.max(max.z, point.z))
            );
        }

        AxisAlignedBounds expanded(double padding) {
            if (!Double.isFinite(padding)) {
                return new AxisAlignedBounds(
                    new Vector3d(Double.NaN, Double.NaN, Double.NaN),
                    new Vector3d(Double.NaN, Double.NaN, Double.NaN)
                );
            }
            return new AxisAlignedBounds(
                new Vector3d(min).sub(padding, padding, padding),
                new Vector3d(max).add(padding, padding, padding)
            );
        }
    }

    private record BoundsResolution(BoundsStatus status, @Nullable AxisAlignedBounds bounds) {
        static BoundsResolution absent() {
            return new BoundsResolution(BoundsStatus.ABSENT, null);
        }

        static BoundsResolution invalid() {
            return new BoundsResolution(BoundsStatus.INVALID, null);
        }

        static BoundsResolution valid(AxisAlignedBounds bounds) {
            return new BoundsResolution(BoundsStatus.VALID, bounds);
        }
    }
}
