package com.nodecraft.nodesystem.util;

import com.nodecraft.nodesystem.datatypes.EllipsoidGeometryData;
import com.nodecraft.nodesystem.datatypes.RegionData;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public final class EllipsoidBlockGenerator {

    public enum VoxelMode {
        SOLID,
        SHELL
    }

    private EllipsoidBlockGenerator() {
    }

    public static RegionData createBoundingRegion(EllipsoidGeometryData geometry) {
        Vector3d center = geometry.getCenter();
        Vector3d radii = geometry.getRadii();
        Matrix3d orientation = geometry.getOrientationMatrix();

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    Vector3d corner = new Vector3d(sx * radii.x, sy * radii.y, sz * radii.z);
                    orientation.transform(corner);
                    corner.add(center);
                    minX = Math.min(minX, corner.x);
                    minY = Math.min(minY, corner.y);
                    minZ = Math.min(minZ, corner.z);
                    maxX = Math.max(maxX, corner.x);
                    maxY = Math.max(maxY, corner.y);
                    maxZ = Math.max(maxZ, corner.z);
                }
            }
        }

        BlockPos minCorner = BlockPos.ofFloored(minX, minY, minZ);
        BlockPos maxCorner = BlockPos.ofFloored(maxX - 1e-9d, maxY - 1e-9d, maxZ - 1e-9d);
        return new RegionData(minCorner, maxCorner);
    }

    public static void populateEllipsoid(BlockPosList blocks,
                                         RegionData region,
                                         EllipsoidGeometryData geometry,
                                         boolean fillSolid) {
        populateEllipsoid(blocks, region, geometry, fillSolid ? VoxelMode.SOLID : VoxelMode.SHELL, 1.0d);
    }

    public static void populateEllipsoid(BlockPosList blocks,
                                         RegionData region,
                                         EllipsoidGeometryData geometry,
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

        Vector3d center = geometry.getCenter();
        Vector3d radii = geometry.getRadii();
        Matrix3d inverseOrientation = geometry.getOrientationMatrix().transpose(new Matrix3d());
        boolean fillSolid = voxelMode == null || voxelMode == VoxelMode.SOLID;

        double innerRx = Math.max(0.0d, radii.x - Math.max(0.0d, shellThickness));
        double innerRy = Math.max(0.0d, radii.y - Math.max(0.0d, shellThickness));
        double innerRz = Math.max(0.0d, radii.z - Math.max(0.0d, shellThickness));
        boolean hasInner = innerRx > 0.0d && innerRy > 0.0d && innerRz > 0.0d;

        for (int x = minCorner.getX(); x <= maxCorner.getX(); x++) {
            for (int y = minCorner.getY(); y <= maxCorner.getY(); y++) {
                for (int z = minCorner.getZ(); z <= maxCorner.getZ(); z++) {
                    Vector3d local = new Vector3d(x + 0.5d, y + 0.5d, z + 0.5d).sub(center);
                    inverseOrientation.transform(local);
                    double outerDist = normalizedDistance(local.x, local.y, local.z, radii.x, radii.y, radii.z);
                    if (outerDist > 1.0d) {
                        continue;
                    }

                    if (!fillSolid && hasInner) {
                        double innerDist = normalizedDistance(local.x, local.y, local.z, innerRx, innerRy, innerRz);
                        if (innerDist < 1.0d) {
                            continue;
                        }
                    }

                    blocks.add(new BlockPos(x, y, z));
                }
            }
        }
    }

    private static double normalizedDistance(double dx, double dy, double dz, double rx, double ry, double rz) {
        if (rx <= 0.0d || ry <= 0.0d || rz <= 0.0d) {
            return Double.POSITIVE_INFINITY;
        }
        return (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz);
    }
}
