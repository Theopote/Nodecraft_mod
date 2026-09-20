package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

final class ProfilePlaneUtils {
    private ProfilePlaneUtils() {
    }

    /** Minecraft-first default: horizontal ground plane. */
    static final PlaneData DEFAULT_PLANE = PlaneData.XZ_PLANE;

    static @Nullable Vector3d resolvePoint(@Nullable Object value) {
        return SpatialValueResolver.resolveVector3d(value);
    }

    /**
     * Center override if connected; otherwise plane origin; otherwise world origin.
     */
    static Vector3d resolveCenter(@Nullable Object centerValue, PlaneData plane) {
        Vector3d fromPort = resolvePoint(centerValue);
        if (fromPort != null) {
            return fromPort;
        }
        if (plane != null) {
            Vector3d origin = plane.getPoint();
            if (origin != null) {
                return new Vector3d(origin);
            }
        }
        return new Vector3d(0.0d, 0.0d, 0.0d);
    }

    static PlaneData resolvePlane(@Nullable Object planeValue) {
        return planeValue instanceof PlaneData plane ? plane : DEFAULT_PLANE;
    }

    static List<PointData> toPointList(List<Vector3d> points) {
        List<PointData> out = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            out.add(new PointData(point));
        }
        return List.copyOf(out);
    }

    static @Nullable Basis createBasis(PlaneData plane, @Nullable Vector3d preferredXAxis) {
        Vector3d normal = plane.getNormal();
        if (normal.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        normal.normalize();

        Vector3d xAxis = preferredXAxis != null ? new Vector3d(preferredXAxis) : null;
        if (xAxis != null) {
            xAxis.sub(new Vector3d(normal).mul(xAxis.dot(normal)));
        }
        if (xAxis == null || xAxis.lengthSquared() <= 1.0e-12d) {
            xAxis = fallbackAxis(normal);
        }
        if (xAxis.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        xAxis.normalize();

        Vector3d yAxis = new Vector3d(normal).cross(xAxis);
        if (yAxis.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        yAxis.normalize();
        xAxis = new Vector3d(yAxis).cross(normal).normalize();

        return new Basis(xAxis, yAxis, normal);
    }

    static PolylineData toPolyline(List<Vector3d> points) {
        List<Vec3d> vecPoints = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            vecPoints.add(new Vec3d(point.x, point.y, point.z));
        }
        return new PolylineData(vecPoints);
    }

    private static Vector3d fallbackAxis(Vector3d normal) {
        Vector3d reference = Math.abs(normal.z) < 0.99d
            ? new Vector3d(0.0d, 0.0d, 1.0d)
            : new Vector3d(0.0d, 1.0d, 0.0d);
        return reference.sub(new Vector3d(normal).mul(reference.dot(normal)));
    }

    record Basis(Vector3d xAxis, Vector3d yAxis, Vector3d normal) {
    }
}
