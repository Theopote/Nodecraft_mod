package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.BooleanSdfData;
import com.nodecraft.nodesystem.datatypes.BentSdfData;
import com.nodecraft.nodesystem.datatypes.BoxSdfData;
import com.nodecraft.nodesystem.datatypes.CapsuleSdfData;
import com.nodecraft.nodesystem.datatypes.DomainWarpedSdfData;
import com.nodecraft.nodesystem.datatypes.NoiseDisplacedSdfData;
import com.nodecraft.nodesystem.datatypes.SignedDistanceFieldData;
import com.nodecraft.nodesystem.datatypes.SphereSdfData;
import com.nodecraft.nodesystem.datatypes.TorusSdfData;
import com.nodecraft.nodesystem.datatypes.TransformedSdfData;
import com.nodecraft.nodesystem.datatypes.TwistedSdfData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Conservative axis-aligned bounds for SDF trees, used before voxel baking.
 */
public final class SdfBoundsEstimator {

    public record AxisAlignedBounds(Vector3d min, Vector3d max) {
        public boolean isValid() {
            return VectorUtils.isFinite(min)
                && VectorUtils.isFinite(max)
                && min.x <= max.x
                && min.y <= max.y
                && min.z <= max.z;
        }

        public @Nullable AxisAlignedBounds expanded(double padding) {
            if (!isValid() || !Double.isFinite(padding) || padding < 0.0d) {
                return null;
            }
            AxisAlignedBounds result = new AxisAlignedBounds(
                new Vector3d(min.x - padding, min.y - padding, min.z - padding),
                new Vector3d(max.x + padding, max.y + padding, max.z + padding)
            );
            return result.isValid() ? result : null;
        }

        public @Nullable AxisAlignedBounds union(@Nullable AxisAlignedBounds other) {
            if (other == null || !other.isValid()) {
                return isValid() ? this : null;
            }
            if (!isValid()) {
                return other;
            }
            AxisAlignedBounds result = new AxisAlignedBounds(
                new Vector3d(
                    Math.min(min.x, other.min.x),
                    Math.min(min.y, other.min.y),
                    Math.min(min.z, other.min.z)
                ),
                new Vector3d(
                    Math.max(max.x, other.max.x),
                    Math.max(max.y, other.max.y),
                    Math.max(max.z, other.max.z)
                )
            );
            return result.isValid() ? result : null;
        }

        public @Nullable AxisAlignedBounds intersect(@Nullable AxisAlignedBounds other) {
            if (!isValid() || other == null || !other.isValid()) {
                return null;
            }
            Vector3d mergedMin = new Vector3d(
                Math.max(min.x, other.min.x),
                Math.max(min.y, other.min.y),
                Math.max(min.z, other.min.z)
            );
            Vector3d mergedMax = new Vector3d(
                Math.min(max.x, other.max.x),
                Math.min(max.y, other.max.y),
                Math.min(max.z, other.max.z)
            );
            AxisAlignedBounds result = new AxisAlignedBounds(mergedMin, mergedMax);
            return result.isValid() ? result : null;
        }
    }

    private static final class WalkState {
        int nodes;
    }

    private SdfBoundsEstimator() {
    }

    public static @Nullable AxisAlignedBounds estimate(SignedDistanceFieldData sdf) {
        if (!SdfExpressionLimits.validate(sdf)) {
            return null;
        }
        return estimate(sdf, 1, new WalkState());
    }

