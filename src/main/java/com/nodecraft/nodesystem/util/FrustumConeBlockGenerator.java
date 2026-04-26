package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.FrustumConeGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

/**
 * Voxelization for circular frustum (truncated cone) geometry.
 */
public final class FrustumConeBlockGenerator {
    private static final double EPSILON = 1.0e-9d;

    private FrustumConeBlockGenerator() {
    }

    public static RegionData createBoundingRegion(FrustumConeGeometryData geometry) {
        Vector3d base = geometry.getBaseCenter();
        Vector3d top = geometry.getTopCenter();
        double br = geometry.getBaseRadius();
        double tr = geometry.getTopRadius();

        double minX = Math.min(base.x - br, top.x - tr);
        double minY = Math.min(base.y - br, top.y - tr);
        double minZ = Math.min(base.z - br, top.z - tr);
        double maxX = Math.max(base.x + br, top.x + tr);
        double maxY = Math.max(base.y + br, top.y + tr);
        double maxZ = Math.max(base.z + br, top.z + tr);

        return new RegionData(
            BlockPos.ofFloored(minX, minY, minZ),
            BlockPos.ofFloored(maxX, maxY, maxZ)
        );
    }

    public static void populateFrustum(BlockPosList blocks,
                                       RegionData region,
                                       FrustumConeGeometryData geometry,
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
        Vector3d topCenter = geometry.getTopCenter();
        Vector3d axis = new Vector3d(topCenter).sub(baseCenter);
        double height = axis.length();
        if (height <= EPSILON) {
            return;
        }

        Vector3d axisDir = new Vector3d(axis).div(height);
        double baseRadius = geometry.getBaseRadius();
        double topRadius = geometry.getTopRadius();
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
                    double t = axialDistance / height;
                    double allowedRadius = baseRadius + (topRadius - baseRadius) * t;
                    if (radialDistance > allowedRadius + 0.5d) {
                        continue;
                    }

                    if (!fillSolid) {
                        boolean nearBase = axialDistance <= shellThickness + 0.25d;
                        boolean nearTop = axialDistance >= height - shellThickness - 0.25d;
                        boolean nearSide = radialDistance >= Math.max(0.0d, allowedRadius - shellThickness - 0.25d);
                        if (!(nearBase || nearTop || nearSide)) {
                            continue;
                        }
                    }

                    blocks.add(new BlockPos(x, y, z));
                }
            }
        }
    }
}
