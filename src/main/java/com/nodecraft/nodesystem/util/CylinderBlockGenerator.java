package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.CylinderGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

public final class CylinderBlockGenerator {

    private CylinderBlockGenerator() {
    }

    public static RegionData createBoundingRegion(CylinderGeometryData geometry) {
        Vector3d start = geometry.getStart();
        Vector3d end = geometry.getEnd();
        double radius = Math.max(1.0d, geometry.getRadius());

        BlockPos minCorner = BlockPos.ofFloored(
            Math.min(start.x, end.x) - radius,
            Math.min(start.y, end.y) - radius,
            Math.min(start.z, end.z) - radius
        );
        BlockPos maxCorner = BlockPos.ofFloored(
            Math.max(start.x, end.x) + radius,
            Math.max(start.y, end.y) + radius,
            Math.max(start.z, end.z) + radius
        );
        return new RegionData(minCorner, maxCorner);
    }

    public static void populateCylinder(BlockPosList blocks, RegionData region, CylinderGeometryData geometry, boolean fillSolid) {
        if (region == null || !region.isComplete()) {
            return;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return;
        }

        Vector3d start = geometry.getStart();
        Vector3d end = geometry.getEnd();
        Vector3d axis = new Vector3d(end).sub(start);
        double axisLengthSquared = axis.lengthSquared();
        double radius = Math.max(1.0d, geometry.getRadius());
        double shellThreshold = Math.max(0.0d, radius - 1.0d);

        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    Vector3d point = new Vector3d(x, y, z);
                    double radialDistance = computeRadialDistance(point, start, axis, axisLengthSquared);
                    if (radialDistance > radius) {
                        continue;
                    }
                    if (!fillSolid && radialDistance < shellThreshold) {
                        continue;
                    }
                    blocks.add(new BlockPos(x, y, z));
                }
            }
        }
    }

    private static double computeRadialDistance(Vector3d point, Vector3d start, Vector3d axis, double axisLengthSquared) {
        if (axisLengthSquared < 1e-9) {
            return point.distance(start);
        }

        double t = new Vector3d(point).sub(start).dot(axis) / axisLengthSquared;
        t = Math.max(0.0d, Math.min(1.0d, t));
        Vector3d nearestPoint = new Vector3d(start).fma(t, axis);
        return point.distance(nearestPoint);
    }
}
