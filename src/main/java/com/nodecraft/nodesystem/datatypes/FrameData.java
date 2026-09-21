package com.nodecraft.nodesystem.datatypes;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.Objects;

/**
 * Oriented frame: origin point plus orthonormal right-handed X/Y/Z axes.
 * Distinct from {@link PlaneData} (origin + normal only — no stable tangent).
 * <p>
 * FRAME is orientation only — not scale/shear. Prefer {@link #orthonormal} /
 * {@link #orthonormalized()} at construction boundaries; placement uses
 * {@link #toRotationMatrix()} so local {@code (1,0,0)/(0,1,0)/(0,0,1)} map to
 * frame X/Y/Z.
 */
public class FrameData {
    private static final double EPS = 1.0e-12d;

    private final Vector3d origin;
    private final Vector3d xAxis;
    private final Vector3d yAxis;
    private final Vector3d zAxis;

    public FrameData(Vector3d origin, Vector3d xAxis, Vector3d yAxis, Vector3d zAxis) {
        this.origin = new Vector3d(origin);
        this.xAxis = new Vector3d(xAxis);
        this.yAxis = new Vector3d(yAxis);
        this.zAxis = new Vector3d(zAxis);
    }

    public FrameData(PointData origin, Vector3d xAxis, Vector3d yAxis, Vector3d zAxis) {
        this(origin.getPosition(), xAxis, yAxis, zAxis);
    }

    /**
     * Builds an orthonormal right-handed frame ({@code Z = X × Y}) via Gram-Schmidt.
     * Returns {@code null} when axes are unusable / degenerate.
     */
    public static @Nullable FrameData orthonormal(Vector3d origin, Vector3d xIn, Vector3d yIn, Vector3d zHint) {
        if (origin == null || xIn == null || yIn == null) {
            return null;
        }
        if (!isFinite(origin) || !isFinite(xIn) || !isFinite(yIn)) {
            return null;
        }
        Vector3d x = new Vector3d(xIn);
        if (x.lengthSquared() <= EPS) {
            return null;
        }
        x.normalize();

        Vector3d y = new Vector3d(yIn).sub(new Vector3d(x).mul(x.dot(yIn)));
        if (y.lengthSquared() <= EPS) {
            Vector3d hint = zHint != null && isFinite(zHint) && zHint.lengthSquared() > EPS
                ? new Vector3d(zHint)
                : leastAlignedCardinal(x);
            y = new Vector3d(hint).cross(x);
            if (y.lengthSquared() <= EPS) {
                return null;
            }
        }
        y.normalize();

        Vector3d z = new Vector3d(x).cross(y);
        if (z.lengthSquared() <= EPS) {
            return null;
        }
        z.normalize();

        // Prefer matching the caller's Z handedness hint when it clearly flips the basis.
        if (zHint != null && isFinite(zHint) && zHint.lengthSquared() > EPS && z.dot(zHint) < 0.0d) {
            y.negate();
            z.negate();
        }

        return new FrameData(origin, x, y, z);
    }

    /** Returns an orthonormal right-handed copy of this frame, or {@code null} if degenerate. */
    public @Nullable FrameData orthonormalized() {
        return orthonormal(origin, xAxis, yAxis, zAxis);
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

    private static boolean isFinite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
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
