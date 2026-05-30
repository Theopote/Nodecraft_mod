package com.nodecraft.nodesystem.nodes.geometry.curves.util;

import com.nodecraft.nodesystem.datatypes.PolylineData;
import com.nodecraft.nodesystem.util.CurvePathSamplingUtil;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

public final class PathUtils {

    private PathUtils() {
    }

    public static @Nullable List<Vector3d> resolveVertices(@Nullable Object curveObj,
                                                           @Nullable Object polyObj,
                                                           @Nullable Object lineObj) {
        return CurvePathSamplingUtil.resolveVertices(curveObj, polyObj, lineObj);
    }

    public static boolean isClosed(List<Vector3d> verts) {
        return CurvePathSamplingUtil.isClosedPolyline(verts);
    }

    public static @Nullable double[] buildCumulative(List<Vector3d> unique, boolean closed) {
        return CurvePathSamplingUtil.buildCumulative(unique, closed);
    }

    public static Vector3d sampleAtDistance(List<Vector3d> unique,
                                            boolean closed,
                                            double[] cumulative,
                                            double targetDistance) {
        return CurvePathSamplingUtil.sampleAtDistance(unique, closed, cumulative, targetDistance);
    }

    public static List<Vec3d> toVec3dList(List<Vector3d> points, boolean closed) {
        return CurvePathSamplingUtil.toVec3dList(points, closed);
    }

    public static @Nullable PolylineData createPolylineOrNull(List<Vec3d> points) {
        return CurvePathSamplingUtil.createPolylineOrNull(points);
    }
}