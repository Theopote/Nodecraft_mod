package com.nodecraft.nodesystem.datatypes;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Objects;

/**
 * Continuous axis-aligned bounding box in world space.
 * <p>
 * Membership is the closed interval {@code [min, max]} on each axis.
 * Block-cell membership is outside this type (see Region / BLOCK_POS).
 */
public class BoundingBoxData {
    private final Vector3d min;
    private final Vector3d max;

    /**
     * Fail-closed factory: returns {@code null} when min/max are null, non-finite,
     * or inverted (any component of min &gt; max).
     */
    public static @Nullable BoundingBoxData create(@Nullable Vector3d min, @Nullable Vector3d max) {
        if (!isValidPair(min, max)) {
            return null;
        }
        return new BoundingBoxData(min, max);
    }

    /**
     * Constructor from min/max vectors.
     *
     * @throws IllegalArgumentException when the pair is null, non-finite, or inverted
     */
    public BoundingBoxData(Vector3d min, Vector3d max) {
        if (!isValidPair(min, max)) {
            throw new IllegalArgumentException("BoundingBox requires finite min<=max componentwise");
        }
        this.min = new Vector3d(min);
        this.max = new Vector3d(max);
    }

    public BoundingBoxData(BoundingBoxData other) {
        this.min = new Vector3d(other.min);
        this.max = new Vector3d(other.max);
    }

    public static boolean isValidPair(@Nullable Vector3d min, @Nullable Vector3d max) {
        if (min == null || max == null) {
            return false;
        }
        return isFinite(min) && isFinite(max)
            && min.x <= max.x
            && min.y <= max.y
            && min.z <= max.z;
    }

    public static boolean isFinite(Vector3d v) {
        return v != null
            && Double.isFinite(v.x)
            && Double.isFinite(v.y)
            && Double.isFinite(v.z);
    }

    public boolean isValid() {
        return isValidPair(min, max);
    }

    public boolean isFinite() {
        return isFinite(min) && isFinite(max);
    }

    public Vector3d getMin() {
        return new Vector3d(min);
    }

    public Vector3d getMax() {
        return new Vector3d(max);
    }

    /**
     * Midpoint of the continuous AABB.
     * <p>
     * Uses {@code min*0.5 + max*0.5} to reduce overflow vs {@code (min+max)*0.5}.
     * Derived values may still be non-finite for extreme finite corners — use
     * {@link #finiteCenter()} at graph-facing boundaries.
     */
    public Vector3d center() {
        return new Vector3d(
            min.x * 0.5d + max.x * 0.5d,
            min.y * 0.5d + max.y * 0.5d,
            min.z * 0.5d + max.z * 0.5d
        );
    }

    /** Axis extents {@code max - min} (zero-thickness allowed; may be non-finite). */
    public Vector3d size() {
        return new Vector3d(max.x - min.x, max.y - min.y, max.z - min.z);
    }

    /** Product of axis extents (may be 0 or non-finite). */
    public double volume() {
        Vector3d s = size();
        return s.x * s.y * s.z;
    }

    /**
     * Graph-facing midpoint: {@code null} when any component is non-finite.
     */
    public @Nullable Vector3d finiteCenter() {
        Vector3d c = center();
        return isFinite(c) ? c : null;
    }

    /**
     * Graph-facing size: {@code null} when any axis extent is non-finite.
     */
    public @Nullable Vector3d finiteSize() {
        Vector3d s = size();
        return isFinite(s) ? s : null;
    }

    /**
     * Graph-facing volume: {@code null} when size or product is non-finite.
     */
    public @Nullable Double finiteVolume() {
        Vector3d s = finiteSize();
        if (s == null) {
            return null;
        }
        double v = s.x * s.y * s.z;
        return Double.isFinite(v) ? v : null;
    }

    /**
     * Axis-aligned union. Returns {@code null} if either argument is null/invalid.
     */
    public static @Nullable BoundingBoxData union(@Nullable BoundingBoxData a, @Nullable BoundingBoxData b) {
        if (a == null || !a.isValid()) {
            return b != null && b.isValid() ? new BoundingBoxData(b) : null;
        }
        if (b == null || !b.isValid()) {
            return new BoundingBoxData(a);
        }
        return create(
            new Vector3d(
                Math.min(a.min.x, b.min.x),
                Math.min(a.min.y, b.min.y),
                Math.min(a.min.z, b.min.z)
            ),
            new Vector3d(
                Math.max(a.max.x, b.max.x),
                Math.max(a.max.y, b.max.y),
                Math.max(a.max.z, b.max.z)
            )
        );
    }

    /**
     * Axis-aligned intersection. Returns {@code null} on null/invalid input or non-overlap.
     */
    public static @Nullable BoundingBoxData intersection(@Nullable BoundingBoxData a, @Nullable BoundingBoxData b) {
        if (a == null || b == null || !a.isValid() || !b.isValid()) {
            return null;
        }
        return create(
            new Vector3d(
                Math.max(a.min.x, b.min.x),
                Math.max(a.min.y, b.min.y),
                Math.max(a.min.z, b.min.z)
            ),
            new Vector3d(
                Math.min(a.max.x, b.max.x),
                Math.min(a.max.y, b.max.y),
                Math.min(a.max.z, b.max.z)
            )
        );
    }

    /**
     * Closed-interval membership: point is inside when {@code min <= p <= max} on each axis.
     */
    public boolean testPoint(double x, double y, double z) {
        return x >= min.x && x <= max.x &&
               y >= min.y && y <= max.y &&
               z >= min.z && z <= max.z;
    }

    public boolean testPoint(Vector3d point) {
        return testPoint(point.x, point.y, point.z);
    }

    @Override
    public String toString() {
        return "BoundingBox[min=" + min + ", max=" + max + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoundingBoxData that = (BoundingBoxData) o;
        return Objects.equals(this.min, that.min) && Objects.equals(this.max, that.max);
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }
}
