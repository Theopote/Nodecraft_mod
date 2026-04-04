package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

public final class SphereBlockGenerator {

    private SphereBlockGenerator() {
    }

    public static RegionData createBoundingRegion(SphereData geometry) {
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

    public static void populateSphere(BlockPosList blocks, RegionData region, SphereData geometry, boolean fillSolid) {
        if (region == null || !region.isComplete()) {
            return;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return;
        }

        Vector3d center = geometry.getCenter();
        double radius = Math.max(1.0d, geometry.getRadius());
        double shellThreshold = Math.max(0.0d, radius - 1.0d);

        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    double distance = center.distance(x, y, z);
                    if (distance > radius) {
                        continue;
                    }
                    if (!fillSolid && distance < shellThreshold) {
                        continue;
                    }
                    blocks.add(new BlockPos(x, y, z));
                }
            }
        }
    }
}
