package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.RegionData;
import com.nodecraft.nodesystem.datatypes.TorusGeometryData;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

/**
 * Shared helpers for turning torus definitions into block coordinates.
 */
public final class TorusBlockGenerator {

    private TorusBlockGenerator() {
    }

    public static RegionData createBoundingRegion(TorusGeometryData geometry) {
        Vector3d center = geometry.center();
        double bound = geometry.majorRadius() + geometry.minorRadius();
        BlockPos minCorner = BlockPos.ofFloored(center.x - bound, center.y - bound, center.z - bound);
        BlockPos maxCorner = BlockPos.ofFloored(center.x + bound, center.y + bound, center.z + bound);
        return new RegionData(minCorner, maxCorner);
    }

    public static void populateTorus(BlockPosList blocks, RegionData region, TorusGeometryData geometry, boolean fillSolid) {
        if (region == null || !region.isComplete()) {
            return;
        }

        BlockPos minCorner = region.getMinCorner();
        BlockPos maxCorner = region.getMaxCorner();
        if (minCorner == null || maxCorner == null) {
            return;
        }

        Vector3d axis = geometry.axis();
        Vector3d tangent = buildOrthogonalBasisVector(axis);
        Vector3d bitangent = new Vector3d(axis).cross(tangent).normalize();
        double majorRadius = geometry.majorRadius();
        double minorRadius = geometry.minorRadius();
        double minorRadiusSquared = minorRadius * minorRadius;
        Vector3d center = geometry.center();

        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    if (!containsTorus(x, y, z, center, tangent, bitangent, axis, majorRadius, minorRadiusSquared)) {
                        continue;
                    }

                    if (fillSolid || isShellBlock(x, y, z, center, tangent, bitangent, axis, majorRadius, minorRadiusSquared)) {
                        blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    public static boolean containsTorus(
        int x,
        int y,
        int z,
        Vector3d center,
        Vector3d tangent,
        Vector3d bitangent,
        Vector3d axis,
        double majorRadius,
        double minorRadiusSquared
    ) {
        Vector3d relative = new Vector3d(x + 0.5d, y + 0.5d, z + 0.5d).sub(center);
        double localX = relative.dot(tangent);
        double localY = relative.dot(bitangent);
        double localZ = relative.dot(axis);
        double radialDistance = Math.sqrt(localX * localX + localY * localY);
        double torusEquation = (radialDistance - majorRadius) * (radialDistance - majorRadius) + localZ * localZ;
        return torusEquation <= minorRadiusSquared;
    }

    public static boolean isShellBlock(
        int x,
        int y,
        int z,
        Vector3d center,
        Vector3d tangent,
        Vector3d bitangent,
        Vector3d axis,
        double majorRadius,
        double minorRadiusSquared
    ) {
        return !containsTorus(x + 1, y, z, center, tangent, bitangent, axis, majorRadius, minorRadiusSquared)
            || !containsTorus(x - 1, y, z, center, tangent, bitangent, axis, majorRadius, minorRadiusSquared)
            || !containsTorus(x, y + 1, z, center, tangent, bitangent, axis, majorRadius, minorRadiusSquared)
            || !containsTorus(x, y - 1, z, center, tangent, bitangent, axis, majorRadius, minorRadiusSquared)
            || !containsTorus(x, y, z + 1, center, tangent, bitangent, axis, majorRadius, minorRadiusSquared)
            || !containsTorus(x, y, z - 1, center, tangent, bitangent, axis, majorRadius, minorRadiusSquared);
    }

    public static Vector3d buildOrthogonalBasisVector(Vector3d axis) {
        Vector3d reference = Math.abs(axis.y) < 0.99d
            ? new Vector3d(0.0d, 1.0d, 0.0d)
            : new Vector3d(1.0d, 0.0d, 0.0d);

        Vector3d tangent = reference.cross(axis, new Vector3d());
        if (tangent.lengthSquared() < 1e-9d) {
            tangent = new Vector3d(0.0d, 0.0d, 1.0d);
        }
        return tangent.normalize();
    }
}
