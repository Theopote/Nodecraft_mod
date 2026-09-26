package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.SphereData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

public final class SphereBlockGenerator {

    public enum VoxelMode {
        SOLID,
        SHELL
    }

    private SphereBlockGenerator() {
    }

    public static RegionData createBoundingRegion(SphereData geometry) {
        Vector3d center = geometry.center();
        double radius = Math.max(0.0d, geometry.radius());
        return BlockSpace.inclusiveRegionFromClosedAabb(
            center.x - radius,
            center.y - radius,
            center.z - radius,
            center.x + radius,
            center.y + radius,
            center.z + radius
        );
    }

    public static void populateSphere(BlockPosList blocks, RegionData region, SphereData geometry, boolean fillSolid) {
        populateSphere(blocks, region, geometry, fillSolid ? VoxelMode.SOLID : VoxelMode.SHELL, 1.0d);
    }

    public static void populateSphere(BlockPosList blocks,
                                      RegionData region,
                                      SphereData geometry,
                                      VoxelMode voxelMode,
                                      double shellThickness) {
        if (region == null || !region.isComplete()) {
            return;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return;
        }

        Vector3d center = geometry.center();
        double radius = Math.max(0.0d, geometry.radius());
        double shellThreshold = Math.max(0.0d, radius - Math.max(0.0d, shellThickness));
        boolean fillSolid = voxelMode == null || voxelMode == VoxelMode.SOLID;

        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    Vector3d sample = BlockSpace.voxelSamplePoint(x, y, z);
                    double distance = center.distance(sample.x, sample.y, sample.z);
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