    private static @Nullable AxisAlignedBounds estimate(
            SignedDistanceFieldData sdf,
            int depth,
            WalkState walk
    ) {
        if (sdf == null) {
            return null;
        }
        walk.nodes++;
        switch (sdf) {
            case SphereSdfData sphere -> {
                Vector3d center = sphere.center();
                double r = sphere.radius();
                return boxAround(center, r, r, r);
            }
            case BoxSdfData box -> {
                Vector3d center = box.getCenter();
                Vector3d half = box.getHalfExtents();
                return boxAround(center, half.x, half.y, half.z);
            }
            case CapsuleSdfData capsule -> {
                return boundsForCapsule(capsule);
            }
            case TorusSdfData torus -> {
                Vector3d center = torus.center();
                double outer = torus.majorRadius() + torus.minorRadius();
                return boxAround(center, outer, torus.minorRadius(), outer);
            }
            case BooleanSdfData booleanSdf -> {
                return boundsForBoolean(booleanSdf, depth, walk);
            }
            case TransformedSdfData transformed -> {
                return boundsForTransform(transformed, depth, walk);
            }
            case NoiseDisplacedSdfData noise -> {
                AxisAlignedBounds inner = estimate(noise.getSource(), depth + 1, walk);
                if (inner == null) {
                    return null;
                }
                return inner.expanded(noise.getAmplitude());
            }
            case DomainWarpedSdfData warp -> {
                AxisAlignedBounds inner = estimate(warp.getSource(), depth + 1, walk);
                if (inner == null) {
                    return null;
                }
                return inner.expanded(warp.getWarpAmplitude());
            }
            case TwistedSdfData twisted -> {
                return boundsForTwist(twisted, depth, walk);
            }
            case BentSdfData bent -> {
                return boundsForBend(bent, depth, walk);
            }
            default -> {
                return null;
            }
        }
    }

    private static @Nullable AxisAlignedBounds boundsForBoolean(
            BooleanSdfData booleanSdf,
            int depth,
            WalkState walk
    ) {
        AxisAlignedBounds left = estimate(booleanSdf.getLeft(), depth + 1, walk);
        AxisAlignedBounds right = estimate(booleanSdf.getRight(), depth + 1, walk);
        double blendPad = booleanSdf.getSmoothK();

        return switch (booleanSdf.getOperation()) {
            case UNION -> expandUnion(left, right, blendPad);
            case INTERSECTION -> {
                AxisAlignedBounds merged = left == null ? null : left.intersect(right);
                yield merged == null ? null : merged.expanded(blendPad);
            }
            // Hard/smooth difference cannot create solid outside the minuend (A).
            // Keep minuend-conservative bounds (expand by smoothK for soft blend only).
            case DIFFERENCE -> left == null ? null : left.expanded(blendPad);
        };
    }

    private static @Nullable AxisAlignedBounds expandUnion(
        @Nullable AxisAlignedBounds left,
        @Nullable AxisAlignedBounds right,
        double blendPad
    ) {
        AxisAlignedBounds merged = left == null ? right : left.union(right);
        return merged == null ? null : merged.expanded(blendPad);
    }

    private static @Nullable AxisAlignedBounds boundsForTransform(
            TransformedSdfData transformed,
            int depth,
            WalkState walk
    ) {
        AxisAlignedBounds inner = estimate(transformed.getSource(), depth + 1, walk);
        if (inner == null || !inner.isValid()) {
            return null;
        }

        Vector3d center = VectorUtils.safeAdd(
            VectorUtils.safeScale(inner.min, 0.5d),
            VectorUtils.safeScale(inner.max, 0.5d)
        );
        Vector3d extent = VectorUtils.safeSubtract(inner.max, inner.min);
        Vector3d half = VectorUtils.safeScale(extent, 0.5d);
        half = VectorUtils.safeScale(half, transformed.getScale());
        center = VectorUtils.safeAdd(center, transformed.getTranslation());
        if (center == null || half == null) {
            return null;
        }

        double rotationPad = VectorUtils.safeLength(half) * transformed.getRotationPaddingFactor();
        if (!Double.isFinite(rotationPad)) {
            return null;
        }
        Vector3d padded = VectorUtils.safeAdd(half, new Vector3d(rotationPad, rotationPad, rotationPad));
        if (padded == null) {
            return null;
        }

        AxisAlignedBounds result = new AxisAlignedBounds(
            new Vector3d(center.x - padded.x, center.y - padded.y, center.z - padded.z),
            new Vector3d(center.x + padded.x, center.y + padded.y, center.z + padded.z)
        );
        return result.isValid() ? result : null;
    }

