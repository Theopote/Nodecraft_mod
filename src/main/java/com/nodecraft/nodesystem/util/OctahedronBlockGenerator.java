package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

public final class OctahedronBlockGenerator {

    private OctahedronBlockGenerator() {
    }

    public static RegionData createBoundingRegion(OctahedronGeometryData geometry) {
        Vector3d center = geometry.getCenter();
        double radius = geometry.getVertexRadius();

        return new RegionData(
            BlockPos.ofFloored(center.x - radius, center.y - radius, center.z - radius),
            BlockPos.ofFloored(center.x + radius, center.y + radius, center.z + radius)
        );
    }

    public static void populateOctahedron(BlockPosList blocks,
                                          RegionData region,
                                          OctahedronGeometryData geometry,
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
        int size = Math.max(0, (int) Math.round(geometry.getVertexRadius()));
        int innerSize = Math.max(-1, size - 1);

        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    int dx = (int) Math.round(x - center.x);
                    int dy = (int) Math.round(y - center.y);
                    int dz = (int) Math.round(z - center.z);
                    int manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (manhattan > size) {
                        continue;
                    }
                    if (!fillSolid && manhattan <= innerSize) {
                        continue;
                    }
                    blocks.add(new BlockPos(x, y, z));
                }
            }
        }
    }
}
