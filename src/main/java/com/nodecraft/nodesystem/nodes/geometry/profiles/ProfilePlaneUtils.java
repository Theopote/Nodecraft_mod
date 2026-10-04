package com.nodecraft.nodesystem.nodes.geometry.profiles;

import com.nodecraft.nodesystem.datatypes.FrameData;
import com.nodecraft.nodesystem.datatypes.PlaneData;
import com.nodecraft.nodesystem.datatypes.PointData;
import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.util.FrameUtils;
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

    static List<PointData> toPointList(List<Vector3d> points) {
        List<PointData> out = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            out.add(new PointData(point));
        }
        return List.copyOf(out);
    }

    static @Nullable Basis createBasis(PlaneData plane, @Nullable Vector3d preferredXAxis) {
        FrameData frame = preferredXAxis == null
            ? FrameUtils.fromPlane(plane, null)
            : FrameUtils.fromPlaneRequireHint(plane, preferredXAxis);
        if (frame == null) {
            return null;
        }
        return new Basis(frame.getXAxis(), frame.getYAxis(), frame.getZAxis());
    }

    static PolylineData toPolyline(List<Vector3d> points) {
        List<Vec3d> vecPoints = new ArrayList<>(points.size());
        for (Vector3d point : points) {
            vecPoints.add(new Vec3d(point.x, point.y, point.z));
        }
        return new PolylineData(vecPoints);
    }

    record Basis(Vector3d xAxis, Vector3d yAxis, Vector3d normal) {
    }
}