    private static @Nullable AxisAlignedBounds boundsForTwist(TwistedSdfData twisted, int depth, WalkState walk) {
        AxisAlignedBounds inner = estimate(twisted.getSource(), depth + 1, walk);
        if (inner == null || !inner.isValid()) {
            return null;
        }

        int samples = 5;
        AxisAlignedBounds bounds = null;
        for (int ix = 0; ix < samples; ix++) {
            double x = lerp(inner.min.x, inner.max.x, ix / (double) (samples - 1));
            for (int iy = 0; iy < samples; iy++) {
                double y = lerp(inner.min.y, inner.max.y, iy / (double) (samples - 1));
                for (int iz = 0; iz < samples; iz++) {
                    double z = lerp(inner.min.z, inner.max.z, iz / (double) (samples - 1));
                    Vector3d point = twisted.twistPoint(new Vector3d(x, y, z));
                    AxisAlignedBounds pointBounds = new AxisAlignedBounds(new Vector3d(point), new Vector3d(point));
                    bounds = bounds == null ? pointBounds : bounds.union(pointBounds);
                }
            }
        }
        return bounds == null ? null : bounds.expanded(2.0d);
    }

    private static @Nullable AxisAlignedBounds boundsForBend(BentSdfData bent, int depth, WalkState walk) {
        AxisAlignedBounds inner = estimate(bent.getSource(), depth + 1, walk);
        if (inner == null || !inner.isValid()) {
            return null;
        }

        int samples = 5;
        AxisAlignedBounds bounds = null;
        for (int ix = 0; ix < samples; ix++) {
            double x = lerp(inner.min.x, inner.max.x, ix / (double) (samples - 1));
            for (int iy = 0; iy < samples; iy++) {
                double y = lerp(inner.min.y, inner.max.y, iy / (double) (samples - 1));
                for (int iz = 0; iz < samples; iz++) {
                    double z = lerp(inner.min.z, inner.max.z, iz / (double) (samples - 1));
                    Vector3d point = bent.bendPoint(new Vector3d(x, y, z));
                    AxisAlignedBounds pointBounds = new AxisAlignedBounds(new Vector3d(point), new Vector3d(point));
                    bounds = bounds == null ? pointBounds : bounds.union(pointBounds);
                }
            }
        }
        return bounds == null ? null : bounds.expanded(2.0d);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static @Nullable AxisAlignedBounds boundsForCapsule(CapsuleSdfData capsule) {
        Vector3d a = capsule.getEndpointA();
        Vector3d b = capsule.getEndpointB();
        double r = capsule.getRadius();
        if (!Double.isFinite(r)) {
            return null;
        }
        Vector3d min = new Vector3d(
            Math.min(a.x, b.x) - r,
            Math.min(a.y, b.y) - r,
            Math.min(a.z, b.z) - r
        );
        Vector3d max = new Vector3d(
            Math.max(a.x, b.x) + r,
            Math.max(a.y, b.y) + r,
            Math.max(a.z, b.z) + r
        );
        AxisAlignedBounds result = new AxisAlignedBounds(min, max);
        return result.isValid() ? result : null;
    }

    private static @Nullable AxisAlignedBounds boxAround(Vector3d center, double halfX, double halfY, double halfZ) {
        if (!VectorUtils.isFinite(center)
            || !Double.isFinite(halfX) || !Double.isFinite(halfY) || !Double.isFinite(halfZ)) {
            return null;
        }
        AxisAlignedBounds result = new AxisAlignedBounds(
            new Vector3d(center.x - halfX, center.y - halfY, center.z - halfZ),
            new Vector3d(center.x + halfX, center.y + halfY, center.z + halfZ)
        );
        return result.isValid() ? result : null;
    }
}
