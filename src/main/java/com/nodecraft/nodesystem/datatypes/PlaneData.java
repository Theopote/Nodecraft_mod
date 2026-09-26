package com.nodecraft.nodesystem.datatypes;

import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.PlaneUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector4d;

import java.util.Objects;
import net.minecraft.util.math.Vec3d;

/**
 * Plane with an explicit construction origin plus ax+by+cz+d=0 equation.
 * {@link #getPoint()} returns the construction origin (Minecraft-first profile/world plane workflows).
 * <p>
 * Canonical invariant: finite origin + unit non-zero normal (|normal| = 1).
 */
public class PlaneData {
    private static final double EPS = PlaneUtils.EPS;

    private final Vector4d plane; // x,y,z = normal, w = plane constant
    private final Vector3d origin;

    public static final PlaneData XY_PLANE = requireCanonical(new Vector3d(0, 0, 0), new Vector3d(0, 0, 1));
    public static final PlaneData YZ_PLANE = requireCanonical(new Vector3d(0, 0, 0), new Vector3d(1, 0, 0));
    public static final PlaneData XZ_PLANE = requireCanonical(new Vector3d(0, 0, 0), new Vector3d(0, 1, 0));

    private PlaneData(Vector3d origin, Vector4d plane) {
        this.origin = origin;
        this.plane = plane;
    }

    /**
     * Builds a canonical plane from a finite origin and usable normal.
     * Returns {@code null} when origin or normal is invalid.
     */
    public static @Nullable PlaneData canonical(@Nullable Vector3d origin, @Nullable Vector3d normal) {
        if (!FrameUtils.isFinite(origin) || !PlaneUtils.isUsableNormal(normal)) {
            return null;
        }
        Vector3d normalizedNormal = new Vector3d(normal).normalize();
        Vector3d canonicalOrigin = new Vector3d(origin);
        return new PlaneData(
                canonicalOrigin,
                new Vector4d(
                        normalizedNormal.x,
                        normalizedNormal.y,
                        normalizedNormal.z,
                        -normalizedNormal.dot(canonicalOrigin))
        );
    }

    /**
     * Builds a canonical plane from a plane equation, normalizing (a,b,c,d) by |normal|.
     */
    public static @Nullable PlaneData fromEquation(Vector4d equation) {
        if (equation == null) {
            return null;
        }
        double nx = equation.x;
        double ny = equation.y;
        double nz = equation.z;
        double normalLengthSq = nx * nx + ny * ny + nz * nz;
        if (!Double.isFinite(normalLengthSq) || normalLengthSq <= EPS * EPS) {
            return null;
        }
        double invLength = 1.0d / Math.sqrt(normalLengthSq);
        Vector4d normalized = new Vector4d(
                nx * invLength,
                ny * invLength,
                nz * invLength,
                equation.w * invLength
        );
        Vector3d reconstructedOrigin = reconstructAnyPoint(normalized);
        if (!FrameUtils.isFinite(reconstructedOrigin)) {
            return null;
        }
        return new PlaneData(
                reconstructedOrigin,
                new Vector4d(normalized)
        );
    }

    /** Returns a canonical copy of this plane, or {@code null} if degenerate. */
    public @Nullable PlaneData normalized() {
        return canonical(origin, getNormal());
    }

    public PlaneData(Vector3d origin, Vector3d normal) {
        PlaneData canonical = canonical(origin, normal);
        if (canonical == null) {
            throw new IllegalArgumentException("invalid plane origin or normal");
        }
        this.origin = canonical.origin;
        this.plane = canonical.plane;
    }

    public PlaneData(Vec3d origin, Vec3d normal) {
        this(new Vector3d(origin.x, origin.y, origin.z), new Vector3d(normal.x, normal.y, normal.z));
    }

    public PlaneData(Vector3d p1, Vector3d p2, Vector3d p3) {
        PlaneData fromPoints = PlaneUtils.fromThreePoints(p1, p2, p3);
        if (fromPoints == null) {
            throw new IllegalArgumentException("three points do not define a valid plane");
        }
        this.origin = fromPoints.origin;
        this.plane = fromPoints.plane;
    }

    public PlaneData(Vector4d plane) {
        PlaneData fromEquation = fromEquation(plane);
        if (fromEquation == null) {
            throw new IllegalArgumentException("invalid plane equation");
        }
        this.origin = fromEquation.origin;
        this.plane = fromEquation.plane;
    }

    private static PlaneData requireCanonical(Vector3d origin, Vector3d normal) {
        PlaneData canonical = canonical(origin, normal);
        if (canonical == null) {
            throw new ExceptionInInitializerError("invalid plane constant");
        }
        return canonical;
    }

    private static Vector3d reconstructAnyPoint(Vector4d plane) {
        if (Math.abs(plane.x) > EPS) {
            return new Vector3d(-plane.w / plane.x, 0, 0);
        }
        if (Math.abs(plane.y) > EPS) {
            return new Vector3d(0, -plane.w / plane.y, 0);
        }
        if (Math.abs(plane.z) > EPS) {
            return new Vector3d(0, 0, -plane.w / plane.z);
        }
        return new Vector3d(Double.NaN, Double.NaN, Double.NaN);
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
