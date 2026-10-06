package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.SpatialTolerance;
import com.nodecraft.nodesystem.util.VectorUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.Objects;

/**
 * Oriented frame: origin point plus orthonormal right-handed X/Y/Z axes.
 * Distinct from {@link PlaneData} (origin + normal only — no stable tangent).
 * <p>
 * FRAME is orientation only — not scale/shear. Public construction is
 * {@link #orthonormal} / {@link #canonical} only. Placement uses
 * {@link #toRotationMatrix()} so local {@code (1,0,0)/(0,1,0)/(0,0,1)} map to
 * frame X/Y/Z.
 */
public class FrameData {
    private static final double EPS = SpatialTolerance.EPS;

    private final Vector3d origin;
    private final Vector3d xAxis;
    private final Vector3d yAxis;
    private final Vector3d zAxis;

    FrameData(Vector3d origin, Vector3d xAxis, Vector3d yAxis, Vector3d zAxis) {
        this.origin = new Vector3d(origin);
        this.xAxis = new Vector3d(xAxis);
        this.yAxis = new Vector3d(yAxis);
        this.zAxis = new Vector3d(zAxis);
    }

    /**
     * Returns a frame only when origin/axes already pass {@link #isCanonical()}.
     */
    public static @Nullable FrameData canonical(
            @Nullable Vector3d origin,
            @Nullable Vector3d xAxis,
            @Nullable Vector3d yAxis,
            @Nullable Vector3d zAxis
    ) {
        if (origin == null || xAxis == null || yAxis == null || zAxis == null) {
            return null;
        }
        FrameData frame = new FrameData(origin, xAxis, yAxis, zAxis);
        return frame.isCanonical() ? frame : null;
    }

    /**
     * Builds an orthonormal right-handed frame ({@code Z = X × Y}) via Gram-Schmidt.
     * Returns {@code null} when axes are unusable / degenerate.
     */
    public static @Nullable FrameData orthonormal(Vector3d origin, Vector3d xIn, Vector3d yIn, Vector3d zHint) {
        if (origin == null || xIn == null || yIn == null) {
            return null;
        }
        if (!VectorUtils.isFinite(origin)) {
            return null;
        }
        Vector3d x = VectorUtils.safeNormalize(xIn);
        if (x == null) {
            return null;
        }

        double yDotX = VectorUtils.safeDot(yIn, x);
        Vector3d y = null;
        if (Double.isFinite(yDotX)) {
            y = VectorUtils.safeNormalize(VectorUtils.safeSubtract(yIn, VectorUtils.safeScale(x, yDotX)));
        }
        if (y == null) {
            Vector3d hint = VectorUtils.isNonZero(zHint) ? new Vector3d(zHint) : leastAlignedCardinal(x);
            y = VectorUtils.safeNormalize(VectorUtils.safeCross(hint, x));
            if (y == null) {
                return null;
            }
        }

        Vector3d z = VectorUtils.safeNormalize(VectorUtils.safeCross(x, y));
        if (z == null) {
            return null;
        }

        // Prefer matching the caller's Z handedness hint when it clearly flips the basis.
        if (VectorUtils.isNonZero(zHint)) {
            double alignment = VectorUtils.safeDot(z, zHint);
            if (Double.isFinite(alignment) && alignment < 0.0d) {
                y.negate();
                z.negate();
            }
        }

        return new FrameData(origin, x, y, z);
    }

    /** Returns an orthonormal right-handed copy of this frame, or {@code null} if degenerate. */
    public @Nullable FrameData orthonormalized() {
        return orthonormal(origin, xAxis, yAxis, zAxis);
    }

    /**
     * True when origin is finite and axes are finite unit, pairwise orthogonal, and right-handed.
     */
    public boolean isCanonical() {
        if (!VectorUtils.isFinite(origin)) {
            return false;
        }
        if (!isUnit(xAxis) || !isUnit(yAxis) || !isUnit(zAxis)) {
            return false;
        }
        if (!isOrthogonal(xAxis, yAxis) || !isOrthogonal(yAxis, zAxis) || !isOrthogonal(zAxis, xAxis)) {
            return false;
        }
        Vector3d yCrossZ = VectorUtils.safeCross(yAxis, zAxis);
        double triple = VectorUtils.safeDot(xAxis, yCrossZ);
        return Double.isFinite(triple) && Math.abs(triple - 1.0d) <= EPS;
    }

    /**
     * Rotation whose columns are frame X/Y/Z:
     * {@code R * (1,0,0) = X}, {@code R * (0,1,0) = Y}, {@code R * (0,0,1) = Z}.
     */
    public Matrix3d toRotationMatrix() {
        Matrix3d rotation = new Matrix3d();
        rotation.setColumn(0, xAxis);
        rotation.setColumn(1, yAxis);
        rotation.setColumn(2, zAxis);
        return rotation;
    }

    public Vector3d getOrigin() {
        return new Vector3d(origin);
    }

    public PointData getOriginPoint() {
        return new PointData(origin);
    }

    public Vector3d getXAxis() {
        return new Vector3d(xAxis);
    }

    public Vector3d getYAxis() {
        return new Vector3d(yAxis);
    }

    public Vector3d getZAxis() {
        return new Vector3d(zAxis);
    }

    /** Plane through the origin with normal = Z axis (surface / face frame convention). */
    public PlaneData toPlane() {
        return new PlaneData(origin, zAxis);
    }

    private static Vector3d leastAlignedCardinal(Vector3d axis) {
        double ax = Math.abs(axis.x);
        double ay = Math.abs(axis.y);
        double az = Math.abs(axis.z);
        if (ax <= ay && ax <= az) {
            return new Vector3d(1.0d, 0.0d, 0.0d);
        }
        if (ay <= az) {
            return new Vector3d(0.0d, 1.0d, 0.0d);
        }
        return new Vector3d(0.0d, 0.0d, 1.0d);
    }

    private static boolean isUnit(Vector3d axis) {
        double length = VectorUtils.safeLength(axis);
        return Double.isFinite(length) && Math.abs(length - 1.0d) <= EPS;
    }

    private static boolean isOrthogonal(Vector3d a, Vector3d b) {
        double dot = VectorUtils.safeDot(a, b);
        return Double.isFinite(dot) && Math.abs(dot) <= EPS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FrameData that)) return false;
        return Objects.equals(origin, that.origin)
            && Objects.equals(xAxis, that.xAxis)
            && Objects.equals(yAxis, that.yAxis)
            && Objects.equals(zAxis, that.zAxis);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, xAxis, yAxis, zAxis);
    }

    @Override
    public String toString() {
        return "FrameData{origin=" + origin + ", x=" + xAxis + ", y=" + yAxis + ", z=" + zAxis + "}";
    }
}
