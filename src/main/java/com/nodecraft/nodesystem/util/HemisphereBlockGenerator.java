package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.HemisphereGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

/**
 * Voxelization for hemisphere geometry (sphere ∩ half-space).
 */
public final class HemisphereBlockGenerator {

    private static final double EPS = 1.0e-9d;

    private HemisphereBlockGenerator() {
    }

    public static RegionData createBoundingRegion(HemisphereGeometryData geometry) {
        Vector3d center = geometry.getCenter();
        double radius = Math.max(1.0d, geometry.getRadius());
        BlockPos minCorner = BlockPos.ofFloored(
            center.x - radius,
            center.y - radius,
            center.z - radius
        );
        BlockPos maxCorner = BlockPos.ofFloored(
            center.x + radius,
            center.y + radius,
            center.z + radius
        );
        return new RegionData(minCorner, maxCorner);
    }

    public static void populateHemisphere(BlockPosList blocks,
                                          RegionData region,
                                          HemisphereGeometryData geometry,
                                          boolean fillSolid) {
        if (blocks == null || region == null || !region.isComplete() || geometry == null) {
            return;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return;
        }

        Vector3d center = geometry.getCenter();
        Vector3d axis = geometry.getAxis();
        double radius = Math.max(1.0d, geometry.getRadius());
        double shellThickness = 1.0d;
        double shellThreshold = Math.max(0.0d, radius - Math.max(0.0d, shellThickness));

        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    // Match SphereBlockGenerator: use block corner coordinates for inclusion tests.
                    Vector3d rel = new Vector3d(x, y, z).sub(center, new Vector3d());
                    double axial = rel.dot(axis);
                    if (axial < -EPS) {
                        continue;
                    }

                    double distance = rel.length();
                    if (distance > radius) {
                        continue;
                    }

                    if (!fillSolid) {
                        boolean nearSphere = distance >= shellThreshold;
                        boolean nearFlat = axial <= shellThickness + 0.25d;
                        if (!(nearSphere || nearFlat)) {
                            continue;
                        }
                    }

                    blocks.add(new BlockPos(x, y, z));
                }
            }
        }
    }
}
