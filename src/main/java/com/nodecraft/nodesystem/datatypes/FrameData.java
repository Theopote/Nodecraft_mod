package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;

import java.util.Objects;

/**
 * Oriented frame: origin point plus orthonormal (or usable) X/Y/Z axes.
 * Distinct from {@link PlaneData} (origin + normal only — no stable tangent).
 */
public class FrameData {
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
