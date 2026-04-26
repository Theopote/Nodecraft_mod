package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.OctahedronGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public final class OctahedronBlockGenerator {

    private OctahedronBlockGenerator() {
    }

    public static RegionData createBoundingRegion(OctahedronGeometryData geometry) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vector3d v : geometry.getVertices()) {
            minX = Math.min(minX, v.x);
            minY = Math.min(minY, v.y);
            minZ = Math.min(minZ, v.z);
            maxX = Math.max(maxX, v.x);
            maxY = Math.max(maxY, v.y);
            maxZ = Math.max(maxZ, v.z);
        }
        return new RegionData(
            BlockPos.ofFloored(minX - 1.0d, minY - 1.0d, minZ - 1.0d),
            BlockPos.ofFloored(maxX + 1.0d, maxY + 1.0d, maxZ + 1.0d)
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
        Matrix3d r = geometry.getOrientationMatrix();
        int sizeInt = Math.max(0, (int) Math.round(geometry.getVertexRadius()));
        int innerInt = Math.max(-1, sizeInt - 1);
        Vector3d local = new Vector3d();

        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    double px = x - center.x;
                    double py = y - center.y;
                    double pz = z - center.z;
                    local.set(px, py, pz).mulTranspose(r, local);
                    double l1 = Math.abs(local.x) + Math.abs(local.y) + Math.abs(local.z);
                    if (l1 > sizeInt + 0.5d) {
                        continue;
                    }
                    if (!fillSolid && innerInt >= 0 && l1 <= innerInt + 0.5d) {
                        continue;
                    }
                    blocks.add(new BlockPos(x, y, z));
                }
            }
        }
    }
}
