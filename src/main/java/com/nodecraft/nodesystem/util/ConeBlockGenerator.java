package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.ConeGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

/**
 * Shared cone voxelization utilities.
 */
public final class ConeBlockGenerator {
    private static final double EPSILON = 1.0e-9d;

    private ConeBlockGenerator() {
    }

    public static RegionData createBoundingRegion(ConeGeometryData geometry) {
        Vector3d baseCenter = geometry.getBaseCenter();
        Vector3d apex = geometry.getApex();
        double radius = geometry.getBaseRadius();

        double minX = Math.min(baseCenter.x - radius, apex.x);
        double minY = Math.min(baseCenter.y - radius, apex.y);
        double minZ = Math.min(baseCenter.z - radius, apex.z);
        double maxX = Math.max(baseCenter.x + radius, apex.x);
        double maxY = Math.max(baseCenter.y + radius, apex.y);
        double maxZ = Math.max(baseCenter.z + radius, apex.z);

        return new RegionData(
            BlockPos.ofFloored(minX, minY, minZ),
            BlockPos.ofFloored(maxX, maxY, maxZ)
        );
    }

    public static void populateCone(BlockPosList blocks,
                                    RegionData region,
                                    ConeGeometryData geometry,
                                    boolean fillSolid) {
        if (blocks == null || region == null || !region.isComplete() || geometry == null) {
            return;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return;
        }

        Vector3d baseCenter = geometry.getBaseCenter();
        Vector3d apex = geometry.getApex();
        Vector3d axis = new Vector3d(apex).sub(baseCenter);
        double height = axis.length();
        if (height <= EPSILON) {
            return;
        }

        Vector3d axisDir = new Vector3d(axis).div(height);
        double radius = geometry.getBaseRadius();
        double shellThickness = 1.0d;

        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    Vector3d sample = new Vector3d(x + 0.5d, y + 0.5d, z + 0.5d);
                    Vector3d relative = sample.sub(baseCenter, new Vector3d());
                    double axialDistance = relative.dot(axisDir);
                    if (axialDistance < 0.0d || axialDistance > height) {
                        continue;
                    }

                    Vector3d projected = new Vector3d(axisDir).mul(axialDistance);
                    double radialDistance = relative.sub(projected, new Vector3d()).length();
                    double allowedRadius = radius * (1.0d - (axialDistance / height));
                    if (radialDistance > allowedRadius + 0.5d) {
                        continue;
                    }

                    if (!fillSolid) {
                        boolean nearSide = radialDistance >= Math.max(0.0d, allowedRadius - shellThickness - 0.25d);
                        boolean nearBase = axialDistance <= shellThickness + 0.25d;
                        boolean nearApex = axialDistance >= height - shellThickness - 0.25d;
                        if (!(nearSide || nearBase || nearApex)) {
                            continue;
                        }
                    }

                    blocks.add(new BlockPos(x, y, z));
                }
            }
        }
    }
}
