package com.nodecraft.nodesystem.nodes.geometry.curves.util;

import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

public final class PlaneProjectionUtils {

    private PlaneProjectionUtils() {
    }

    public static @Nullable Vector3d resolvePoint(@Nullable Object value) {
        return SpatialValueResolver.resolveVector3d(value);
    }

    public static @Nullable Vec3d resolveVec3dPoint(@Nullable Object value) {
        if (value instanceof Vec3d vec3d) {
            return vec3d;
        }
        Vector3d resolved = resolvePoint(value);
        return resolved == null ? null : new Vec3d(resolved.x, resolved.y, resolved.z);
    }

    public static Vector3d resolvePointOrDefault(@Nullable Object value, @Nullable String fallbackCoords) {
        Vector3d resolved = resolvePoint(value);
        if (resolved != null) {
            return resolved;
        }
        Vector3d parsed = parseCsvVector3d(fallbackCoords);
        return parsed != null ? parsed : new Vector3d(0.0d, 0.0d, 0.0d);
    }

    public static @Nullable Vector3d resolveNormal(@Nullable Object planeValue,
                                                    @Nullable Object normalValue,
                                                    @Nullable String fallbackPlaneType) {
        if (planeValue instanceof PlaneData plane) {
            return plane.getNormal();
        }
        Vector3d resolved = resolvePoint(normalValue);
        if (resolved != null) {
            return resolved;
        }
        return planeFromType(fallbackPlaneType).getNormal();
    }

    public static PlaneData planeFromType(@Nullable String planeType) {
        return switch (planeType) {
            case "XY" -> PlaneData.XY_PLANE;
            case "YZ" -> PlaneData.YZ_PLANE;
            default -> PlaneData.XZ_PLANE;
        };
    }

    public static @Nullable Basis createBasisFromNormal(@Nullable Vector3d normalIn) {
        if (normalIn == null || normalIn.lengthSquared() <= 1.0e-12d) {
            return null;
        }
        Vector3d normal = new Vector3d(normalIn).normalize();
        Vector3d xAxis = fallbackAxis(normal);
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

    public static @Nullable Basis createBasis(PlaneData plane, @Nullable Vector3d preferredXAxis) {
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

    public static final class PlaneAxes {
        private final Vector3d origin;
        private final Vector3d axisU;
        private final Vector3d axisV;

        private PlaneAxes(Vector3d origin, Vector3d axisU, Vector3d axisV) {
            this.origin = origin;
            this.axisU = axisU;
            this.axisV = axisV;
        }

        public static PlaneAxes from(PlaneData plane) {
            Vector3d n = plane.getNormal();
            Vector3d axisU = new Vector3d(1, 0, 0);
            if (Math.abs(axisU.dot(n)) > 0.9d) {
                axisU.set(0, 1, 0);
            }
            Vector3d axisV = new Vector3d(n).cross(axisU, new Vector3d()).normalize();
            axisU = new Vector3d(axisV).cross(n, new Vector3d()).normalize();
            return new PlaneAxes(new Vector3d(plane.getPoint()), axisU, axisV);
        }

        public Vector2d to2d(Vector3d p) {
            Vector3d rel = new Vector3d(p).sub(origin);
            return new Vector2d(rel.dot(axisU), rel.dot(axisV));
        }

        public Vector3d from2d(Vector2d p) {
            return new Vector3d(origin)
                .add(new Vector3d(axisU).mul(p.x, new Vector3d()))
                .add(new Vector3d(axisV).mul(p.y, new Vector3d()));
        }
    }

    private static Vector3d fallbackAxis(Vector3d normal) {
        Vector3d reference = Math.abs(normal.z) < 0.99d
            ? new Vector3d(0.0d, 0.0d, 1.0d)
            : new Vector3d(0.0d, 1.0d, 0.0d);
        return reference.sub(new Vector3d(normal).mul(reference.dot(normal)));
    }

    private static @Nullable Vector3d parseCsvVector3d(@Nullable String coords) {
        if (coords == null) {
            return null;
        }
        String[] parts = coords.trim().split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            return new Vector3d(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record Basis(Vector3d xAxis, Vector3d yAxis, Vector3d normal) {
    }
}