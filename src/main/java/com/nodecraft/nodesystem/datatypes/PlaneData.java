package com.nodecraft.nodesystem.datatypes;

import org.joml.Vector3d;
import org.joml.Vector4d;
import org.joml.Math;
import java.util.Objects;
import net.minecraft.util.math.Vec3d;

/**
 * Plane with an explicit construction origin plus ax+by+cz+d=0 equation.
 * {@link #getPoint()} returns the construction origin (Minecraft-first profile/world plane workflows).
 */
public class PlaneData {
    private final Vector4d plane; // x,y,z = normal, w = plane constant
    private final Vector3d origin;

    public static final PlaneData XY_PLANE = new PlaneData(new Vector3d(0, 0, 0), new Vector3d(0, 0, 1));
    public static final PlaneData YZ_PLANE = new PlaneData(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0));
    public static final PlaneData XZ_PLANE = new PlaneData(new Vector3d(0, 0, 0), new Vector3d(0, 1, 0));

    public PlaneData(Vector3d origin, Vector3d normal) {
        Vector3d normalizedNormal = new Vector3d(normal).normalize();
        this.origin = new Vector3d(origin);
        this.plane = new Vector4d(
                normalizedNormal.x,
                normalizedNormal.y,
                normalizedNormal.z,
                -normalizedNormal.dot(this.origin));
    }

    public PlaneData(Vec3d origin, Vec3d normal) {
        this(new Vector3d(origin.x, origin.y, origin.z), new Vector3d(normal.x, normal.y, normal.z));
    }

    public PlaneData(Vector3d p1, Vector3d p2, Vector3d p3) {
        Vector3d v1 = new Vector3d();
        Vector3d v2 = new Vector3d();
        Vector3d normal = new Vector3d();

        p2.sub(p1, v1);
        p3.sub(p1, v2);
        v1.cross(v2, normal);
        normal.normalize();

        this.origin = new Vector3d(p1);
        this.plane = new Vector4d(normal, -normal.dot(p1));
    }

    public PlaneData(Vector4d plane) {
        this.plane = new Vector4d(plane);
        this.origin = reconstructAnyPoint(this.plane);
    }

    private static Vector3d reconstructAnyPoint(Vector4d plane) {
        if (Math.abs(plane.x) > 1e-6) {
            return new Vector3d(-plane.w / plane.x, 0, 0);
        }
        if (Math.abs(plane.y) > 1e-6) {
            return new Vector3d(0, -plane.w / plane.y, 0);
        }
        return new Vector3d(0, 0, -plane.w / plane.z);
    }

    public Vector4d getPlane() {
        return new Vector4d(plane);
    }

    public Vector3d getNormal() {
        return new Vector3d(plane.x, plane.y, plane.z);
    }

    public Vec3d normal() {
        return new Vec3d(plane.x, plane.y, plane.z);
    }

    /** Construction origin used when creating this plane (preferred profile/world-plane center). */
    public Vector3d getPoint() {
        return new Vector3d(origin);
    }

    public double distanceTo(Vector3d point) {
        return Math.abs(signedDistanceTo(point));
    }

    public double signedDistanceTo(Vector3d point) {
        return plane.x * point.x + plane.y * point.y + plane.z * point.z + plane.w;
    }

    public Vector3d projectPoint(Vector3d point) {
        double t = -(plane.x * point.x + plane.y * point.y + plane.z * point.z + plane.w)
                / (plane.x * plane.x + plane.y * plane.y + plane.z * plane.z);
        return new Vector3d(
                point.x + plane.x * t,
                point.y + plane.y * t,
                point.z + plane.z * t
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaneData planeData = (PlaneData) o;
        return Objects.equals(plane, planeData.plane) && Objects.equals(origin, planeData.origin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plane, origin);
    }

    @Override
    public String toString() {
        return "Plane[origin=(" + origin.x + ", " + origin.y + ", " + origin.z
                + "), normal=(" + plane.x + ", " + plane.y + ", " + plane.z + "), d=" + plane.w + "]";
    }
}
