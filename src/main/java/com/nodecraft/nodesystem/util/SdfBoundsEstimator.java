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
            return min.x <= max.x && min.y <= max.y && min.z <= max.z;
        }

        public AxisAlignedBounds expanded(double padding) {
            double pad = Math.max(0.0d, padding);
            return new AxisAlignedBounds(
                new Vector3d(min.x - pad, min.y - pad, min.z - pad),
                new Vector3d(max.x + pad, max.y + pad, max.z + pad)
            );
        }

        public @Nullable AxisAlignedBounds union(@Nullable AxisAlignedBounds other) {
            if (other == null || !other.isValid()) {
                return this;
            }
            if (!isValid()) {
                return other;
            }
            return new AxisAlignedBounds(
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
        }

        public @Nullable AxisAlignedBounds intersect(@Nullable AxisAlignedBounds other) {
            if (other == null || !other.isValid()) {
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

    private SdfBoundsEstimator() {
    }

    public static @Nullable AxisAlignedBounds estimate(SignedDistanceFieldData sdf) {
        switch (sdf) {
            case null -> {
                return null;
            }
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
                return boundsForBoolean(booleanSdf);
            }
            case TransformedSdfData transformed -> {
                return boundsForTransform(transformed);
            }
            case NoiseDisplacedSdfData noise -> {
                AxisAlignedBounds inner = estimate(noise.getSource());
                if (inner == null) {
                    return null;
                }
                return inner.expanded(noise.getAmplitude());
            }
            case DomainWarpedSdfData warp -> {
                AxisAlignedBounds inner = estimate(warp.getSource());
                if (inner == null) {
                    return null;
                }
                return inner.expanded(warp.getWarpAmplitude());
            }
            case TwistedSdfData twisted -> {
                return boundsForTwist(twisted);
            }
            case BentSdfData bent -> {
                return boundsForBend(bent);
            }
            default -> {
            }
        }

        return null;
    }

    private static @Nullable AxisAlignedBounds boundsForBoolean(BooleanSdfData booleanSdf) {
        AxisAlignedBounds left = estimate(booleanSdf.getLeft());
        AxisAlignedBounds right = estimate(booleanSdf.getRight());
        double blendPad = booleanSdf.getSmoothK();

        return switch (booleanSdf.getOperation()) {
            case UNION -> expandUnion(left, right, blendPad);
            case INTERSECTION -> {
                AxisAlignedBounds merged = left == null ? null : left.intersect(right);
                yield merged == null ? null : merged.expanded(blendPad);
            }
            case DIFFERENCE -> expandUnion(left, right, blendPad);
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

    private static @Nullable AxisAlignedBounds boundsForTransform(TransformedSdfData transformed) {
        AxisAlignedBounds inner = estimate(transformed.getSource());
        if (inner == null) {
            return null;
        }

        Vector3d center = new Vector3d(inner.min).add(inner.max).mul(0.5d);
        Vector3d half = new Vector3d(inner.max).sub(inner.min).mul(0.5d);

        double scale = transformed.getScale();
        half.mul(scale);

        Vector3d translation = transformed.getTranslation();
        center.add(translation);

        double rotationPad = half.length() * transformed.getRotationPaddingFactor();
        half.add(rotationPad, rotationPad, rotationPad);

        return new AxisAlignedBounds(
            new Vector3d(center.x - half.x, center.y - half.y, center.z - half.z),
            new Vector3d(center.x + half.x, center.y + half.y, center.z + half.z)
        );
    }

    private static @Nullable AxisAlignedBounds boundsForTwist(TwistedSdfData twisted) {
        AxisAlignedBounds inner = estimate(twisted.getSource());
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

    private static @Nullable AxisAlignedBounds boundsForBend(BentSdfData bent) {
        AxisAlignedBounds inner = estimate(bent.getSource());
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

    private static AxisAlignedBounds boundsForCapsule(CapsuleSdfData capsule) {
        Vector3d a = capsule.getEndpointA();
        Vector3d b = capsule.getEndpointB();
        double r = capsule.getRadius();
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
        return new AxisAlignedBounds(min, max);
    }

    private static AxisAlignedBounds boxAround(Vector3d center, double halfX, double halfY, double halfZ) {
        return new AxisAlignedBounds(
            new Vector3d(center.x - halfX, center.y - halfY, center.z - halfZ),
            new Vector3d(center.x + halfX, center.y + halfY, center.z + halfZ)
        );
    }
}
