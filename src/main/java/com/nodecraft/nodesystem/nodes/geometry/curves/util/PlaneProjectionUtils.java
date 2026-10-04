package com.nodecraft.nodesystem.nodes.geometry.curves.util;

import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.util.FrameUtils;
import com.nodecraft.nodesystem.util.SpatialValueResolver;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

public final class PlaneProjectionUtils {

    public enum DefaultPlane {
        XZ,
        XY,
        YZ
    }

    private PlaneProjectionUtils() {
    }

    public static @Nullable Vector3d resolvePoint(@Nullable Object value) {
        return SpatialValueResolver.resolvePoint(value);
    }

    public static @Nullable Vector3d resolveVector(@Nullable Object value) {
        return SpatialValueResolver.resolveVector(value);
    }

    public static @Nullable Vec3d resolveVec3dPoint(@Nullable Object value) {
        if (value instanceof Vec3d vec3d) {
            return vec3d;
        }
        Vector3d resolved = resolvePoint(value);
        return resolved == null ? null : new Vec3d(resolved.x, resolved.y, resolved.z);
    }

    public static Vector3d resolvePointOrDefault(@Nullable Object value,
                                                 double fallbackX,
                                                 double fallbackY,
                                                 double fallbackZ) {
        Vector3d resolved = resolvePoint(value);
        return resolved != null ? resolved : new Vector3d(fallbackX, fallbackY, fallbackZ);
    }

    public static @Nullable Vector3d resolveNormal(@Nullable Object planeValue,
                                                    @Nullable Object normalValue,
                                                    DefaultPlane fallbackPlane) {
        if (planeValue instanceof PlaneData plane) {
            return plane.getNormal();
        }
        Vector3d resolved = resolveVector(normalValue);
        if (resolved != null) {
            return resolved;
        }
        return planeFromPreset(fallbackPlane).getNormal();
    }

    public static PlaneData planeFromPreset(DefaultPlane plane) {
        return switch (plane) {
            case XY -> PlaneData.XY_PLANE;
            case YZ -> PlaneData.YZ_PLANE;
            default -> PlaneData.XZ_PLANE;
        };
    }

    public static @Nullable Basis createBasisFromNormal(@Nullable Vector3d normalIn) {
        FrameData frame = FrameUtils.fromNormal(new Vector3d(), normalIn, null);
        if (frame == null) {
            return null;
        }
        return new Basis(frame.getXAxis(), frame.getYAxis(), frame.getZAxis());
    }

    public static @Nullable Basis createBasis(PlaneData plane, @Nullable Vector3d preferredXAxis) {
        if (plane == null) {
            return null;
        }
        FrameData frame = FrameUtils.fromNormal(plane.getPoint(), plane.getNormal(), preferredXAxis);
        if (frame == null) {
            return null;
        }
        return new Basis(frame.getXAxis(), frame.getYAxis(), frame.getZAxis());
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

    /**
     * Plane UV relative to a local origin (typically the first operand vertex)
     * so far-from-origin profiles do not inflate JTS / shoelace coordinates.
     */
    public static final class PlaneProjectionContext {
        private final PlaneAxes axes;
        private final Vector2d localOriginUv;

        private PlaneProjectionContext(PlaneAxes axes, Vector2d localOriginUv) {
            this.axes = axes;
            this.localOriginUv = localOriginUv;
        }

        public static PlaneProjectionContext from(PlaneData plane, @Nullable Vector3d worldAnchor) {
            PlaneAxes axes = PlaneAxes.from(plane);
            Vector3d anchor = worldAnchor != null ? worldAnchor : new Vector3d(plane.getPoint());
            return new PlaneProjectionContext(axes, axes.to2d(anchor));
        }

        public Vector2d toLocal(Vector3d world) {
            Vector2d uv = axes.to2d(world);
            return new Vector2d(uv.x - localOriginUv.x, uv.y - localOriginUv.y);
        }

        public Vector3d fromLocal(Vector2d local) {
            return axes.from2d(new Vector2d(local.x + localOriginUv.x, local.y + localOriginUv.y));
        }
    }

    public record Basis(Vector3d xAxis, Vector3d yAxis, Vector3d normal) {
    }
}
